package com.atlassian.bamboo.plugins.git;

import com.atlassian.util.concurrent.Function;
import com.atlassian.util.concurrent.ManagedLock;
import com.atlassian.util.concurrent.ManagedLocks;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    private static final Logger log = Logger.getLogger(GitCacheDirectory.class);

    @SuppressWarnings({"ResultOfMethodCallIgnored"})
    @Nullable
    static File getCacheDirectory(@NotNull final File workingDirectory, @NotNull final String repositoryUrl)
    {
        long hashCode = repositoryUrl.hashCode();

        File cacheDirectory = new File(workingDirectory, GIT_REPOSITORY_CACHE_DIRECTORY);
        if (!cacheDirectory.isDirectory() && !cacheDirectory.mkdirs())
        {
            log.error("Failed to mkdirs on cacheDirectory(" + cacheDirectory + ").");
            return null;
        }

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
                log.error("Failed to get git cache repository '" + cacheDescription + "'", e);
                return null;
            }
            catch (Exception e)
            {
                log.error("Exception during locking git cache repository '" + cacheDescription + "'", e);
                return null;
            }
        }
    }
}
