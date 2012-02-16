package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.v2.build.BuildRepositoryChanges;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.storage.file.FileRepository;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;

public class CheckingOutTagsTest extends GitAbstractTest
{
    private GitTestRepository srcRepo;
    private File srcDir;

    @BeforeClass
    void setUpTest() throws IOException, GitAPIException
    {
        srcDir = createTempDirectory();
        srcRepo = new GitTestRepository(srcDir);
        String initial = srcRepo.commitFileContents("Initial contents").name();

        srcRepo.commitFileContents("master1");
        srcRepo.git.tag().setName("master1").call();

        srcRepo.commitFileContents("master2");
        srcRepo.git.tag().setName("master2").call();

        srcRepo.commitFileContents("master/3");
        srcRepo.git.tag().setName("master/3").call();

        srcRepo.commitFileContents("Master top");

        srcRepo.git.checkout().setCreateBranch(true).setName("branch").setStartPoint(initial).call();

        srcRepo.commitFileContents("branch1");
        srcRepo.git.tag().setName("branch1").call();

        srcRepo.commitFileContents("branch2");
        srcRepo.git.tag().setName("branch2").call();

        srcRepo.commitFileContents("Branch top");

        srcRepo.git.checkout().setCreateBranch(true).setName("last").setStartPoint(initial).call();
        srcRepo.commitFileContents("Last commit");


    }

    @AfterClass
    public void tearDown() throws Exception
    {
        srcRepo.close();
    }


    @DataProvider(parallel = true)
    Object[][] refsData()
    {
        return new String[][] {
                {"master", "Master top"},
                {"branch", "Branch top"},
                {"master1", "master1"},
                {"master2", "master2"},
                {"master/3", "master/3"},
                {"branch1", "branch1"},
                {"branch2", "branch2"},

                {"refs/heads/master", "Master top"},
                {"refs/heads/branch", "Branch top"},
                {"refs/tags/master1", "master1"},
                {"refs/tags/master2", "master2"},
                {"refs/tags/master/3", "master/3"},
                {"refs/tags/branch1", "branch1"},
                {"refs/tags/branch2", "branch2"},

                {"HEAD", "Last commit"},
        };
    }

    @Test(dataProvider = "refsData")
    public void testCheckoutBranchOrTag(String ref, String expectedContents) throws Exception
    {
        GitRepository gitRepository = createGitRepository();
        setRepositoryProperties(gitRepository, srcDir, ref);

        BuildRepositoryChanges changes = gitRepository.collectChangesSinceLastBuild(PLAN_KEY.getKey(), null);
        File checkoutDir = new File(gitRepository.getWorkingDirectory(), "checkout");
        gitRepository.retrieveSourceCode(mockBuildContext(), changes.getVcsRevisionKey(), checkoutDir);

        File result = srcRepo.getTextFile(checkoutDir);
        String contents = FileUtils.readFileToString(result);

        Assert.assertEquals(contents, expectedContents);
    }


    @DataProvider(parallel = true)
    Object[][] invalidRefsData()
    {
        return new String[][] {
                {"refs/tags/master"},
                {"refs/heads/master1"},
                {"unknown_branch"},
        };
    }

    @Test(dataProvider = "invalidRefsData", expectedExceptions = RepositoryException.class, expectedExceptionsMessageRegExp = "Cannot determine head revision of .*")
    public void testCheckoutNonExistingBranchOrTag(String ref) throws Exception
    {
        GitRepository gitRepository = createGitRepository();
        setRepositoryProperties(gitRepository, srcDir, ref);

        gitRepository.collectChangesSinceLastBuild(PLAN_KEY.getKey(), null);
    }

    @DataProvider(parallel = true)
    Object[][] localBranchesData()
    {
        return new String[][] {
                {"master", "refs/heads/master", "Master top"},
                {"branch", "refs/heads/branch", "Branch top"},

                {"refs/heads/master", "refs/heads/master", "Master top"},
                {"refs/heads/branch", "refs/heads/branch", "Branch top"},
                {"refs/tags/master1", "refs/heads/master", "master1"},
                {"refs/tags/branch1", "refs/heads/master", "branch1"},      // tags don't know about branches

                {"HEAD", "refs/heads/master", "Last commit"},
        };
    }

    @Test(dataProvider = "localBranchesData")
    public void testLocalBranchReflectsRemoteCached(String ref, String localBranch, String expectedContents) throws Exception
    {
        GitRepository gitRepository = createGitRepository();
        setRepositoryProperties(gitRepository, srcDir, ref);

        BuildRepositoryChanges changes = gitRepository.collectChangesSinceLastBuild(PLAN_KEY.getKey(), null);
        gitRepository.retrieveSourceCode(mockBuildContext(), changes.getVcsRevisionKey(), getCheckoutDir(gitRepository));

        verifyCurrentBranch(localBranch, expectedContents, gitRepository);
    }

    @Test(dataProvider = "localBranchesData")
    public void testLocalBranchReflectsRemoteDirect(String ref, String localBranch, String expectedContents) throws Exception
    {
        GitRepository gitRepository = createGitRepository();
        setRepositoryProperties(gitRepository, srcDir, ref);

        String expectedRevision = srcRepo.srcRepo.getRefDatabase().getRef(ref).getObjectId().name();
        gitRepository.retrieveSourceCode(mockBuildContext(), expectedRevision, getCheckoutDir(gitRepository));

        verifyCurrentBranch(localBranch, expectedContents, gitRepository);
    }

    @Test
    public void testSwitchingBetweenBranchesWithCache() throws Exception
    {
        GitRepository gitRepository = createGitRepository();
        setRepositoryProperties(gitRepository, srcDir, "master");

        BuildRepositoryChanges changes = gitRepository.collectChangesSinceLastBuild(PLAN_KEY.getKey(), null);
        gitRepository.retrieveSourceCode(mockBuildContext(), changes.getVcsRevisionKey(), getCheckoutDir(gitRepository));

        verifyCurrentBranch("refs/heads/master", "Master top", gitRepository);

        setRepositoryProperties(gitRepository, srcDir, "branch");
        BuildRepositoryChanges changes2 = gitRepository.collectChangesSinceLastBuild(PLAN_KEY.getKey(), changes.getVcsRevisionKey());
        gitRepository.retrieveSourceCode(mockBuildContext(), changes2.getVcsRevisionKey(), getCheckoutDir(gitRepository));

        verifyCurrentBranch("refs/heads/branch", "Branch top", gitRepository);
    }

    @Test
    public void testSwitchingBetweenBranchesDirect() throws Exception
    {
        GitRepository gitRepository = createGitRepository();
        setRepositoryProperties(gitRepository, srcDir, "master");

        final String masterHead = srcRepo.srcRepo.getRefDatabase().getRef("refs/heads/master").getObjectId().name();
        gitRepository.retrieveSourceCode(mockBuildContext(), masterHead, getCheckoutDir(gitRepository));

        verifyCurrentBranch("refs/heads/master", "Master top", gitRepository);

        setRepositoryProperties(gitRepository, srcDir, "branch");
        final String branchHead = srcRepo.srcRepo.getRefDatabase().getRef("refs/heads/branch").getObjectId().name();
        gitRepository.retrieveSourceCode(mockBuildContext(), branchHead, getCheckoutDir(gitRepository));

        verifyCurrentBranch("refs/heads/branch", "Branch top", gitRepository);
    }

    private void verifyCurrentBranch(String localBranch, String expectedContents, GitRepository gitRepository)
            throws RepositoryException, IOException
    {
        File targetDir = getCheckoutDir(gitRepository);
        File result = srcRepo.getTextFile(targetDir);
        String contents = FileUtils.readFileToString(result);

        Assert.assertEquals(contents, expectedContents);

        FileRepository targetRepository = new FileRepository(new File(targetDir, Constants.DOT_GIT));
        Assert.assertEquals(targetRepository.getFullBranch(), localBranch);
        targetRepository.close();
    }

    @DataProvider(parallel = true)
    Object[][] tagsData()
    {
        return new Object[][] {
                {false, "master", "master1",  "master1"},
                {false, "master", "master2",  "master2"},
                {false, "master", "master/3", "master/3"},
                {false, "branch", "branch1",  "branch1"},
                {false, "branch", "branch2",  "branch2"},
                {true,  "master", "master1",  "master1"},
                {true,  "master", "master2",  "master2"},
                {true,  "master", "master/3", "master/3"},
                {true,  "branch", "branch1",  "branch1"},
                {true,  "branch", "branch2",  "branch2"},
        };
    }

    @Test(dataProvider = "tagsData")
    public void testTagsExistsAfterFetch(boolean useCache, String branch, String tag, String expectedContents) throws Exception
    {
        boolean useShallow = false; // this doesn't matter until jgit has server support for shallow clones over local repositories
        File src = createTempDirectory();
        GitOperationHelper helper = createJGitOperationHelper(createAccessData(srcDir, branch));

        File cache = null;

        if (useCache)
        {
            cache = createTempDirectory();
            helper.fetch(cache, useShallow);
        }
        else
        {
            helper.fetch(src, useShallow);
        }
        helper.checkout(cache, src, tag, null);

        String contents = FileUtils.readFileToString(srcRepo.getTextFile(src));
        Assert.assertEquals(contents, expectedContents);
    }

    @Test
    public void testShallowTagIsTheSecondCommit() throws Exception
    {
        //see BAM-8240
        //this test is to check shallow fetching (with auto_follow tags) following structure:
        // {commit A} (HEAD of branch)
        // {commit B} (tag x.y.z)
        // {rest of commits}

        File src = createTempDirectory();
        GitOperationHelper helper = createJGitOperationHelper(createAccessData("https://github.com/github/git.git", "dup-post-receive-refs-patch"));
        helper.fetch(src, true);
        helper.checkout(null, src, "v1.7.0.2", null);
        helper.checkout(null, src, "5565f47c", "v1.7.0.2");
        helper.checkout(null, src, "8ed5bd96", "5565f47c");
    }
}

