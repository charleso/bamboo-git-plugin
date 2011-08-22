package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.commit.Commit;
import com.atlassian.bamboo.commit.CommitFile;
import com.atlassian.bamboo.v2.build.BuildRepositoryChanges;
import com.google.common.collect.Sets;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.NoMessageException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepository;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class ReportingMergesTest extends GitAbstractTest
{
    private static final String FILE_TXT = "file.txt";
    private final ThreadLocal<Git> localGit = new ThreadLocal<Git>();
    private final ThreadLocal<File> localRepository = new ThreadLocal<File>();


    @Test
    public void testReportFilesTouchedByConflictingMerge() throws Exception
    {
        Git git = localGit.get();
        String baseRevision = commitNewFileContents(FILE_TXT, "First Line\n", "Commit 1").name();


        git.checkout().setCreateBranch(true).setName("branch").call();
        RevCommit onBranch = commitNewFileContents(FILE_TXT, "Branch Line\n", "Commit on Branch");

        git.checkout().setName("master").call();
        String secondRevision = commitNewFileContents(FILE_TXT, "Different Line\n", "Commit 2").name();

        MergeResult mergeResult = git.merge().include(onBranch).call();
        Assert.assertEquals(mergeResult.getMergeStatus(), MergeResult.MergeStatus.CONFLICTING, "Precondition");
        Assert.assertEquals(mergeResult.getConflicts().size(), 1, "Precondition");
        Assert.assertEquals(mergeResult.getConflicts().get(FILE_TXT), new int[][]{{0,0,0}}, "Precondition");

        String mergeRevision = commitNewFileContents(FILE_TXT, "Merged Line\n", "Merge Commit").name();

        verifyFilePresent(Arrays.asList(baseRevision, secondRevision), mergeRevision);
    }

    @Test
    public void testReportFilesTouchedByNonConflictingMerge() throws Exception
    {
        Git git = localGit.get();
        String baseRevision = commitNewFileContents(FILE_TXT, "First Line\n", "Commit 1").name(); // enough context for auto merge

        git.checkout().setCreateBranch(true).setName("branch").call();
        RevCommit onBranch = commitNewFileContents(FILE_TXT, "Branch Line\n", "Commit on Branch");

        git.checkout().setName("master").call();
        String secondRevision = commitNewFileContents("file2.txt", "Different File", "Commit 2").name();

        MergeResult mergeResult = git.merge().include(onBranch).call();
        Assert.assertEquals(mergeResult.getMergeStatus(), MergeResult.MergeStatus.MERGED, "Precondition");
        Assert.assertEquals(FileUtils.readFileToString(new File(localRepository.get(), FILE_TXT)), "Branch Line\n", "Precondition");

        String mergeRevision = mergeResult.getNewHead().name();

        verifyFilePresent(Arrays.asList(baseRevision, secondRevision), mergeRevision);
    }

    @Test
    public void testReportFilesTouchedOnMergedBranchOnly() throws Exception
    {
        Git git = localGit.get();
        String baseRevision = commitNewFileContents(FILE_TXT, "1\n2\n2\n3\n4\n5\n", "Commit 1").name(); // enough context for auto merge

        git.checkout().setCreateBranch(true).setName("branch").call();
        RevCommit onBranch = commitNewFileContents(FILE_TXT, "1\n2\n2\n3\n4\n5\n6\n", "Commit on Branch");

        git.checkout().setName("master").call();
        String secondRevision = commitNewFileContents(FILE_TXT, "0\n1\n2\n2\n3\n4\n5\n", "Commit 2").name();

        MergeResult mergeResult = git.merge().include(onBranch).call();
        Assert.assertEquals(mergeResult.getMergeStatus(), MergeResult.MergeStatus.MERGED, "Precondition");
        Assert.assertEquals(FileUtils.readFileToString(new File(localRepository.get(), FILE_TXT)), "0\n1\n2\n2\n3\n4\n5\n6\n", "Precondition");

        String mergeRevision = mergeResult.getNewHead().name();

        verifyFilePresent(Arrays.asList(baseRevision, secondRevision), mergeRevision);
    }

    private void verifyFilePresent(List<String> prevRevisions, String mergeRevision)
            throws Exception
    {
        File localRepository = this.localRepository.get();
        // assert that file.txt is present in latest commit no matter hat the previous commit was
        GitRepository gitRepository = createGitRepository();
        setRepositoryProperties(gitRepository, localRepository);
        for (String prevRevision : prevRevisions)
        {
            BuildRepositoryChanges buildChanges = gitRepository.collectChangesSinceLastBuild(PLAN_KEY.getKey(), prevRevision);
            Assert.assertEquals(buildChanges.getVcsRevisionKey(), mergeRevision);
            Set<String> filesAggregate = Sets.newHashSet();
            for (Commit commit : buildChanges.getChanges())
            {
                for (CommitFile file : commit.getFiles())
                {
                    filesAggregate.add(file.getName());
                }
            }
            Assert.assertTrue(filesAggregate.contains(FILE_TXT), FILE_TXT + " expected in the collected changes since " + prevRevision + "; collected : " + filesAggregate);
        }
    }

    @BeforeMethod
    public void setUp() throws Exception
    {
        localRepository.set(createTempDirectory());
        FileRepository repository = new FileRepository(new File(localRepository.get(), Constants.DOT_GIT));
        repository.create(false);
        repository.close();
        localGit.set(new Git(repository));
    }

    @AfterMethod
    public void tearDown() throws Exception
    {
        localGit.get().getRepository().close();
        localGit.remove();
        localRepository.remove();
    }

    private RevCommit commitNewFileContents(String file, String text, String message)
            throws IOException, NoFilepatternException, NoHeadException, NoMessageException, ConcurrentRefUpdateException, WrongRepositoryStateException
    {
        FileUtils.writeStringToFile(new File(localRepository.get(), file), text);
        localGit.get().add().addFilepattern(".").call();
        return localGit.get().commit().setMessage(message).setCommitter("testUser", "testUser@testDomain").call();
    }
}
