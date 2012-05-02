package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.author.AuthorImpl;
import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.commit.Commit;
import com.atlassian.bamboo.commit.CommitFileImpl;
import com.atlassian.bamboo.commit.CommitImpl;
import com.atlassian.bamboo.plan.branch.VcsBranch;
import com.atlassian.bamboo.plan.branch.VcsBranchImpl;
import com.atlassian.bamboo.plugins.git.GitRepository.GitRepositoryAccessData;
import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.utils.SystemProperty;
import com.atlassian.bamboo.v2.build.BuildRepositoryChanges;
import com.atlassian.bamboo.v2.build.BuildRepositoryChangesImpl;
import com.google.common.collect.Lists;
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
import org.eclipse.jgit.transport.PushResult;
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
    protected final GitRepositoryAccessData accessData;
    // ------------------------------------------------------------------------------------------------- Type Properties
    // ---------------------------------------------------------------------------------------------------- Dependencies
    protected final BuildLogger buildLogger;
    protected final TextProvider textProvider;
    // ---------------------------------------------------------------------------------------------------- Constructors

    public GitOperationHelper(final GitRepositoryAccessData accessData, final @NotNull BuildLogger buildLogger,
                              final @NotNull TextProvider textProvider)
    {
        this.accessData = accessData;
        this.buildLogger = buildLogger;
        this.textProvider = textProvider;
    }



    // ----------------------------------------------------------------------------------------------- Interface Methods

    protected abstract void doFetch(@NotNull final Transport transport,
                                    @NotNull final File sourceDirectory,
                                    RefSpec refSpec,
                                    boolean useShallow) throws RepositoryException;

    protected abstract String doCheckout(@NotNull final FileRepository localRepository,
                                         @NotNull File sourceDirectory,
                                         @NotNull String targetRevision,
                                         @Nullable String previousRevision,
                                         final boolean useSubmodules) throws RepositoryException;

    // -------------------------------------------------------------------------------------------------- Action Methods
    // -------------------------------------------------------------------------------------------------- Public Methods

    /**
     * Pushes arbitrary revision (refspec?) back to the upstream repo.
     */
    public void pushRevision(@NotNull final File sourceDirectory, @NotNull String revision) throws RepositoryException
    {
        try
        {
            final FileRepository localRepository = createLocalRepository(sourceDirectory, null);
            try
            {
                withFetchConnection(localRepository, accessData, new WithFetchConnectionCallback<IOException, Void>()
                {                    
                    @Override
                    public Void doWithFetchConnection(@NotNull Transport transport, @NotNull FetchConnection connection) throws IOException
                    {
                        final String resolvedBranch = resolveRefSpec(accessData.branch, connection).getName();

                        RefSpec refSpec = new RefSpec()
                                .setForceUpdate(true)
                                .setSource(resolvedBranch)
                                .setDestination(resolvedBranch);

                        PushResult pushResult = transport.push(new BuildLoggerProgressMonitor(buildLogger), transport.findRemoteRefUpdatesFor(Arrays.asList(refSpec)));
                        buildLogger.addBuildLogEntry("Git: " + pushResult.getMessages());
                        
                        return null;
                    }
                });
            }
            finally
            {
                localRepository.close();
            }
        }
        catch (IOException e)
        {
            throw new RepositoryException(buildLogger.addErrorLogEntry(textProvider.getText("repository.git.messages.pushFailed", Arrays.asList(revision))) + e.getMessage(), e);
        }
    }
    
    public abstract String commit(@NotNull File sourceDirectory, @NotNull String message, @NotNull String comitterName, @NotNull String comitterEmail) throws RepositoryException;

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
            final FileRepository localRepository = createLocalRepository(sourceDirectory, cacheDirectory);
            try
            {
                //try to clean .git/index.lock file prior to checkout, otherwise checkout would fail with Exception
                File lck = new File(localRepository.getIndexFile().getParentFile(), localRepository.getIndexFile().getName() + ".lock");
                FileUtils.deleteQuietly(lck);

                return doCheckout(localRepository, sourceDirectory, targetRevision, previousRevision, accessData.useSubmodules);
            }
            finally
            {
                localRepository.close();
            }
        }
        catch (IOException e)
        {
            throw new RepositoryException(buildLogger.addErrorLogEntry(textProvider.getText("repository.git.messages.checkoutFailed", Arrays.asList(targetRevision))) + e.getMessage(), e);
        }
    }

    public void fetch(@NotNull final File sourceDirectory, boolean useShallow) throws RepositoryException
    {
        fetch(sourceDirectory, accessData.branch, useShallow);
    }

    private void fetch(@NotNull final File sourceDirectory, final String branch, final boolean useShallow) throws RepositoryException
    {
        final String[] branchDescription = {"(unresolved) " + branch};
        try
        {
            final FileRepository localRepository = createLocalRepository(sourceDirectory, null);
            try
            {
                withTransport(localRepository, accessData, new WithTransportCallback<Exception, Void>()
                {
                    @Override
                    public Void doWithTransport(@NotNull Transport transport) throws Exception
                    {
                        final String resolvedBranch;
                        if (StringUtils.startsWithAny(branch, FQREF_PREFIXES))
                        {
                            resolvedBranch = branch;
                        }
                        else
                        {
                            resolvedBranch = withFetchConnection(transport, new WithFetchConnectionCallback<Exception, String>()
                            {
                                @Override
                                public String doWithFetchConnection(@NotNull Transport transport, @NotNull FetchConnection connection) throws Exception
                                {
                                    final Ref ref = resolveRefSpec(branch, connection);
                                    return ref.getName();
                                }
                            });
                        }
                        branchDescription[0] = resolvedBranch;

                        buildLogger.addBuildLogEntry(textProvider.getText("repository.git.messages.fetchingBranch", Arrays.asList(resolvedBranch, accessData.repositoryUrl))
                                                             + (useShallow ? " " + textProvider.getText("repository.git.messages.doingShallowFetch") : ""));
                        RefSpec refSpec = new RefSpec()
                                .setForceUpdate(true)
                                .setSource(resolvedBranch)
                                .setDestination(resolvedBranch);

                        doFetch(transport, sourceDirectory, refSpec, useShallow);

                        if (resolvedBranch.startsWith(Constants.R_HEADS))
                        {
                            localRepository.updateRef(Constants.HEAD).link(resolvedBranch);
                        }

                        return null;
                    }
                });
            }
            finally
            {
                localRepository.close();
            }
        }
        catch (Exception e)
        {
            String message = textProvider.getText("repository.git.messages.fetchingFailed", Arrays.asList(accessData.repositoryUrl, branchDescription[0], sourceDirectory));
            throw new RepositoryException(buildLogger.addErrorLogEntry(message + " " + e.getMessage()), e);
        }
    }

    @NotNull
    public String getCurrentRevision(@NotNull final File sourceDirectory) throws RepositoryException
    {
        return getRevision(sourceDirectory, Constants.HEAD);
    }

    protected String getRevision(File sourceDirectory, @NotNull final String revision) throws RepositoryException {
        File gitDirectory = new File(sourceDirectory, Constants.DOT_GIT);
        if (!gitDirectory.exists())
        {
            throw new RepositoryException(sourceDirectory + " does not exist");
        }
        FileRepository localRepository = null;
        try
        {
            localRepository = new FileRepository(new File(sourceDirectory, Constants.DOT_GIT));
            ObjectId objId = localRepository.resolve(revision);
            if (objId==null)
            {
                throw new RepositoryException("Cannot resolve " + revision);
            }
            return objId.getName();
        }
        catch (IOException e)
        {
            log.warn(buildLogger.addBuildLogEntry(textProvider.getText("repository.git.messages.cannotDetermineRevision", Arrays.asList(sourceDirectory)) + " " + e.getMessage()), e);
            throw new RepositoryException("Cannot resolve HEAD revision in " + sourceDirectory, e);
        }
        finally
        {
            if (localRepository != null)
            {
                localRepository.close();
            }
        }
    }

    @Nullable
    public String getRevisionIfExists(@NotNull final File sourceDirectory, @NotNull final String revision)
    {
        try
        {
            return getRevision(sourceDirectory, revision);
        }
        catch (RepositoryException e)
        {
            return null;
        }
    }

    @NotNull
    public String obtainLatestRevision() throws RepositoryException
    {
        try
        {
            return withFetchConnection(new FileRepository(""), accessData, new WithFetchConnectionCallback<RepositoryException, String>()
            {
                @Override
                public String doWithFetchConnection(@NotNull Transport transport, @NotNull FetchConnection connection) throws RepositoryException
                {
                    Ref headRef = resolveRefSpec(accessData.branch, connection);
                    if (headRef == null)
                    {
                        throw new RepositoryException(textProvider.getText("repository.git.messages.cannotDetermineHead", Arrays.asList(accessData.repositoryUrl, accessData.branch)));
                    }
                    else
                    {
                        return headRef.getObjectId().getName();
                    }
                }
            });
        }
        catch (NotSupportedException e)
        {
            throw new RepositoryException(buildLogger.addErrorLogEntry(textProvider.getText("repository.git.messages.protocolUnsupported", Arrays.asList(accessData.repositoryUrl))), e);
        }
        catch (TransportException e)
        {
            throw new RepositoryException(buildLogger.addErrorLogEntry(e.getMessage()), e);
        }
        catch (IOException e)
        {
            throw new RepositoryException(buildLogger.addErrorLogEntry(textProvider.getText("repository.git.messages.failedToCreateFileRepository")), e);
        }
    }

    @NotNull
    public List<VcsBranch> getOpenBranches(@NotNull final GitRepositoryAccessData repositoryData) throws RepositoryException
    {
        try
        {
            return withFetchConnection(new FileRepository(""), accessData, new WithFetchConnectionCallback<RepositoryException, List<VcsBranch>>()
            {
                @Override
                public List<VcsBranch> doWithFetchConnection(@NotNull Transport transport, @NotNull FetchConnection connection) throws RepositoryException
                {
                    List<VcsBranch> openBranches = Lists.newArrayList();
                    for (Ref ref : connection.getRefs())
                    {
                        if (ref.getName().startsWith(Constants.R_HEADS))
                        {
                            openBranches.add(new VcsBranchImpl(ref.getName().substring(Constants.R_HEADS.length())));
                        }
                    }
                    return openBranches;
                }
            });
        }
        catch (NotSupportedException e)
        {
            throw new RepositoryException(textProvider.getText("repository.git.messages.protocolUnsupported", Arrays.asList(repositoryData.repositoryUrl)), e);
        }
        catch (TransportException e)
        {
            throw new RepositoryException(e.getMessage(), e);
        }
        catch (IOException e)
        {
            throw new RepositoryException(textProvider.getText("repository.git.messages.failedToCreateFileRepository"), e);
        }
    }

    /**
     *
     * @param repositoryDirectory directory where repository is fetched
     * @param targetRevision revision to find in repository
     * @return true if revision found
     * @throws IOException thrown when revision not found (MissingObjectException)
     */
    public boolean checkRevisionExistsInCacheRepository(@NotNull File repositoryDirectory, @NotNull String targetRevision) throws IOException
    {
        final FileRepository localRepository = createLocalRepository(repositoryDirectory, null);
        try
        {
            RevWalk revWalk = new RevWalk(localRepository);
            final RevCommit targetCommit = revWalk.parseCommit(localRepository.resolve(targetRevision));
            return targetCommit != null;
        }
        finally
        {
            localRepository.close();
        }
    }
    
    // -------------------------------------------------------------------------------------- Basic Accessors / Mutators

    @Nullable
    protected static Ref resolveRefSpec(String branch, FetchConnection fetchConnection)
    {
        final Collection<String> candidates;
        if (StringUtils.isBlank(branch))
        {
            candidates = Arrays.asList(Constants.R_HEADS + Constants.MASTER, Constants.HEAD);
        }
        else if (StringUtils.startsWithAny(branch, FQREF_PREFIXES))
        {
            candidates = Collections.singletonList(branch);
        }
        else
        {
            candidates = Arrays.asList(branch, Constants.R_HEADS + branch, Constants.R_TAGS + branch);
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

    /**
     * Should not be called directly but rather via {@link #withTransport(FileRepository, GitRepositoryAccessData, GitOperationHelper.WithTransportCallback)}
     *
     * @param localRepository
     * @param accessData
     * @return
     * @throws RepositoryException
     */
    @NotNull
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
            // transport should be opened using factory method at least first time to properly initialize all transports
            // for non http/https this is absolutely the same way as usual, for http/https as we use own modified transports
            // we have to close just opened transport and use the own one
            Transport transport = Transport.open(localRepository, uri);
            if (TransportAllTrustingHttps.canHandle(uri))
            {
                transport.close();
                transport = new TransportAllTrustingHttps(localRepository, uri);
            }
            else if ("http".equals(uri.getScheme()))
            {
                transport.close();
                class TransportHttpHack extends TransportHttp {
                    TransportHttpHack(FileRepository localRepository, URIish uri) throws NotSupportedException
                    {
                        super(localRepository, uri);
                    }
                }
                transport = new TransportHttpHack(localRepository, uri);
            }
            transport.setTimeout(DEFAULT_TRANSFER_TIMEOUT);
            if (transport instanceof SshTransport)
            {
                final boolean useKey = accessData.authenticationType == GitAuthenticationType.SSH_KEYPAIR;

                final String sshKey = useKey ? accessData.sshKey : null;
                final String passphrase = useKey ? accessData.sshPassphrase : null;

                SshSessionFactory factory = new GitSshSessionFactory(sshKey, passphrase);
                ((SshTransport)transport).setSshSessionFactory(factory);
                if (passphrase != null)
                {
                    transport.setCredentialsProvider(new TweakedUsernamePasswordCredentialsProvider("dummy", passphrase));
                }
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

    protected interface WithTransportCallback<E extends java.lang.Throwable, T>
    {
        T doWithTransport(@NotNull Transport transport) throws E;
    }

    protected <E extends java.lang.Throwable, T> T withTransport(@NotNull FileRepository repository, 
                                                                 @NotNull final GitRepositoryAccessData accessData, 
                                                                 @NotNull WithTransportCallback<E, T> callback) throws E, RepositoryException
    {
        final Transport transport = open(repository, accessData);
        try
        {
            return callback.doWithTransport(transport);
        }
        finally
        {
            transport.close();
        }
    }

    protected interface WithFetchConnectionCallback<E extends java.lang.Throwable, T>
    {
        T doWithFetchConnection(@NotNull Transport transport, @NotNull FetchConnection connection) throws E;
    }

    protected <E extends java.lang.Throwable, T> T withFetchConnection(@NotNull final Transport transport,
                                                                       @NotNull final WithFetchConnectionCallback<E, T> callback) throws E, NotSupportedException, TransportException
    {
        final FetchConnection connection = transport.openFetch();
        try
        {
            return callback.doWithFetchConnection(transport, connection);
        }
        finally
        {
            connection.close();
        }
    }

    protected <E extends java.lang.Throwable, T> T withFetchConnection(@NotNull final FileRepository repository,
                                                                       @NotNull final GitRepositoryAccessData accessData,
                                                                       @NotNull final WithFetchConnectionCallback<E, T> callback) throws E, RepositoryException, NotSupportedException, TransportException
    {
        final Transport transport = open(repository, accessData);
        try
        {
            final FetchConnection connection = transport.openFetch();
            try
            {
                return callback.doWithFetchConnection(transport, connection);
            }
            finally
            {
                connection.close();
            }
        }
        finally
        {
            transport.close();
        }
    }

    public abstract boolean merge(@NotNull final File workspaceDir, @NotNull final String targetRevision, @NotNull String committerName, @NotNull String committerEmail) throws RepositoryException;
}
