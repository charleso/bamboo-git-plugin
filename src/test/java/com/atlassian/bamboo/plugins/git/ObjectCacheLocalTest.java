package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.repository.RepositoryException;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.NoMessageException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.storage.file.FileRepository;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;

public class ObjectCacheLocalTest extends GitAbstractTest
{
    @Test
    public void testCacheGetsReusedLocally() throws Exception
    {
        TestSetup t = new TestSetup().prepare();

        File targetDir = t.createDir("target");

        GitOperationHelper goh = createGitOperationHelper();
        goh.checkout(t.cacheDir, targetDir, t.lastRevision, null, false);

        String contents = FileUtils.readFileToString(new File(targetDir, "file.txt"));
        Assert.assertEquals(contents, t.lastContents);

        RepositorySummary rs = new RepositorySummary(targetDir);
        Assert.assertTrue(rs.objects.isEmpty());
        Assert.assertTrue(rs.packs.isEmpty());
    }

    @Test
    public void testEmptyCache() throws Exception
    {
        TestSetup t = new TestSetup().prepare();
        File targetDir = t.createDir("target");

        GitOperationHelper goh = createGitOperationHelper();
        goh.fetch(targetDir, t.accessData, false);
        goh.checkout(null, targetDir, t.lastRevision, null, false);

        String contents = FileUtils.readFileToString(new File(targetDir, "file.txt"));
        Assert.assertEquals(contents, t.lastContents);

        RepositorySummary rs = new RepositorySummary(targetDir);
         // no cache - something expected but we can't know whether objects or pack - depends
        Assert.assertFalse(rs.objects.isEmpty() && rs.packs.isEmpty());
    }

    @Test
    public void testDeleteCacheDirBeforeSourceCheckout() throws Exception
    {
        TestSetup t = new TestSetup().prepare();
        File targetDir = t.createDir("target");
        File emptyCache = new File(createTempDirectory(), "not_created");

        GitOperationHelper goh = createGitOperationHelper();
        goh.fetch(targetDir, t.accessData, false);
        goh.checkout(emptyCache, targetDir, t.lastRevision, null, false);

        String contents = FileUtils.readFileToString(new File(targetDir, "file.txt"));
        Assert.assertEquals(contents, t.lastContents);
    }


    // this test is invalid
    public void testOldCacheGetsReusedLocally() throws Exception
    {
        final String asyncContents = "Async commit contents";

        TestSetup t = new TestSetup().prepare();
        String asyncRev = t.commitTestFileContents(asyncContents, "Async commit");

        File targetDir = t.createDir("target");

        GitOperationHelper goh = createGitOperationHelper();
        goh.fetch(targetDir, t.accessData, false);
        goh.checkout(t.cacheDir, targetDir, asyncRev, null, false);

        String contents = FileUtils.readFileToString(new File(targetDir, "file.txt"));
        Assert.assertEquals(contents, asyncContents);
    }

    private class TestSetup
    {
        private File baseDir;
        private File cacheDir;
        private String lastRevision;
        private String lastContents;
        private GitRepository.GitRepositoryAccessData accessData;
        private File sourceRepositoryDir;
        private Git srcGit;
        private File srcFile;


        public TestSetup prepare()
                throws IOException, NoFilepatternException, NoHeadException, NoMessageException, ConcurrentRefUpdateException, WrongRepositoryStateException, RepositoryException
        {
            baseDir = createTempDirectory();
            sourceRepositoryDir = createDir("source");
            cacheDir = createDir("cache");

            FileRepository sourceRepository = new FileRepository(new File(sourceRepositoryDir, Constants.DOT_GIT));
            sourceRepository.create(false);
            srcGit = new Git(sourceRepository);

            srcFile = new File(sourceRepositoryDir, "file.txt");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 100; i++)
            {
                sb.append("Line ").append(i).append('\n');
                lastContents = sb.toString();
                lastRevision = commitTestFileContents(lastContents, "Commit " + i);
            }

            accessData = createAccessData(sourceRepositoryDir.getAbsolutePath());

            GitOperationHelper goh = createGitOperationHelper();
            goh.fetch(cacheDir, accessData, false);
            return this;
        }

        private String commitTestFileContents(String contents, String comment)
                throws IOException, NoFilepatternException, NoHeadException, NoMessageException, ConcurrentRefUpdateException, WrongRepositoryStateException
        {
            FileUtils.writeStringToFile(srcFile, contents);
            srcGit.add().addFilepattern(".").call();
            return srcGit.commit().setMessage(comment).setCommitter("testUser", "testUser@testDomain").call().name();
        }

        private File createDir(String dirName)
        {
            File dir = new File(baseDir, dirName);
            Assert.assertTrue(dir.mkdir(), "Creating test directory " + dirName);
            return dir;
        }
    }
}
