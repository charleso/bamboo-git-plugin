package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.author.AuthorImpl;
import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.commit.Commit;
import com.atlassian.bamboo.commit.CommitFileImpl;
import com.atlassian.bamboo.commit.CommitImpl;
import com.atlassian.bamboo.plugins.git.GitRepository.GitRepositoryAccessData;
import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.security.StringEncrypter;
import com.atlassian.bamboo.ssh.ProxyConnectionData;
import com.atlassian.bamboo.ssh.ProxyConnectionDataBuilder;
import com.atlassian.bamboo.ssh.ProxyException;
import com.atlassian.bamboo.ssh.ProxyRegistrationInfo;
import com.atlassian.bamboo.ssh.SshProxy;
import com.atlassian.bamboo.ssh.SshProxyService;
import com.atlassian.bamboo.utils.SystemProperty;
import com.atlassian.bamboo.v2.build.BuildRepositoryChanges;
import com.atlassian.bamboo.v2.build.BuildRepositoryChangesImpl;
import com.opensymphony.xwork.TextProvider;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.storage.file.RefDirectory;
import org.eclipse.jgit.transport.FetchConnection;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.TransportHttp;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Class used for issuing various git operations. We don't want to hold this logic in
 * GitRepository class.
 */
public abstract class GitOperationHelper
{
    private static final Logger log = Logger.getLogger(GitOperationHelper.class);
    // ------------------------------------------------------------------------------------------------------- Constants
    private static final int DEFAULT_TRANSFER_TIMEOUT = new SystemProperty(false, "atlassian.bamboo.git.timeout", "GIT_TIMEOUT").getValue(10 * 60);
    private static final int CHANGESET_LIMIT = new SystemProperty(false, "atlassian.bamboo.git.changeset.limit", "GIT_CHANGESET_LIMIT").getValue(100);

    private static final String[] FQREF_PREFIXES = {Constants.R_HEADS, Constants.R_REFS};
    // ------------------------------------------------------------------------------------------------- Type Properties
    private ProxyRegistrationInfo proxyRegistrationInfo;
    // ---------------------------------------------------------------------------------------------------- Dependencies
    protected final BuildLogger buildLogger;
    protected final SshProxyService sshProxyService;
    protected final TextProvider textProvider;
    // ---------------------------------------------------------------------------------------------------- Constructors

    public GitOperationHelper(final @NotNull BuildLogger buildLogger,
                              final @NotNull SshProxyService sshProxyService,
                              final @NotNull TextProvider textProvider)
    {
        this.buildLogger = buildLogger;
        this.sshProxyService = sshProxyService;
        this.textProvider = textProvider;
    }

    // ----------------------------------------------------------------------------------------------- Interface Methods

    protected abstract void doFetch(@NotNull final Transport transport,
                                    @NotNull final File sourceDirectory,
                                    @NotNull final GitRepository.GitRepositoryAccessData accessData,
                                    RefSpec refSpec,
                                    boolean useShallow) throws RepositoryException;

    protected abstract String doCheckout(@NotNull final FileRepository localRepository,
                                         @NotNull File sourceDirectory,
                                         @NotNull String targetRevision,
                                         @Nullable String previousRevision) throws RepositoryException;

    // -------------------------------------------------------------------------------------------------- Action Methods
    // -------------------------------------------------------------------------------------------------- Public Methods

    /*
     * returns revision found after checkout in sourceDirectory
     */
    @NotNull
    public String checkout(@Nullable File cacheDirectory,
                           @NotNull final File sourceDirectory,
                           @NotNull final String targetRevision,
                           @Nullable final String previousRevision) throws RepositoryException
    {
        // would be cool to store lastCheckoutedRevision in the localRepository somehow - so we don't need to specify it
        buildLogger.addBuildLogEntry(textProvider.getText("repository.git.messages.checkingOutRevision", Arrays.asList(targetRevision)));

        try
        {
            FileRepository localRepository = createLocalRepository(sourceDirectory, cacheDirectory);

            //try to clean .git/index.lock file prior to checkout, otherwise checkout would fail with Exception
            File lck = new File(localRepository.getIndexFile().getParentFile(), localRepository.getIndexFile().getName() + ".lock");
            FileUtils.deleteQuietly(lck);

            return doCheckout(localRepository, sourceDirectory, targetRevision, previousRevision);
        }
        catch (IOException e)
        {
            throw new RepositoryException(buildLogger.addErrorLogEntry(textProvider.getText("repository.git.messages.checkoutFailed", Arrays.asList(targetRevision))) + e.getMessage(), e);
        }
   }

    public void fetch(@NotNull final File sourceDirectory, @NotNull final GitRepositoryAccessData accessData, boolean useShallow) throws RepositoryException
    {
        SshProxy.getRunningInstance();

        Transport transport = null;
        FileRepository localRepository = null;
        String branchDescription = "(unresolved) " + accessData.branch;
        try
        {
            localRepository = createLocalRepository(sourceDirectory, null);

            transport = open(localRepository, accessData);
            final String resolvedBranch;
            if (StringUtils.startsWithAny(accessData.branch, FQREF_PREFIXES))
            {
                resolvedBranch = accessData.branch;
            }
            else
            {
                final FetchConnection fetchConnection = transport.openFetch();
                try
                {
                    resolvedBranch = resolveRefSpec(accessData, fetchConnection).getName();
                }
                finally
                {
                    fetchConnection.close();
                }
            }
            branchDescription = resolvedBranch;

            buildLogger.addBuildLogEntry(textProvider.getText("repository.git.messages.fetchingBranch", Arrays.asList(branchDescription, accessData.repositoryUrl))
                    + (useShallow ? " " + textProvider.getText("repository.git.messages.doingShallowFetch") : ""));
            RefSpec refSpec = new RefSpec()
                    .setForceUpdate(true)
                    .setSource(resolvedBranch)
                    .setDestination(resolvedBranch);

            doFetch(transport, sourceDirectory, accessData, refSpec, useShallow);

            if (resolvedBranch.startsWith(Constants.R_HEADS))
            {
                localRepository.updateRef(Constants.HEAD).link(resolvedBranch);
            }
        }
        catch (IOException e)
        {
            String message = textProvider.getText("repository.git.messages.fetchingFailed", Arrays.asList(accessData.repositoryUrl, branchDescription, sourceDirectory));
            throw new RepositoryException(buildLogger.addErrorLogEntry(message + " " + e.getMessage()), e);
        }
        finally
        {
            if (localRepository != null)
            {
                localRepository.close();
            }
            if (transport != null)
            {
                transport.close();
            }
        }
    }

    @Nullable
    public String getCurrentRevision(@NotNull final File sourceDirectory)
    {
        File gitDirectory = new File(sourceDirectory, Constants.DOT_GIT);
        if (!gitDirectory.exists())
        {
            return null;
        }
        FileRepository localRepository = null;
        try
        {
            localRepository = new FileRepository(new File(sourceDirectory, Constants.DOT_GIT));
            ObjectId objId = localRepository.resolve(Constants.HEAD);
            return(objId != null ? objId.getName() : null);
        }
        catch (IOException e)
        {
            log.warn(buildLogger.addBuildLogEntry(textProvider.getText("repository.git.messages.cannotDetermineRevision", Arrays.asList(sourceDirectory)) + " " + e.getMessage()), e);
            return null;
        }
        finally
        {
            if (localRepository != null)
            {
                localRepository.close();
            }
        }
    }

    @NotNull
    public String obtainLatestRevision(@NotNull final GitRepositoryAccessData repositoryData) throws RepositoryException
    {
        Transport transport = null;
        FetchConnection fetchConnection = null;
        try
        {
            transport = open(new FileRepository(""), repositoryData);
            fetchConnection = transport.openFetch();
            Ref headRef = resolveRefSpec(repositoryData, fetchConnection);
            if (headRef == null)
            {
                throw new RepositoryException(textProvider.getText("repository.git.messages.cannotDetermineHead", Arrays.asList(repositoryData.repositoryUrl, repositoryData.branch)));
            }
            else
            {
                return headRef.getObjectId().getName();
            }
        }
        catch (NotSupportedException e)
        {
            throw new RepositoryException(buildLogger.addErrorLogEntry(textProvider.getText("repository.git.messages.protocolUnsupported", Arrays.asList(repositoryData.repositoryUrl))), e);
        }
        catch (TransportException e)
        {
            throw new RepositoryException(buildLogger.addErrorLogEntry(e.getMessage()), e);
        }
        catch (IOException e)
        {
            throw new RepositoryException(buildLogger.addErrorLogEntry(textProvider.getText("repository.git.messages.failedToCreateFileRepository")), e);
        }
        finally
        {
            if (fetchConnection != null)
            {
                fetchConnection.close();
            }
            if (transport != null)
            {
                transport.close();
            }
        }
    }

    public void close()
    {
        sshProxyService.unregister(proxyRegistrationInfo);
    }

    // -------------------------------------------------------------------------------------- Basic Accessors / Mutators

    @Nullable
    protected static Ref resolveRefSpec(GitRepositoryAccessData repositoryData, FetchConnection fetchConnection)
    {
        final Collection<String> candidates;
        if (StringUtils.isBlank(repositoryData.branch))
        {
            candidates = Arrays.asList(Constants.R_HEADS + Constants.MASTER, Constants.HEAD);
        }
        else if (StringUtils.startsWithAny(repositoryData.branch, FQREF_PREFIXES))
        {
            candidates = Collections.singletonList(repositoryData.branch);
        }
        else
        {
            candidates = Arrays.asList(repositoryData.branch, Constants.R_HEADS + repositoryData.branch, Constants.R_TAGS + repositoryData.branch);
        }

        for (String candidate : candidates)
        {
            Ref headRef = fetchConnection.getRef(candidate);
            if (headRef != null)
            {
                return headRef;
            }
        }
        return null;
    }

    protected GitRepositoryAccessData proxifyAccessData(@NotNull final GitRepositoryAccessData accessData) throws RepositoryException
    {
        if (accessData.authenticationType == GitAuthenticationType.SSH_KEYPAIR)
        {
            GitRepositoryAccessData proxyAccessData = new GitRepositoryAccessData();
            proxyAccessData.repositoryUrl = accessData.repositoryUrl;
            proxyAccessData.branch = accessData.branch;
            proxyAccessData.username = accessData.username;
            proxyAccessData.password = accessData.password;
            proxyAccessData.sshKey = accessData.sshKey;
            proxyAccessData.sshPassphrase = accessData.sshPassphrase;
            proxyAccessData.authenticationType = accessData.authenticationType;
            proxyAccessData.useShallowClones = accessData.useShallowClones;

            if (!StringUtils.contains(proxyAccessData.repositoryUrl, "://"))
            {
                proxyAccessData.repositoryUrl = "ssh://" + proxyAccessData.repositoryUrl.replaceFirst(":", "/");
            }

            URI repositoryUri = URI.create(proxyAccessData.repositoryUrl);
            if ("git".equals(repositoryUri.getScheme()) || "ssh".equals(repositoryUri.getScheme()))
            {
                try
                {
                    ProxyConnectionData connectionData = new ProxyConnectionDataBuilder()
                            .withRemoteAddress(repositoryUri.getHost(), repositoryUri.getPort() == -1 ? 22 : repositoryUri.getPort())
                            .withRemoteUserName(StringUtils.defaultIfEmpty(proxyAccessData.username, repositoryUri.getUserInfo()))
                            //.withErrorReceiver(hgCommandProcessor)
                            .withKeyFromString(proxyAccessData.sshKey, proxyAccessData.sshPassphrase)
                            .build();

                    proxyRegistrationInfo = sshProxyService.register(connectionData);

                    URI cooked = new URI(repositoryUri.getScheme(),
                            proxyRegistrationInfo.getProxyUserName(),
                            proxyRegistrationInfo.getProxyHost(),
                            proxyRegistrationInfo.getProxyPort(),
                            repositoryUri.getRawPath(),
                            repositoryUri.getRawQuery(),
                            repositoryUri.getRawFragment());

                    proxyAccessData.repositoryUrl = cooked.toString();
                }
                catch (IOException e)
                {
                    if (e.getMessage().contains("exception using cipher - please check password and data."))
                    {
                        throw new RepositoryException(buildLogger.addErrorLogEntry("Encryption exception - please check ssh keyfile passphrase."), e);
                    }
                    else
                    {
                        throw new RepositoryException("Cannot decode connection params", e);
                    }
                }
                catch (ProxyException e)
                {
                    throw new RepositoryException("Cannot create SSH proxy", e);
                }
                catch (URISyntaxException e)
                {
                    throw new RepositoryException("Remote repository URL invalid", e);
                }

                return proxyAccessData;
            }
        }

        return accessData;
    }

    protected FileRepository createLocalRepository(File workingDirectory, @Nullable File cacheDirectory)
            throws IOException
    {
        File gitDirectory = new File(workingDirectory, Constants.DOT_GIT);
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        builder.setGitDir(gitDirectory);
        String headRef = null;
        File cacheGitDir = null;
        if (cacheDirectory != null && cacheDirectory.exists())
        {
            FileRepositoryBuilder cacheRepoBuilder = new FileRepositoryBuilder().setWorkTree(cacheDirectory).setup();
            cacheGitDir = cacheRepoBuilder.getGitDir();
            File objectsCache = cacheRepoBuilder.getObjectDirectory();
            if (objectsCache != null && objectsCache.exists())
            {
                builder.addAlternateObjectDirectory(objectsCache);
                headRef = FileUtils.readFileToString(new File(cacheRepoBuilder.getGitDir(), Constants.HEAD));
            }
        }
        FileRepository localRepository = builder.build();

        if (!gitDirectory.exists())
        {
            buildLogger.addBuildLogEntry(textProvider.getText("repository.git.messages.creatingGitRepository", Arrays.asList(gitDirectory)));
            localRepository.create();
        }

        // lets update alternatives here for a moment
        File[] alternateObjectDirectories = builder.getAlternateObjectDirectories();
        if (ArrayUtils.isNotEmpty(alternateObjectDirectories))
        {
            List<String> alternatePaths = new ArrayList<String>(alternateObjectDirectories.length);
            for (File alternateObjectDirectory : alternateObjectDirectories)
            {
                alternatePaths.add(alternateObjectDirectory.getAbsolutePath());
            }
            final File alternates = new File(new File(localRepository.getObjectsDirectory(), "info"), "alternates");
            FileUtils.writeLines(alternates, alternatePaths, "\n");
        }

        if (cacheGitDir != null && cacheGitDir.isDirectory())
        {
            // copy tags and branches heads from the cache repository
            FileUtils.copyDirectoryToDirectory(new File(cacheGitDir, Constants.R_TAGS), new File(localRepository.getDirectory(), Constants.R_REFS));
            FileUtils.copyDirectoryToDirectory(new File(cacheGitDir, Constants.R_HEADS), new File(localRepository.getDirectory(), Constants.R_REFS));

            File shallow = new File(cacheGitDir, "shallow");
            if (shallow.exists())
            {
                FileUtils.copyFileToDirectory(shallow, localRepository.getDirectory());
            }
        }

        if (StringUtils.startsWith(headRef, RefDirectory.SYMREF))
        {
            FileUtils.writeStringToFile(new File(localRepository.getDirectory(), Constants.HEAD), headRef);
        }

        return localRepository;
    }

    BuildRepositoryChanges extractCommits(@NotNull final File directory, @Nullable final String previousRevision, @Nullable final String targetRevision)
            throws RepositoryException
    {
        List<Commit> commits = new ArrayList<Commit>();
        int skippedCommits = 0;

        FileRepository localRepository = null;
        RevWalk revWalk = null;
        TreeWalk treeWalk = null;

        try
        {
            File gitDirectory = new File(directory, Constants.DOT_GIT);
            localRepository = new FileRepository(gitDirectory);
            revWalk = new RevWalk(localRepository);

            if (targetRevision != null)
            {
                revWalk.markStart(revWalk.parseCommit(localRepository.resolve(targetRevision)));
            }
            if (previousRevision != null)
            {
                revWalk.markUninteresting(revWalk.parseCommit(localRepository.resolve(previousRevision)));
            }

            treeWalk = new TreeWalk(localRepository);
            treeWalk.setRecursive(true);

            for (final RevCommit jgitCommit : revWalk)
            {
                if (commits.size() >= CHANGESET_LIMIT)
                {
                    skippedCommits++;
                    continue;
                }

                CommitImpl commit = new CommitImpl();
                commit.setComment(jgitCommit.getFullMessage());
                commit.setAuthor(getAuthor(jgitCommit));
                commit.setDate(jgitCommit.getAuthorIdent().getWhen());
                commit.setChangeSetId(jgitCommit.getName());
                commits.add(commit);
                if (jgitCommit.getParentCount() >= 2) //merge commit
                {
                    continue;
                }

                if (localRepository.getShallows().contains(jgitCommit.getId()))
                {
                    continue;
                }

                treeWalk.reset();
                int treePosition = jgitCommit.getParentCount() > 0 ? treeWalk.addTree(jgitCommit.getParent(0).getTree()) : treeWalk.addTree(new EmptyTreeIterator());
                treeWalk.addTree(jgitCommit.getTree());

                for (final DiffEntry entry : DiffEntry.scan(treeWalk))
                {
                    if (entry.getOldId().equals(entry.getNewId()))
                    {
                        continue;
                    }
                    commit.addFile(new CommitFileImpl(jgitCommit.getId().getName(), entry.getChangeType() == DiffEntry.ChangeType.DELETE ? entry.getOldPath() : entry.getNewPath()));
                }
            }
        }
        catch (IOException e)
        {
            String message = textProvider.getText("repository.git.messages.extractingChangesetsException", Arrays.asList(directory, previousRevision, targetRevision));
            throw new RepositoryException(buildLogger.addErrorLogEntry(message + " " + e.getMessage()), e);
        }
        finally
        {
            if (treeWalk != null)
            {
                treeWalk.release();
            }
            if (revWalk != null)
            {
                revWalk.release();
            }
            if (localRepository != null)
            {
                localRepository.close();
            }
        }
        BuildRepositoryChanges buildChanges = new BuildRepositoryChangesImpl(targetRevision, commits);
        buildChanges.setSkippedCommitsCount(skippedCommits);
        return buildChanges;
    }

    private AuthorImpl getAuthor(RevCommit commit)
    {
        PersonIdent gitPerson = commit.getAuthorIdent();
        if (gitPerson == null)
            return new AuthorImpl(AuthorImpl.UNKNOWN_AUTHOR);
        return new AuthorImpl(String.format("%s <%s>", gitPerson.getName(), gitPerson.getEmailAddress()));
    }

    //user of this method has responsibility to finally .close() returned Transport!
    Transport open(@NotNull final FileRepository localRepository, @NotNull final GitRepositoryAccessData accessData) throws RepositoryException
    {
        try
        {
            URIish uri = new URIish(accessData.repositoryUrl);
            if ("ssh".equals(uri.getScheme()) && accessData.authenticationType == GitAuthenticationType.PASSWORD
                    && StringUtils.isBlank(uri.getUser()) && StringUtils.isNotBlank(accessData.username))
            {
                uri = uri.setUser(accessData.username);
            }
            final Transport transport;
            if (TransportAllTrustingHttps.canHandle(uri))
            {
                transport = new TransportAllTrustingHttps(localRepository, uri);
            }
            else if ("http".equals(uri.getScheme()))
            {
                class TransportHttpHack extends TransportHttp {
                    TransportHttpHack(FileRepository localRepository, URIish uri) throws NotSupportedException
                    {
                        super(localRepository, uri);
                    }
                }
                transport = new TransportHttpHack(localRepository, uri);
            }
            else
            {
                transport = Transport.open(localRepository, uri);
            }
            transport.setTimeout(DEFAULT_TRANSFER_TIMEOUT);
            if (transport instanceof SshTransport)
            {
                final boolean useKey = accessData.authenticationType == GitAuthenticationType.SSH_KEYPAIR;

                final String sshKey = useKey ? accessData.sshKey : null;
                final String passphrase = useKey ? accessData.sshPassphrase : null;

                SshSessionFactory factory = new GitSshSessionFactory(sshKey, passphrase);
                ((SshTransport)transport).setSshSessionFactory(factory);
            }
            if (accessData.authenticationType == GitAuthenticationType.PASSWORD)
            {
                // username may be specified in the URL instead of in the text field, we may still need the password if it's set
                transport.setCredentialsProvider(new TweakedUsernamePasswordCredentialsProvider(accessData.username, accessData.password));
            }
            return transport;
        }
        catch (URISyntaxException e)
        {
            throw new RepositoryException(buildLogger.addErrorLogEntry(textProvider.getText("repository.git.messages.invalidURI", Arrays.asList(accessData.repositoryUrl))), e);
        }
        catch (IOException e)
        {
            throw new RepositoryException(buildLogger.addErrorLogEntry(textProvider.getText("repository.git.messages.failedToOpenTransport", Arrays.asList(accessData.repositoryUrl))), e);
        }
    }
}
