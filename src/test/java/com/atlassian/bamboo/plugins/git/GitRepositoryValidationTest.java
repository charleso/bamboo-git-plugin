package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;
import com.opensymphony.xwork.TextProvider;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.defaultanswers.ReturnsMocks;
import org.testng.Assert;
import org.testng.annotations.*;

public class GitRepositoryValidationTest
{

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

        GitRepository repo = createRepository();
        ErrorCollection errorCollection = repo.validate(conf);
        Assert.assertNotNull(errorCollection.getErrors().get("repository.git.maven.path"));
    }

    @DataProvider(parallel = true)
    Object[][] validUrlUsernameCombinations()
    {
        return new String [][] {
                { "http://host/repo", null},
                { "http://host/repo", "username"},
                { "https://host/repo", null},
                { "https://host/repo", "username"},
                { "ssh://user@host/repo", null},
                { "user@host:repo", null},

        };
    }


    @Test(dataProvider = "validUrlUsernameCombinations")
    public void testValidUrlUsernameCombinations(String url, String username) throws Exception
    {
        GitRepository repository = createRepository();
        BuildConfiguration buildConfiguration = new BuildConfiguration();

        buildConfiguration.setProperty("repository.git.repositoryUrl", url);
        buildConfiguration.setProperty("repository.git.username", username);

        ErrorCollection errorCollection = repository.validate(buildConfiguration);
        Assert.assertFalse(errorCollection.hasAnyErrors());
    }

    @DataProvider(parallel = true)
    Object[][] invalidUrlUsernameCombinations()
    {
        return new String [][] {
                { "ssh://host/repo", "username"},
                { "host/repo", "username"},
                { "/host/repo", "username"},
        };
    }

    @Test(dataProvider = "invalidUrlUsernameCombinations")
    public void testInvalidUrlUsernameCombinations(String url, String username) throws Exception
    {
        GitRepository repository = createRepository();
        BuildConfiguration buildConfiguration = new BuildConfiguration();

        buildConfiguration.setProperty("repository.git.repositoryUrl", url);
        buildConfiguration.setProperty("repository.git.username", username);

        ErrorCollection errorCollection = repository.validate(buildConfiguration);
        Assert.assertTrue(errorCollection.hasAnyErrors());
        Assert.assertNotNull(errorCollection.getFieldErrors().get("repository.git.username"));
    }

    @Test
    public void testRejectUsernameFieldForNonHttp() throws Exception
    {
        GitRepository repository = new GitRepository();
        BuildConfiguration buildConfiguration = new BuildConfiguration();

        buildConfiguration.setProperty("repository.git.repositoryUrl", "ssh://host/repo");

    }

    private static GitRepository createRepository()
    {
        GitRepository repo = new GitRepository();
        repo.setTextProvider(Mockito.mock(TextProvider.class, new ReturnsMocks()));
        return repo;
    }
}
