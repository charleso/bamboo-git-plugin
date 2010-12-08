package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.util.concurrent.Function;
import com.atlassian.util.concurrent.ManagedLock;
import com.atlassian.util.concurrent.ManagedLocks;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

/**
 * Class used to handle git cache directory operations.
 */
public class GitCacheDirectory {

    static final String GIT_REPOSITORY_CACHE_DIRECTORY = "_git-repositories-cache";
    static final String DESCRIPTION = "description";
    static final String REPOSITORY = "repository";

    static final Function<File, ManagedLock> cacheLockFactory = ManagedLocks.weakManagedLockFactory();

    @SuppressWarnings({"ResultOfMethodCallIgnored"})
    static File getCacheDirectory(@NotNull final File workingDirectory, @NotNull final String repositoryUrl) throws RepositoryException
    {
        long hashCode = repositoryUrl.hashCode();

        File cacheDirectory = new File(workingDirectory, GIT_REPOSITORY_CACHE_DIRECTORY);
        cacheDirectory.mkdirs();

        int index = 0;
        while (true)
        {
            index++;
            final File candidate = new File(cacheDirectory, "0x" + Long.toString(hashCode, 16) + "-" + Integer.toString(index));
            final File cacheDescription = new File(candidate, DESCRIPTION);
            try
            {
                if (Boolean.TRUE.equals(cacheLockFactory.get(candidate).withLock(new Callable<Boolean>()
                {
                    public Boolean call() throws Exception
                    {
                        if (candidate.exists())
                        {
                            return repositoryUrl.equals(FileUtils.readFileToString(cacheDescription));
                        }
                        else
                        {
                            candidate.mkdir();
                            FileUtils.writeStringToFile(cacheDescription, repositoryUrl);
                            return true;
                        }
                    }
                })))
                {
                    return new File(candidate, REPOSITORY);
                }
            }
            catch (IOException e)
            {
                throw new RepositoryException("Failed to get git cache repository '" + cacheDescription + "'", e);
            }
            catch (Exception e)
            {
                throw new RepositoryException("Exception during locking git cache repository '" + cacheDescription + "'", e);
            }
        }
    }
}
