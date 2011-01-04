package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.plugins.git.GitRepository.GitRepositoryAccessData;
import com.atlassian.bamboo.author.AuthorImpl;
import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.commit.Commit;
import com.atlassian.bamboo.commit.CommitFileImpl;
import com.atlassian.bamboo.commit.CommitImpl;
import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.security.StringEncrypter;
import com.atlassian.bamboo.utils.SystemProperty;
import com.opensymphony.xwork.TextProvider;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.transport.FetchConnection;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Class used for issuing various git operations. We don't want to hold this logic in
 * GitRepository class.
 */
public class GitOperationHelper
{
    private static int DEFAULT_TRANSFER_TIMEOUT = new SystemProperty(false, "git.timeout", "GIT_TIMEOUT").getValue(10 * 60);
    private static int CHANGESET_LIMIT = new SystemProperty(false, "git.changeset.limit", "GIT_CHANGESET_LIMIT").getValue(100);

    private static final Logger log = Logger.getLogger(GitOperationHelper.class);
    private final BuildLogger buildLogger;
    private final TextProvider textProvider;

    public GitOperationHelper(final @NotNull BuildLogger buildLogger, final @NotNull TextProvider textProvider)
    {
        this.buildLogger = buildLogger;
        this.textProvider = textProvider;
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
            Ref headRef = fetchConnection.getRef(Constants.R_HEADS + (StringUtils.isNotBlank(repositoryData.branch) ? repositoryData.branch : Constants.MASTER));
            headRef = (headRef != null) ? headRef : fetchConnection.getRef(StringUtils.isNotBlank(repositoryData.branch) ? repositoryData.branch : Constants.HEAD);
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
    public String fetchAndCheckout(@NotNull final File sourceDirectory, @NotNull final GitRepositoryAccessData accessData,
            final @Nullable String targetRevision) throws RepositoryException
    {
        String previousRevision = getCurrentRevision(sourceDirectory);
        final String notNullTargetRevision = targetRevision != null ? targetRevision : obtainLatestRevision(accessData);
        fetch(sourceDirectory, accessData);
        return checkout(sourceDirectory, notNullTargetRevision, previousRevision);
    }

    void fetch(@NotNull final File sourceDirectory, @NotNull final GitRepositoryAccessData accessData) throws RepositoryException
    {
        String realBranch = StringUtils.isNotBlank(accessData.branch) ? accessData.branch : Constants.MASTER;

        Transport transport = null;
        FileRepository localRepository = null;
        try
        {
            File gitDirectory = new File(sourceDirectory, Constants.DOT_GIT);
            localRepository = new FileRepository(new File(sourceDirectory, Constants.DOT_GIT));
            if (!gitDirectory.exists())
            {
                buildLogger.addBuildLogEntry(textProvider.getText("repository.git.messages.creatingGitRepository", Arrays.asList(gitDirectory)));
                localRepository.create();
            }

            transport = open(localRepository, accessData);

            buildLogger.addBuildLogEntry(textProvider.getText("repository.git.messages.fetchingBranch", Arrays.asList(realBranch)));

            RefSpec refSpec = new RefSpec()
                    .setForceUpdate(true)
                    .setSource(Constants.R_HEADS + realBranch)
                    .setDestination(Constants.R_HEADS + realBranch);

            //todo: what if remote repository doesn't contain branches? i.e. it has only HEAD reference like the ones in resources/obtainLatestRevision/x.zip?

            transport.fetch(new BuildLoggerProgressMonitor(buildLogger), Arrays.asList(refSpec), 1);
        }
        catch (IOException e)
        {
            String message = textProvider.getText("repository.git.messages.fetchingFailed", Arrays.asList(accessData.repositoryUrl, realBranch, sourceDirectory));
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

    /*
     * returns revision found after checkout in sourceDirectory
     */
    @NotNull
    String checkout(@NotNull final File sourceDirectory, @NotNull final String targetRevision, @Nullable final String previousRevision) throws RepositoryException
    {
        buildLogger.addBuildLogEntry(textProvider.getText("repository.git.messages.checkingOutRevision", Arrays.asList(targetRevision)));
        File gitDirectory = new File(sourceDirectory, Constants.DOT_GIT);
        FileRepository localRepository = null;
        RevWalk revWalk = null;
        try
        {
            localRepository = new FileRepository(gitDirectory);
            revWalk = new RevWalk(localRepository);
            final RevCommit targetCommit = revWalk.parseCommit(localRepository.resolve(targetRevision));
            final RevCommit previousCommit = previousRevision == null ? null : revWalk.parseCommit(localRepository.resolve(previousRevision));

            //clean .git/index.lock file prior to checkout, otherwise checkout would fail with Exception
            File lck = new File(localRepository.getIndexFile().getParentFile(), localRepository.getIndexFile().getName() + ".lock");
            FileUtils.deleteQuietly(lck);

            DirCacheCheckout dirCacheCheckout = new DirCacheCheckout(localRepository,
                    previousCommit == null ? null : previousCommit.getTree(),
                    localRepository.lockDirCache(),
                    targetCommit.getTree());
            dirCacheCheckout.setFailOnConflict(true);
            dirCacheCheckout.checkout();

            final RefUpdate refUpdate = localRepository.updateRef(Constants.HEAD);
            refUpdate.setNewObjectId(targetCommit);
            refUpdate.forceUpdate();
            // if new branch -> refUpdate.link() instead of forceUpdate()

            return targetCommit.getId().getName();
        }
        catch (IOException e)
        {
            throw new RepositoryException(buildLogger.addErrorLogEntry(textProvider.getText("repository.git.messages.checkoutFailed", Arrays.asList(targetRevision))), e);
        }
        finally
        {
            if (revWalk != null)
            {
                revWalk.release();
            }
            if (localRepository != null)
            {
                localRepository.close();
            }
        }
    }

    List<Commit> extractCommits(@NotNull final File directory, @Nullable final String previousRevision, @Nullable final String targetRevision)
            throws RepositoryException
    {
        List<Commit> commits = new ArrayList<Commit>();

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

            int skippedCommits = 0; //todo: return it :P
            for (final RevCommit commit : revWalk)
            {
                if (commits.size() >= CHANGESET_LIMIT)
                {
                    skippedCommits++;
                    continue;
                }

                CommitImpl curr = new CommitImpl();
                curr.setComment(commit.getFullMessage());
                curr.setAuthor(new AuthorImpl(commit.getAuthorIdent().getName()));
                curr.setDate(commit.getAuthorIdent().getWhen());
                commits.add(curr);

                if (commit.getParentCount() >= 2) //merge commit
                {
                    continue;
                }

                treeWalk.reset();
                int treePosition = commit.getParentCount() > 0 ? treeWalk.addTree(commit.getParent(0).getTree()) : treeWalk.addTree(new EmptyTreeIterator());
                treeWalk.addTree(commit.getTree());

                for (final DiffEntry entry : DiffEntry.scan(treeWalk))
                {
                    if (entry.getOldId().equals(entry.getNewId()))
                    {
                        continue;
                    }
//                    curr.addFile(new CommitFileImpl(commit.getId().getName(), entry.getNewPath())); <-- since bamboo 3.0
                    CommitFileImpl commitFile = new CommitFileImpl(entry.getChangeType() == DiffEntry.ChangeType.DELETE ? entry.getOldPath() : entry.getNewPath());
                    commitFile.setRevision(commit.getId().getName());
                    curr.addFile(commitFile);
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
        return commits;
    }

    //user of this method has responsibility to finally .close() returned Transport!
    Transport open(@NotNull final FileRepository localRepository, @NotNull final GitRepositoryAccessData accessData) throws RepositoryException
    {
        try
        {
            StringEncrypter encrypter = new StringEncrypter();
            Transport transport = Transport.open(localRepository, new URIish(accessData.repositoryUrl));
            transport.setTimeout(DEFAULT_TRANSFER_TIMEOUT);
            if (transport instanceof SshTransport)
            {
                SshSessionFactory factory = new GitSshSessionFactory(encrypter.decrypt(accessData.sshKey), encrypter.decrypt(accessData.sshPassphrase));
                ((SshTransport)transport).setSshSessionFactory(factory);
            }
            transport.setCredentialsProvider(new UsernamePasswordCredentialsProvider(accessData.username, accessData.password != null ? encrypter.decrypt(accessData.password) : ""));
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
