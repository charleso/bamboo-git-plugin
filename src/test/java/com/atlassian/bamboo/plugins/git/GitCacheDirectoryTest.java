package com.atlassian.bamboo.plugins.git;

import org.jetbrains.annotations.NotNull;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class GitCacheDirectoryTest extends GitAbstractTest
{
    @DataProvider
    Object[][] fieldInfluenceOnCacheLocation()
    {
        return new Object[][] {
                {"repositoryUrl", true},
                {"username", true},

                {"branch", false},
                {"password", false},
                {"sshKey", false},
                {"sshPassphrase", false},
        };
    }

    @Test(dataProvider = "fieldInfluenceOnCacheLocation")
    public void testFieldInfluenceOnCacheLocaton(String field, boolean different) throws Exception
    {
        GitRepository.GitRepositoryAccessData accessData = createAccessData(
                "someUrl",
                "branch",
                "username",
                "password",
                "sshKey",
                "sshPass"
        );
        GitRepository.GitRepositoryAccessData accessData2 = createAccessData(
                "someUrl",
                "branch",
                "username",
                "password",
                "sshKey",
                "sshPass"
        );

        Field f = GitRepository.GitRepositoryAccessData.class.getDeclaredField(field);
        String val = (String) f.get(accessData2);
        f.set(accessData2, val + "chg");

        File baseDir = createTempDirectory();
        File cache1 = GitCacheDirectory.getCacheDirectory(baseDir, accessData);
        File cache2 = GitCacheDirectory.getCacheDirectory(baseDir, accessData2);

        Assert.assertEquals(cache1.equals(cache2), !different);
    }

    @Test(timeOut = 5000)
    public void testCallOnSameDirectoryBlocks() throws Exception
    {
        verifySecondThreadBlocks("repository.url", "repository.url", true);
    }

    @Test(timeOut = 5000)
    public void testCallOnDifferentDirectoryDoesNotBlock() throws Exception
    {
        verifySecondThreadBlocks("repository.url", "different.url", false);
    }

    private void verifySecondThreadBlocks(String firstUrl, String secondUrl, boolean blockExpected) throws Exception
    {
        final GitRepository repository1 = createGitRepository();
        setRepositoryProperties(repository1, firstUrl, "");

        final GitRepository repository2 = createGitRepository();
        repository2.setWorkingDir(repository1.getWorkingDirectory());
        setRepositoryProperties(repository2, secondUrl, "");

        final ArrayBlockingQueue<Boolean> hasBlocked = new ArrayBlockingQueue<Boolean>(1);
        final CountDownLatch firstCalled = new CountDownLatch(1);
        final CountDownLatch secondCalled = new CountDownLatch(1);


        Thread firstThread = new Thread("First thread") {
            @Override
            public void run()
            {
                try
                {
                    GitCacheDirectory.callOnCacheWithLock(repository1.getCacheDirectory(), new AbstractGitCacheDirectoryOperation<Object>()
                    {
                        @Override
                        public Object call(@NotNull File cacheDirectory) throws Exception
                        {
                            firstCalled.countDown();
                            boolean await = secondCalled.await(1000, TimeUnit.MILLISECONDS);
                            hasBlocked.put(!await); // await false = timeout
                            return null;
                        }
                    });
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            }
        };

        firstThread.start();

        Assert.assertTrue(firstCalled.await(1000, TimeUnit.MILLISECONDS), "First thread should be let in promptly");
        Thread secondThread = new Thread("Second thread") {
            @Override
            public void run()
            {
                try
                {
                    GitCacheDirectory.callOnCacheWithLock(repository2.getCacheDirectory(), new AbstractGitCacheDirectoryOperation<Object>()
                    {
                        @Override
                        public Object call(@NotNull File cacheDirectory) throws Exception
                        {
                            secondCalled.countDown();
                            return null;
                        }
                    });
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            }
        };

        secondThread.start();

        Assert.assertEquals(hasBlocked.take(), Boolean.valueOf(blockExpected), "Second thread blocking");
        Assert.assertTrue(secondCalled.await(2000, TimeUnit.MILLISECONDS), "Second thread should be eventually let in");
    }
}
