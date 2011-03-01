package com.atlassian.bamboo.plugins.git;

import com.atlassian.util.concurrent.Function;
import com.atlassian.util.concurrent.ManagedLock;
import com.atlassian.util.concurrent.ManagedLocks;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.CharEncoding;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

/**
 * Class used to handle git cache directory operations.
 */
public class GitCacheDirectory {

    static final String GIT_REPOSITORY_CACHE_DIRECTORY = "_git-repositories-cache";

    static final Function<File, ManagedLock> cacheLockFactory = ManagedLocks.weakManagedLockFactory();

    private static final Logger log = Logger.getLogger(GitCacheDirectory.class);

    @NotNull
    static File getCacheDirectory(@NotNull final File workingDirectory, @NotNull final GitRepository.GitRepositoryAccessData repositoryData)
    {
        String repositorySha = repositoryData.useShallowClones ?
                calculateAggregateSha(repositoryData.repositoryUrl, repositoryData.username, repositoryData.branch) :
                calculateAggregateSha(repositoryData.repositoryUrl, repositoryData.username);

        File cacheDirectory = new File(workingDirectory, GIT_REPOSITORY_CACHE_DIRECTORY);
        return new File(cacheDirectory, repositorySha);
    }
    
    static String calculateAggregateSha(String... params)
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (String param : params)
        {
            if (param != null)
            {
                try
                {
                    baos.write(param.getBytes(CharEncoding.UTF_8));
                }
                catch (IOException e)
                {
                    throw new RuntimeException("Cannot happen: Error writing string to byte array", e);
                }
            }
            baos.write(0); // separator to avoid collision when you move 1 letter from username to url
        }

        return DigestUtils.shaHex(baos.toByteArray());
    }

    public static ManagedLock getCacheLock(@NotNull File cache)
    {
        return cacheLockFactory.get(cache);
    }
}
