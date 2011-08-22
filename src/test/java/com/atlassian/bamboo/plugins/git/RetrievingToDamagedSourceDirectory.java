package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.build.fileserver.BuildDirectoryManager;
import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.v2.build.agent.remote.RemoteBuildDirectoryManager;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.storage.file.FileRepository;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

public class RetrievingToDamagedSourceDirectory extends GitAbstractTest
{
    @DataProvider(parallel = true)
    Object[][] variants()
    {
        return new Boolean[][] {
                {true, true, false},
                {true, false, false},
                {false, true, false},
                {false, false, false},
                {true, true, true},
                {true, false, true},
                {false, true, true},
                {false, false, true},
        };
    }

    public GitRepository createGitRepository(boolean simulateRemote) throws Exception
    {
        GitRepository gitRepository = super.createGitRepository();
        if (simulateRemote)
        {
            File workingDirectory = gitRepository.getWorkingDirectory();
            BuildDirectoryManager buildDirectoryManager = new RemoteBuildDirectoryManager();
            gitRepository.setBuildDirectoryManager(buildDirectoryManager);
            gitRepository.setWorkingDir(workingDirectory);
        }
        return gitRepository;
    }

    @Test(dataProvider = "variants")
    public void testRetrieveWithDeletedFile_shallow_cache(boolean useShallow, boolean useCache, boolean simulateRemote) throws Exception
    {
        GitRepository gitRepository = createGitRepository(simulateRemote);

        GitTestRepository gtr = prepareExistingState(gitRepository, useShallow, useCache);

        final File textFile = gtr.getTextFile(gitRepository.getSourceCodeDirectory(PLAN_KEY));
        FileUtils.forceDelete(textFile);

        verifyNextCheckout(gitRepository, gtr);
    }

    @Test(dataProvider = "variants")
    public void testRetrieveWithChangedFile_shallow_cache(boolean useShallow, boolean useCache, boolean simulateRemote) throws Exception
    {
        GitRepository gitRepository = createGitRepository(simulateRemote);

        GitTestRepository gtr = prepareExistingState(gitRepository, useShallow, useCache);

        final File textFile = gtr.getTextFile(gitRepository.getSourceCodeDirectory(PLAN_KEY));
        FileUtils.writeStringToFile(textFile, "Changed\nFile\nContents\n");

        verifyNextCheckout(gitRepository, gtr);
    }

    @Test(dataProvider = "variants")
    public void testRetrieveWithDeletedRepository_shallow_cache(boolean useShallow, boolean useCache, boolean simulateRemote) throws Exception
    {
        GitRepository gitRepository = createGitRepository(simulateRemote);

        GitTestRepository gtr = prepareExistingState(gitRepository, useShallow, useCache);

        File localGitDir = new File(gitRepository.getSourceCodeDirectory(PLAN_KEY), Constants.DOT_GIT);
        Assert.assertTrue(localGitDir.isDirectory(), "Precondition");
        FileUtils.deleteDirectory(localGitDir);
        Assert.assertFalse(localGitDir.exists(), "Precondition");

        verifyNextCheckout(gitRepository, gtr);
    }

    @Test(dataProvider = "variants")
    public void testRetrieveWithChangedRepository_shallow_cache(boolean useShallow, boolean useCache, boolean simulateRemote) throws Exception
    {
        GitRepository gitRepository = createGitRepository(simulateRemote);

        GitTestRepository gtr = prepareExistingState(gitRepository, useShallow, useCache);

        final File textFile = gtr.getTextFile(gitRepository.getSourceCodeDirectory(PLAN_KEY));
        FileUtils.writeStringToFile(textFile, "Changed\nFile\nContents\n");

        FileRepository targetRepo = new FileRepository(new File(gitRepository.getSourceCodeDirectory(PLAN_KEY), Constants.DOT_GIT));
        Git git = new Git(targetRepo);
        git.add().addFilepattern(".").call();
        git.commit().setMessage("breaking repo").setCommitter("testUser", "testUser@testDomain").call();

        targetRepo.close();

        verifyNextCheckout(gitRepository, gtr);
    }

    @Test(dataProvider = "variants")
    public void testRetrieveWithBranchedRepository_shallow_cache(boolean useShallow, boolean useCache, boolean simulateRemote) throws Exception
    {
        GitRepository gitRepository = createGitRepository(simulateRemote);

        GitTestRepository gtr = prepareExistingState(gitRepository, useShallow, useCache);

        final File textFile = gtr.getTextFile(gitRepository.getSourceCodeDirectory(PLAN_KEY));

        FileRepository targetRepo = new FileRepository(new File(gitRepository.getSourceCodeDirectory(PLAN_KEY), Constants.DOT_GIT));
        Git git = new Git(targetRepo);
        git.checkout().setCreateBranch(true).setName("branch").call();
        FileUtils.writeStringToFile(textFile, "Changed\nFile\nContents\n");
        git.add().addFilepattern(".").call();
        git.commit().setMessage("breaking repo").setCommitter("testUser", "testUser@testDomain").call();

        Assert.assertEquals(targetRepo.getBranch(), "branch", "Precondition");
        targetRepo.close();

        verifyNextCheckout(gitRepository, gtr);
        FileRepository verifyRepo = new FileRepository(new File(gitRepository.getSourceCodeDirectory(PLAN_KEY), Constants.DOT_GIT));
        Assert.assertEquals(verifyRepo.getBranch(), "master");
        verifyRepo.close();
    }

    private GitTestRepository prepareExistingState(GitRepository gitRepository, boolean useShallow, boolean useCache) throws Exception
    {
        GitTestRepository gtr = new GitTestRepository(createTempDirectory());
        gtr.commitFileContents("initial contents");
        gtr.commitFileContents("first commit");

        setRepositoryProperties(gitRepository, gtr.srcDir.getAbsolutePath(), Collections.singletonMap("repository.git.useShallowClones", (Object)useShallow));
        if (useCache)
        {
            gitRepository.collectChangesSinceLastBuild(PLAN_KEY.getKey(), null);
        }
        Assert.assertEquals(new File(gitRepository.getCacheDirectory(), Constants.DOT_GIT).isDirectory(), useCache, "Precondition");
        Assert.assertEquals(gitRepository.getCacheDirectory().isDirectory(), useCache, "Precondition");

        gitRepository.retrieveSourceCode(mockBuildContext(), null);
        return gtr;
    }

    private void verifyNextCheckout(GitRepository gitRepository, GitTestRepository gtr)
            throws RepositoryException, IOException, GitAPIException
    {
        String target = gtr.commitFileContents("next commit").name();

        if (gitRepository.getCacheDirectory().isDirectory())
        {
            // we used cache when preparing this test case
            String collectedRevision = gitRepository.collectChangesSinceLastBuild(PLAN_KEY.getKey(), null).getVcsRevisionKey();
            Assert.assertEquals(collectedRevision, target);
        }
        String actualTarget = gitRepository.retrieveSourceCode(mockBuildContext(), target);

        final File textFile = gtr.getTextFile(gitRepository.getSourceCodeDirectory(PLAN_KEY));
        Assert.assertEquals(actualTarget, target);
        Assert.assertEquals(FileUtils.readFileToString(textFile), "next commit");
    }
}
