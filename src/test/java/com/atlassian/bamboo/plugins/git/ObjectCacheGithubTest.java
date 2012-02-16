package com.atlassian.bamboo.plugins.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepository;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

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
        GitOperationHelper helperForCache = createJGitOperationHelper(createAccessData(cacheUrl));
        helperForCache.fetch(cacheDir, shallow);

        File targetDir = createTempDirectory();

        GitOperationHelper helper = createJGitOperationHelper(createAccessData(url));
        String targetRevision = helper.obtainLatestRevision();
        helper.fetch(cacheDir, false);
        helper.checkout(cacheDir, targetDir, targetRevision, null);

        verifyContents(targetDir, "shallow-clones/5-contents.zip");

        RepositorySummary rs = new RepositorySummary(targetDir);
        Assert.assertEquals(rs.objects.isEmpty() && rs.packs.isEmpty(), expectEmpty);
    }

    @Test
    public void testCleanFetchThenUpdateSequenceWithShallow() throws Exception
    {
        File cacheDir = createTempDirectory();
        File targetDir = createTempDirectory();
        GitOperationHelper helper3 = createJGitOperationHelper(createAccessData("https://github.com/pstefaniak/3.git"));

        String targetRevision = helper3.obtainLatestRevision();
        String previousRevision = null;
        helper3.fetch(cacheDir, true);
        helper3.checkout(cacheDir, targetDir, targetRevision, previousRevision);
        verifyContents(targetDir, "shallow-clones/3-contents.zip");

        RepositorySummary rs = new RepositorySummary(targetDir);
        Assert.assertTrue(rs.objects.isEmpty() && rs.packs.isEmpty());

        GitOperationHelper helper5 = createJGitOperationHelper(createAccessData("https://github.com/pstefaniak/5.git"));
        targetRevision = helper5.obtainLatestRevision();
        previousRevision = helper5.getCurrentRevision(targetDir);

        helper5.fetch(cacheDir, true);

        helper5.checkout(cacheDir, targetDir, targetRevision, previousRevision);
        verifyContents(targetDir, "shallow-clones/5-contents.zip");

        RepositorySummary rs2 = new RepositorySummary(targetDir);
        Assert.assertTrue(rs2.objects.isEmpty() && rs2.packs.isEmpty());

        File targetDir2 = createTempDirectory();
        String targetRevision2 = helper3.obtainLatestRevision();
        helper3.fetch(targetDir2, true);
        helper3.checkout(cacheDir, targetDir2, targetRevision2, null);
        verifyContents(targetDir2, "shallow-clones/3-contents.zip");

        RepositorySummary rs3 = new RepositorySummary(targetDir);
        Assert.assertTrue(rs3.objects.isEmpty() && rs3.packs.isEmpty());

        File targetDir3 = createTempDirectory();
        String targetRevision3 = helper5.obtainLatestRevision();
        helper5.fetch(targetDir3, true);

        helper5.checkout(cacheDir, targetDir3, targetRevision3, null);
        verifyContents(targetDir3, "shallow-clones/5-contents.zip");

    }

    @Test
    public void testRepositoryCreatedFromCacheIsReadable() throws Exception
    {
        File cacheDir = createTempDirectory();
        File targetDir = createTempDirectory();

        String targetRevision = createJGitOperationHelper(createAccessData("https://github.com/pstefaniak/3.git")).obtainLatestRevision();
        createJGitOperationHelper(createAccessData("https://github.com/pstefaniak/3.git")).fetch(cacheDir, false);
        createJGitOperationHelper(createAccessData("https://github.com/pstefaniak/3.git")).checkout(cacheDir, targetDir, targetRevision, null);
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
        String targetRevision = createJGitOperationHelper(createAccessData(url)).obtainLatestRevision();
        createJGitOperationHelper(createAccessData(url)).fetch(targetDir, false);
        createJGitOperationHelper(createAccessData(url)).checkout(null, targetDir, targetRevision, null);

        verifyContents(targetDir, "shallow-clones/5-contents.zip");

        RepositorySummary rs = new RepositorySummary(targetDir);
         // no cache - something expected but we can't know whether objects or pack - depends
        Assert.assertFalse(rs.objects.isEmpty() && rs.packs.isEmpty());
    }


}
