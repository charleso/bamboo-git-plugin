package com.atlassian.bamboo.plugins.git;

import org.testng.Assert;
import org.testng.annotations.*;

import java.io.File;

public class ObjectCacheGithubTest extends GitAbstractTest
{

    @DataProvider(parallel = true)
    Object[][] githubUrls()
    {
        return new Object[][] {
                // commented out tests don't work due to shallow behavior - not sure yet if they should work or not
//                {"https://github.com/pstefaniak/7.git", "https://github.com/pstefaniak/5.git", true, true},

                {"https://github.com/pstefaniak/5.git", "https://github.com/pstefaniak/5.git", false, true},
                {"https://github.com/pstefaniak/3.git", "https://github.com/pstefaniak/5.git", false, false},
//                {"https://github.com/pstefaniak/5.git", "https://github.com/pstefaniak/5.git", true, true},
                {"https://github.com/pstefaniak/3.git", "https://github.com/pstefaniak/5.git", true, false},
                {"git://github.com/pstefaniak/5.git", "git://github.com/pstefaniak/5.git", false, true},
                {"git://github.com/pstefaniak/3.git", "git://github.com/pstefaniak/5.git", false, false},
//                {"git://github.com/pstefaniak/5.git", "git://github.com/pstefaniak/5.git", true, true},
                {"git://github.com/pstefaniak/3.git", "git://github.com/pstefaniak/5.git", true, false},

                {"https://github.com/pstefaniak/7.git", "https://github.com/pstefaniak/5.git", false, true},
                {"git://github.com/pstefaniak/7.git", "git://github.com/pstefaniak/5.git", false, true},
        };
    }
    @Test(dataProvider = "githubUrls")
    public void testCacheGetsReusedLocally(String cacheUrl, String url, boolean shallow, boolean expectEmpty) throws Exception
    {
        File cacheDir = createTempDirectory();
        createGitOperationHelper().fetch(null, cacheDir, createAccessData(cacheUrl), shallow);

        File targetDir = createTempDirectory();

        createGitOperationHelper().fetchAndCheckout(cacheDir, targetDir, createAccessData(url), null, shallow);

        verifyContents(targetDir, "shallow-clones/5-contents.zip");

        RepositorySummary rs = new RepositorySummary(targetDir);
        Assert.assertEquals(rs.objects.isEmpty() && rs.packs.isEmpty(), expectEmpty);
    }

    @Test
    public void testEmptyCache() throws Exception
    {
        String url = "git://github.com/pstefaniak/5.git";

        File targetDir = createTempDirectory();
        createGitOperationHelper().fetchAndCheckout(null, targetDir, createAccessData(url), null, false);

        verifyContents(targetDir, "shallow-clones/5-contents.zip");

        RepositorySummary rs = new RepositorySummary(targetDir);
         // no cache - something expected but we can't know whether objects or pack - depends
        Assert.assertFalse(rs.objects.isEmpty() && rs.packs.isEmpty());
    }


}
