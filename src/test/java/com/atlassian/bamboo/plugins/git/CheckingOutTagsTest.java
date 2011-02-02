package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.v2.build.BuildChanges;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepository;
import org.testng.Assert;
import org.testng.annotations.*;

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

        BuildChanges changes = gitRepository.collectChangesSinceLastBuild(PLAN_KEY, null);
        gitRepository.retrieveSourceCode(mockBuildContext(), changes.getVcsRevisionKey());

        File result = srcRepo.getTextFile(gitRepository.getSourceCodeDirectory(PLAN_KEY));
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

        BuildChanges changes = gitRepository.collectChangesSinceLastBuild(PLAN_KEY, null);
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

        BuildChanges changes = gitRepository.collectChangesSinceLastBuild(PLAN_KEY, null);
        gitRepository.retrieveSourceCode(mockBuildContext(), changes.getVcsRevisionKey());

        verifyCurrentBranch(localBranch, expectedContents, gitRepository);
    }

    @Test(dataProvider = "localBranchesData")
    public void testLocalBranchReflectsRemoteDirect(String ref, String localBranch, String expectedContents) throws Exception
    {
        GitRepository gitRepository = createGitRepository();
        setRepositoryProperties(gitRepository, srcDir, ref);

        String expectedRevision = srcRepo.srcRepo.getRefDatabase().getRef(ref).getObjectId().name();
        gitRepository.retrieveSourceCode(mockBuildContext(), expectedRevision);

        verifyCurrentBranch(localBranch, expectedContents, gitRepository);
    }

    @Test
    public void testSwitchingBetweenBranchesWithCache() throws Exception
    {
        GitRepository gitRepository = createGitRepository();
        setRepositoryProperties(gitRepository, srcDir, "master");

        BuildChanges changes = gitRepository.collectChangesSinceLastBuild(PLAN_KEY, null);
        gitRepository.retrieveSourceCode(mockBuildContext(), changes.getVcsRevisionKey());

        verifyCurrentBranch("refs/heads/master", "Master top", gitRepository);

        setRepositoryProperties(gitRepository, srcDir, "branch");
        BuildChanges changes2 = gitRepository.collectChangesSinceLastBuild(PLAN_KEY, changes.getVcsRevisionKey());
        gitRepository.retrieveSourceCode(mockBuildContext(), changes2.getVcsRevisionKey());

        verifyCurrentBranch("refs/heads/branch", "Branch top", gitRepository);
    }

    @Test
    public void testSwitchingBetweenBranchesDirect() throws Exception
    {
        GitRepository gitRepository = createGitRepository();
        setRepositoryProperties(gitRepository, srcDir, "master");

        final String masterHead = srcRepo.srcRepo.getRefDatabase().getRef("refs/heads/master").getObjectId().name();
        gitRepository.retrieveSourceCode(mockBuildContext(), masterHead);

        verifyCurrentBranch("refs/heads/master", "Master top", gitRepository);

        setRepositoryProperties(gitRepository, srcDir, "branch");
        final String branchHead = srcRepo.srcRepo.getRefDatabase().getRef("refs/heads/branch").getObjectId().name();
        gitRepository.retrieveSourceCode(mockBuildContext(), branchHead);

        verifyCurrentBranch("refs/heads/branch", "Branch top", gitRepository);
    }

    private void verifyCurrentBranch(String localBranch, String expectedContents, GitRepository gitRepository)
            throws RepositoryException, IOException
    {
        File targetDir = gitRepository.getSourceCodeDirectory(PLAN_KEY);
        File result = srcRepo.getTextFile(targetDir);
        String contents = FileUtils.readFileToString(result);

        Assert.assertEquals(contents, expectedContents);

        FileRepository targetRepository = new FileRepository(new File(targetDir, Constants.DOT_GIT));
        Assert.assertEquals(targetRepository.getFullBranch(), localBranch);
        targetRepository.close();
    }
}

class GitTestRepository
{
    public final FileRepository srcRepo;
    public final Git git;
    public final File textFile;

    GitTestRepository(File srcDir) throws IOException
    {
        srcRepo = new FileRepository(new File(srcDir, Constants.DOT_GIT));
        srcRepo.create();
        textFile = getTextFile(srcDir);
        git = new Git(srcRepo);
    }

    void close()
    {
        srcRepo.close();
    }

    RevCommit commitFileContents(String text) throws IOException, GitAPIException
    {
        FileUtils.writeStringToFile(textFile, text);
        git.add().addFilepattern(".").call();
        return git.commit().setMessage(text).setCommitter("testUser", "testUser@testDomain").call();
    }

    File getTextFile(File baseDir)
    {
        return new File(baseDir, "file.txt");
    }
}
