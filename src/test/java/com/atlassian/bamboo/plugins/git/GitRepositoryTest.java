package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.plan.Plan;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.v2.build.BuildChanges;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.BuildContextImpl;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;
import com.atlassian.testtools.ZipResourceDirectory;
import com.opensymphony.xwork.TextProvider;
import org.apache.commons.io.FileUtils;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.defaultanswers.ReturnsMocks;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;

import static org.testng.Assert.assertTrue;

public class GitRepositoryTest extends GitAbstractTest
{
    static final String PLAN_KEY = "PLAN-KEY";

    @Test
    public void testBasicFunctionality() throws Exception
    {
        GitRepository gitRepository = createGitRepository();
        setRepositoryProperties(gitRepository, "git://github.com/cixot/test.git", "master");

        BuildChanges changes = gitRepository.collectChangesSinceLastBuild(PLAN_KEY, null);

        gitRepository.retrieveSourceCode(mockBuildContext(), changes.getVcsRevisionKey());
    }

    @DataProvider(parallel = false)
    Object[][] testSourceCodeRetrievalData()
    {
        return new Object[][]{
                {"a26ff19c3c63e19d6a57a396c764b140f48c530a", "basic-repo-contents-a26ff19c3c63e19d6a57a396c764b140f48c530a.zip"},
                {"2e20b0733759facbeb0dec6ee345d762dbc8eed8", "basic-repo-contents-2e20b0733759facbeb0dec6ee345d762dbc8eed8.zip"},
                {"55676cfa3db13bcf659b2a35e5d61eba478ed54d", "basic-repo-contents-55676cfa3db13bcf659b2a35e5d61eba478ed54d.zip"},
        };
    }

    @Test(dataProvider = "testSourceCodeRetrievalData")
    public void testSourceCodeRetrieval(String targetRevision, String expectedContentsInZip) throws Exception
    {
        File testRepository = createTempDirectory();
        ZipResourceDirectory.copyZipResourceToDirectory("basic-repository.zip", testRepository);

        GitRepository gitRepository = createGitRepository();
        setRepositoryProperties(gitRepository, testRepository, "master");

        gitRepository.retrieveSourceCode(mockBuildContext(), targetRevision);
        verifyContents(gitRepository.getSourceCodeDirectory(PLAN_KEY), expectedContentsInZip);
    }

    BuildContext mockBuildContext()
    {
        Plan plan = Mockito.mock(Plan.class);
        Mockito.when(plan.getKey()).thenReturn(PLAN_KEY);
        return new BuildContextImpl(plan, 1, null, null, null);
    }

    @DataProvider(parallel = true)
    Object[][] testSshConnectionToGitHubData()
    {
        return new Object[][]{
                {"git@github.com:cixot/test.git", "bamboo-git-plugin-tests-passphrased.id_rsa", "passphrase"},
                {"git@github.com:cixot/test.git", "bamboo-git-plugin-tests-passphraseless.id_rsa", null},
        };
    }

    @Test(dataProvider = "testSshConnectionToGitHubData")
    public void testSshConnectionToGitHub(String repositoryUrl, String sshKeyfile, String sshPassphrase) throws Exception
    {
        GitRepository gitRepository = createGitRepository();
        String sshKey = FileUtils.readFileToString(new File(Thread.currentThread().getContextClassLoader().getResource(sshKeyfile).toURI()));
        setRepositoryProperties(gitRepository, repositoryUrl, "master", sshKey, sshPassphrase);

        BuildChanges changes = gitRepository.collectChangesSinceLastBuild(PLAN_KEY, null);
        gitRepository.retrieveSourceCode(mockBuildContext(), changes.getVcsRevisionKey());
    }

    @DataProvider
    Object[][] invalidMavenPaths()
    {
        return new String[][] {
                {".."},
                {"../relative/pom.xml"},
                {"path/../path/pom.xml"},
        };
    }

    @Test(dataProvider = "invalidMavenPaths")
    public void testValidateDots(String mavenPath) throws Exception
    {
        BuildConfiguration conf = new BuildConfiguration();
        conf.setProperty("repository.git.maven.path", mavenPath);

        GitRepository repo = new GitRepository();
        repo.setTextProvider(Mockito.mock(TextProvider.class, new ReturnsMocks()));
        ErrorCollection errorCollection = repo.validate(conf);
        Assert.assertNotNull(errorCollection.getErrors().get("repository.git.maven.path"));
    }

    // todo: pstefaniak: this test is not working correctly actually, I plan to fix it sooner or later
    @Test
    public void testRecoveringFromErrorsDuringCodeCheckout() throws Exception
    {
        File testRepository = createTempDirectory();
        ZipResourceDirectory.copyZipResourceToDirectory("bamboo-git-plugin-repo.zip", testRepository);

        GitRepository gitRepository = createGitRepository();
        gitRepository.setTextProvider(Mockito.mock(TextProvider.class, new ReturnsMocks()));
        setRepositoryProperties(gitRepository, testRepository, "master");

        gitRepository.collectChangesSinceLastBuild(PLAN_KEY, null);

        BuildChanges changes = gitRepository.collectChangesSinceLastBuild(PLAN_KEY, null); //fetch cache!
        gitRepository.retrieveSourceCode(mockBuildContext(), changes.getVcsRevisionKey());
        verifyContents(gitRepository.getSourceCodeDirectory(PLAN_KEY), "bamboo-git-plugin-repo-contents-7ffea3f46b8cd9c5b5d626528c0bea4e31aec705.zip");

        FileUtils.deleteQuietly(testRepository);
        assertTrue(testRepository.mkdirs());
        ZipResourceDirectory.copyZipResourceToDirectory("basic-repository.zip", testRepository);

        changes = gitRepository.collectChangesSinceLastBuild(PLAN_KEY, changes.getVcsRevisionKey());
        gitRepository.retrieveSourceCode(mockBuildContext(), changes.getVcsRevisionKey());
        verifyContents(gitRepository.getSourceCodeDirectory(PLAN_KEY), "basic-repo-contents-a26ff19c3c63e19d6a57a396c764b140f48c530a.zip");

        File victimOfRandomFailure = new File(gitRepository.getWorkingDirectory(), "sparta.txt");
        FileUtils.deleteQuietly(victimOfRandomFailure);
        victimOfRandomFailure = new File(gitRepository.getWorkingDirectory(), "shodan.txt");
        FileUtils.deleteQuietly(victimOfRandomFailure);

        gitRepository.retrieveSourceCode(mockBuildContext(), "2e20b0733759facbeb0dec6ee345d762dbc8eed8");
        verifyContents(gitRepository.getSourceCodeDirectory(PLAN_KEY), "basic-repo-contents-2e20b0733759facbeb0dec6ee345d762dbc8eed8.zip");
    }
}
