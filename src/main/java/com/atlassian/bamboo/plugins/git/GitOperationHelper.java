package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.author.AuthorImpl;
import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.commit.Commit;
import com.atlassian.bamboo.commit.CommitFileImpl;
import com.atlassian.bamboo.commit.CommitImpl;
import com.atlassian.bamboo.repository.RepositoryException;
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
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
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
    private static final int CHANGESET_LIMIT = 100;

    private static final Logger log = Logger.getLogger(GitOperationHelper.class);
    private final BuildLogger buildLogger;

    public GitOperationHelper(BuildLogger buildLogger)
    {
        this.buildLogger = buildLogger;
    }

    @Nullable
    String obtainLatestRevision(@NotNull final String repositoryUrl, @Nullable final String branch, @Nullable final String sshKey,
            @Nullable final String sshPassphrase) throws RepositoryException
    {
        Transport transport = null;
        FetchConnection fetchConnection = null;
        try
        {
            transport = open(new FileRepository(""), repositoryUrl, sshKey, sshPassphrase);
            fetchConnection = transport.openFetch();
            Ref headRef = fetchConnection.getRef(Constants.R_HEADS + (StringUtils.isNotBlank(branch) ? branch : Constants.MASTER));
            headRef = (headRef != null) ? headRef : fetchConnection.getRef(StringUtils.isNotBlank(branch) ? branch : Constants.HEAD);
            return (headRef == null) ? null : headRef.getObjectId().getName();
        }
        catch (NotSupportedException e)
        {
            final String message = repositoryUrl + " is not supported protocol.";
            buildLogger.addErrorLogEntry(message);
            throw new RepositoryException(message, e);
        }
        catch (TransportException e)
        {
            buildLogger.addErrorLogEntry(e.getMessage());
            throw new RepositoryException(e.getMessage(), e);
        }
        catch (IOException e)
        {
            final String message = "Failed to create FileRepository";
            buildLogger.addErrorLogEntry(message);
            throw new RepositoryException(message, e);
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

    @Nullable String getCurrentRevision(@NotNull final File sourceDirectory)
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
            buildLogger.addBuildLogEntry("Cannot retrieve current repository version of source directory '" + sourceDirectory + "'");
            log.warn("IOException during retrieving current revision in source directory `" + sourceDirectory + "'. Returning null...", e);
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

    @NotNull String fetchAndCheckout(@NotNull final File sourceDirectory, @NotNull final String repositoryUrl, @Nullable final String branch,
            @Nullable final String targetRevision, @Nullable final String sshKey, @Nullable final String sshPassphrase) throws RepositoryException
    {
        String previousRevision = getCurrentRevision(sourceDirectory);
        fetch(sourceDirectory, repositoryUrl, branch, sshKey, sshPassphrase);
        return checkout(sourceDirectory, targetRevision, previousRevision);
    }

    void fetch(@NotNull final File sourceDirectory, @NotNull final String repositoryUrl, @Nullable final String branch,
            @Nullable final String sshKey, @Nullable final String sshPassphrase) throws RepositoryException
    {
        String realBranch = StringUtils.isNotBlank(branch) ? branch : Constants.MASTER;

        Transport transport = null;
        FileRepository localRepository = null;
        try
        {
            File gitDirectory = new File(sourceDirectory, Constants.DOT_GIT);
            localRepository = new FileRepository(new File(sourceDirectory, Constants.DOT_GIT));
            if (!gitDirectory.exists())
            {
                buildLogger.addBuildLogEntry("Creating local git repository in " + gitDirectory.getAbsolutePath());
                localRepository.create();
            }

            transport = open(localRepository, repositoryUrl, sshKey, sshPassphrase);

            buildLogger.addBuildLogEntry("Fetching branch " + realBranch);

            RefSpec refSpec = new RefSpec()
                    .setForceUpdate(true)
                    .setSource(Constants.R_HEADS + realBranch)
                    .setDestination(Constants.R_HEADS + realBranch);

            //todo: what if remote repository doesn't contain branches? i.e. it has only HEAD reference like the ones in resources/obtainLatestRevision/x.zip?

            transport.fetch(new BuildLoggerProgressMonitor(buildLogger), Arrays.asList(refSpec));
        }
        catch (IOException e)
        {
            String message = "Cannot fetch `" + repositoryUrl + "', branch `" + realBranch + "' to source directory `" + sourceDirectory + "'. " + e.getMessage();
            buildLogger.addErrorLogEntry(message);
            throw new RepositoryException(message, e);
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
    String checkout(@NotNull final File sourceDirectory, @Nullable final String targetRevision, @Nullable final String previousRevision) throws RepositoryException
    {
        buildLogger.addBuildLogEntry("Checking out revision " + targetRevision);

        File gitDirectory = new File(sourceDirectory, Constants.DOT_GIT);
        FileRepository localRepository = null;
        RevWalk revWalk = null;

        try
        {
            localRepository = new FileRepository(gitDirectory);

            revWalk = new RevWalk(localRepository);
            final RevCommit targetCommit = revWalk.parseCommit(localRepository.resolve(targetRevision != null ? targetRevision : Constants.HEAD));
            final RevCommit previousCommit = previousRevision == null ? null : revWalk.parseCommit(localRepository.resolve(previousRevision));

            //clean .git/index.lock file prior to checkout, otherwise checkout would fail with Exception
            File lck = new File(localRepository.getIndexFile().getParentFile(), localRepository.getIndexFile().getName() + ".lock");
            FileUtils.deleteQuietly(lck);

            DirCacheCheckout dirCacheCheckout = new DirCacheCheckout(localRepository,
                    previousCommit == null ? null : previousCommit.getTree(),
                    localRepository.lockDirCache(),
                    targetCommit.getTree());
            dirCacheCheckout.setFailOnConflict(true);
            dirCacheCheckout.checkout(); //todo: BAM-7500 size limit exceeded, idea clone has a large (700mb) pack file

            final RefUpdate refUpdate = localRepository.updateRef(Constants.HEAD);
            refUpdate.setNewObjectId(targetCommit);
            refUpdate.forceUpdate();
            // if new branch -> refUpdate.link() instead of forceUpdate()

            return targetCommit.getId().getName();
        }
        catch (IOException e)
        {
            String message = "Checkout to `" + targetRevision + "' failed.";
            buildLogger.addErrorLogEntry(message);
            throw new RepositoryException(message, e);
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
//                curr.setRevision(commit.getId().getName()); <-- since bamboo 3.0
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
            String message = "IOException during extracting changes in '" + directory + "', previousRevision is " + previousRevision
                    + " targetRevision is " + targetRevision;
            buildLogger.addErrorLogEntry(message + " " + e.getMessage());
            throw new RepositoryException(message, e);
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

    //wrapper that add ssh keyfile support
    //user of this method has responsibility to finally .close() returned Transport!
    Transport open(@NotNull final FileRepository localRepository, @NotNull final String repositoryUrl,
            @Nullable final String sshKey, @Nullable final String sshPassphrase) throws RepositoryException
    {
        Transport transport;
        try
        {
            transport = Transport.open(localRepository, new URIish(repositoryUrl));
            if (transport instanceof SshTransport)
            {
                SshSessionFactory factory = new GitSshSessionFactory(sshKey, sshPassphrase);
                ((SshTransport)transport).setSshSessionFactory(factory);
            }
            return transport;
        }
        catch (URISyntaxException e)
        {
            String message = repositoryUrl + " is not valid URI.";
            buildLogger.addErrorLogEntry(message);
            throw new RepositoryException(message, e);
        }
        catch (IOException e)
        {
            String message = "Failed to open transport for " + repositoryUrl;
            buildLogger.addErrorLogEntry(message);
            throw new RepositoryException(message, e);
        }
    }
}
