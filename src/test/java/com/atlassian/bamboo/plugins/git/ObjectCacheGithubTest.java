package com.atlassian.bamboo.plugins.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepository;
import org.testng.Assert;
import org.testng.annotations.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ObjectCacheGithubTest extends GitAbstractTest
{

    @DataProvider(parallel = true)
    Object[][] githubUrls()
    {
        return new Object[][] {
                // commented out tests don't work due to shallow behavior - not sure yet if they should work or not
//                {"https://github.com/pstefaniak/7.git", "https://github.com/pstefaniak/5.git", true, true},

                // following commented out tests are intended exceptions so far.
                {"https://github.com/pstefaniak/5.git", "https://github.com/pstefaniak/5.git", false, true},
//                {"https://github.com/pstefaniak/3.git", "https://github.com/pstefaniak/5.git", false, false},
                {"https://github.com/pstefaniak/5.git", "https://github.com/pstefaniak/5.git", true, true},
//                {"https://github.com/pstefaniak/3.git", "https://github.com/pstefaniak/5.git", true, false},
                {"git://github.com/pstefaniak/5.git", "git://github.com/pstefaniak/5.git", false, true},
//                {"git://github.com/pstefaniak/3.git", "git://github.com/pstefaniak/5.git", false, false},
                {"git://github.com/pstefaniak/5.git", "git://github.com/pstefaniak/5.git", true, true},
//                {"git://github.com/pstefaniak/3.git", "git://github.com/pstefaniak/5.git", true, false},

                {"https://github.com/pstefaniak/7.git", "https://github.com/pstefaniak/5.git", false, true},
                {"git://github.com/pstefaniak/7.git", "git://github.com/pstefaniak/5.git", false, true},
        };
    }
    @Test(dataProvider = "githubUrls")
    public void testCacheGetsReusedLocally(String cacheUrl, String url, boolean shallow, boolean expectEmpty) throws Exception
    {
        File cacheDir = createTempDirectory();
        createGitOperationHelper().fetch(cacheDir, createAccessData(cacheUrl), shallow);

        File targetDir = createTempDirectory();

        createGitOperationHelper().fetchAndCheckout(cacheDir, targetDir, createAccessData(url), null, false);

        verifyContents(targetDir, "shallow-clones/5-contents.zip");

        RepositorySummary rs = new RepositorySummary(targetDir);
        Assert.assertEquals(rs.objects.isEmpty() && rs.packs.isEmpty(), expectEmpty);
    }

    @Test
    public void testCleanFetchThenUpdateSequenceWithShallow() throws Exception
    {
        File cacheDir = createTempDirectory();
        File targetDir = createTempDirectory();

        createGitOperationHelper().fetch(cacheDir, createAccessData("https://github.com/pstefaniak/3.git"), true);

        createGitOperationHelper().fetchAndCheckout(cacheDir, targetDir, createAccessData("https://github.com/pstefaniak/3.git"), null, true);
        verifyContents(targetDir, "shallow-clones/3-contents.zip");

        RepositorySummary rs = new RepositorySummary(targetDir);
        Assert.assertTrue(rs.objects.isEmpty() && rs.packs.isEmpty());

        createGitOperationHelper().fetch(cacheDir, createAccessData("https://github.com/pstefaniak/5.git"), true);
        createGitOperationHelper().fetchAndCheckout(cacheDir, targetDir, createAccessData("https://github.com/pstefaniak/5.git"), null, false);
        verifyContents(targetDir, "shallow-clones/5-contents.zip");

        RepositorySummary rs2 = new RepositorySummary(targetDir);
        Assert.assertTrue(rs2.objects.isEmpty() && rs2.packs.isEmpty());

        File targetDir2 = createTempDirectory();
        createGitOperationHelper().fetchAndCheckout(cacheDir, targetDir2, createAccessData("https://github.com/pstefaniak/3.git"), null, true);
        verifyContents(targetDir2, "shallow-clones/3-contents.zip");

        RepositorySummary rs3 = new RepositorySummary(targetDir);
        Assert.assertTrue(rs3.objects.isEmpty() && rs3.packs.isEmpty());

        File targetDir3 = createTempDirectory();
        createGitOperationHelper().fetchAndCheckout(cacheDir, targetDir3, createAccessData("https://github.com/pstefaniak/5.git"), null, true);
        verifyContents(targetDir3, "shallow-clones/5-contents.zip");

    }

    @Test
    public void testRepositoryCreatedFromCacheIsReadable() throws Exception
    {
        File cacheDir = createTempDirectory();
        File targetDir = createTempDirectory();

        createGitOperationHelper().fetch(cacheDir, createAccessData("https://github.com/pstefaniak/3.git"), false);
        createGitOperationHelper().fetchAndCheckout(cacheDir, targetDir, createAccessData("https://github.com/pstefaniak/3.git"), null, false);
        verifyContents(targetDir, "shallow-clones/3-contents.zip");

        FileRepository repository = new FileRepository(new File(targetDir, Constants.DOT_GIT));
        Git git = new Git(repository);
        Iterable<RevCommit> log = git.log().call();
        List<String> commits = new ArrayList<String>();
        for (RevCommit revCommit : log)
        {
            commits.add(revCommit.name());
        }

        Assert.assertEquals(commits.get(0), "0a77ee667ee310b86022f0173b59174375ed4b5d");
        Assert.assertEquals(commits.size(), 3);

        repository.close();
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
