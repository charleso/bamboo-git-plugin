package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;
import com.opensymphony.xwork.TextProvider;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.defaultanswers.ReturnsMocks;
import org.testng.Assert;
import org.testng.annotations.*;

import static com.atlassian.bamboo.plugins.git.GitAuthenticationType.NONE;
import static com.atlassian.bamboo.plugins.git.GitAuthenticationType.PASSWORD;
import static com.atlassian.bamboo.plugins.git.GitAuthenticationType.SSH_KEYPAIR;

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
        return new Object [][] {
                { "http://host/repo", null, NONE},
                { "http://host/repo", null, PASSWORD}, // let's accept empty passwords so far
                { "http://host/repo", "username", NONE}, // set but ignored - no reason to complain
                { "http://host/repo", "username", PASSWORD}, // set but ignored - no reason to complain

                // same as above but https
                { "https://host/repo", null, NONE},
                { "https://host/repo", null, PASSWORD}, // let's accept empty passwords so far
                { "https://host/repo", "username", NONE}, // set but ignored - no reason to complain
                { "https://host/repo", "username", PASSWORD}, // set but ignored - no reason to complain

                { "ssh://user@host/repo", null, NONE}, // let's assume it's OK so far
                { "ssh://user@host/repo", null, PASSWORD},
                { "ssh://user@host/repo", null, SSH_KEYPAIR},

                { "user@host:repo", null, NONE},
                { "user@host:repo", null, PASSWORD},
                { "user@host:repo", null, SSH_KEYPAIR},

                // username should be ignored if PASSWORD is not selected
                { "ssh://user@host/repo", "username", NONE}, // let's assume it's OK so far
                { "ssh://user@host/repo", "username", SSH_KEYPAIR},

                { "user@host:repo", "username", NONE},
                { "user@host:repo", "username", SSH_KEYPAIR},
        };
    }


    @Test(dataProvider = "validUrlUsernameCombinations")
    public void testValidUrlUsernameCombinations(String url, String username, GitAuthenticationType authenticationType) throws Exception
    {
        ErrorCollection errorCollection = doValidateConfiguration(url, username, authenticationType);

        Assert.assertFalse(errorCollection.hasAnyErrors());
    }

    @DataProvider(parallel = true)
    Object[][] invalidUrlUsernameCombinations()
    {
        return new Object [][] {
                { "ssh://host/repo", "username", PASSWORD},
                { "host/repo", "username", PASSWORD},
                { "/host/repo", "username", PASSWORD},
        };
    }

    @Test(dataProvider = "invalidUrlUsernameCombinations")
    public void testInvalidUrlUsernameCombinations(String url, String username, GitAuthenticationType authenticationType) throws Exception
    {
        ErrorCollection errorCollection = doValidateConfiguration(url, username, authenticationType);

        Assert.assertTrue(errorCollection.hasAnyErrors());
        Assert.assertNotNull(errorCollection.getFieldErrors().get("repository.git.username"));
    }

    @Test
    public void testInvalidAuthForHttp() throws Exception
    {
        ErrorCollection errorCollection = doValidateConfiguration("http://host/repo", null, SSH_KEYPAIR);

        Assert.assertTrue(errorCollection.hasAnyErrors());
        Assert.assertNotNull(errorCollection.getFieldErrors().get("repository.git.authenticationType"));
    }

    private ErrorCollection doValidateConfiguration(String url, String username, GitAuthenticationType authenticationType)
    {
        GitRepository repository = createRepository();
        BuildConfiguration buildConfiguration = new BuildConfiguration();

        buildConfiguration.setProperty("repository.git.repositoryUrl", url);
        buildConfiguration.setProperty("repository.git.username", username);
        buildConfiguration.setProperty("repository.git.authenticationType", authenticationType.name());

        return repository.validate(buildConfiguration);
    }

    private static GitRepository createRepository()
    {
        GitRepository repo = new GitRepository();
        repo.setTextProvider(Mockito.mock(TextProvider.class, new ReturnsMocks()));
        return repo;
    }
}
