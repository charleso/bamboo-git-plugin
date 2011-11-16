package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.repository.MavenPomAccessor;
import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.ssh.SshProxyService;
import com.atlassian.testtools.TempDirectory;
import com.opensymphony.xwork.TextProvider;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.storage.file.FileRepository;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.mock;

public class GitMavenPomAccessorTest extends GitAbstractTest
{
    private static File repoWithPoms;

    @BeforeClass
    void setUpRepoWithPoms() throws IOException, GitAPIException
    {
        repoWithPoms = createTempDirectory();
        createGitRepoWithFiles(repoWithPoms, Arrays.asList("pom.xml", "pom-other.xml", "relative/path/pom.xml", "relative/path/pom-other.xml"));
    }

    private static void createGitRepoWithFiles(File localRepository, List<String> files)
            throws IOException, GitAPIException
    {

        for (String relative : files)
        {
            File file = new File(localRepository, relative);
            FileUtils.forceMkdir(file.getParentFile());
            Assert.assertTrue(file.createNewFile(), "Preparing repo for tests");
        }

        FileRepository repository = new FileRepository(new File(localRepository, Constants.DOT_GIT));
        repository.create(false);

        Git git = new Git(repository);

        git.add().addFilepattern(".").call();
        git.commit().setMessage("testPoms").setCommitter("testUser", "testUser@testDomain").call();
    }

    @DataProvider(parallel = false)
    Object[][] pomPaths()
    {
        return new String[][]{
                {null, "pom.xml"},
                {"", "pom.xml"},
                {"pom-other.xml", "pom-other.xml"},
                {"relative/path/pom.xml", "relative/path/pom.xml"},
                {"relative/path", "relative/path/pom.xml"},
                {"/relative/path", "relative/path/pom.xml"},
                {"relative/path/", "relative/path/pom.xml"},
                {"relative/path/pom-other.xml", "relative/path/pom-other.xml"},
                {"/relative/path/pom-other.xml", "relative/path/pom-other.xml"},
        };
    }

    @Test(dataProvider = "pomPaths")
    public void testCheckoutMavenPom(Object pathToPom, final String expectedPom) throws Exception
    {
        GitRepository repository = createGitRepository();

        GitRepositoryTest.setRepositoryProperties(repository, repoWithPoms.getAbsolutePath(), Collections.singletonMap("repository.git.maven.path", pathToPom));
        MavenPomAccessor pomAccessor = repository.getMavenPomAccessor();

        final File destDir = new TempDirectory(this);
        try
        {
            File pom = pomAccessor.checkoutMavenPom(destDir);
            Assert.assertTrue(pom.exists());
            Assert.assertEquals(pom, new File(destDir, expectedPom));
        }
        finally
        {
            FileUtils.deleteQuietly(destDir);
        }
    }

    @DataProvider(parallel = true)
    Object[][] pomWrongPaths()
    {
        return new String[][]{
                {null, "pom1.xml"},
                {"pom-other.xml", "pom.xml"},
                {"relative/path/pom.xml", "pom.xml"},
                {"relative/path/pom-other.xml", "relative/path/pom.xml"},
        };
    }

    @Test(dataProvider = "pomWrongPaths", expectedExceptions = RepositoryException.class)
    public void testNotCheckingOutTheWrongPom(Object pathToPom, final String mockedPath) throws Exception
    {
        final File destDir = createTempDirectory();
        final File perTestRepo = createTempDirectory();

        try
        {
            createGitRepoWithFiles(perTestRepo, Collections.singletonList(mockedPath));

            GitRepository repository = createGitRepository();

            GitRepositoryTest.setRepositoryProperties(repository, perTestRepo.getAbsolutePath(), Collections.singletonMap("repository.git.maven.path", pathToPom));
            MavenPomAccessor pomAccessor = repository.getMavenPomAccessor();
            pomAccessor.checkoutMavenPom(destDir);
        }
        finally
        {
            FileUtils.deleteQuietly(destDir);
            FileUtils.deleteQuietly(perTestRepo);
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectPathWithDots() throws Exception
    {
        GitRepository repository = mock(GitRepository.class);
        TextProvider textProvider = mock(TextProvider.class);
        SshProxyService sshProxyService = null; //new SshProxyServiceImpl(textProvider);
        new GitMavenPomAccessor(repository, sshProxyService, textProvider, null).withPath("..");
    }

}
