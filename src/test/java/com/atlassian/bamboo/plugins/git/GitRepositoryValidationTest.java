package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;
import com.opensymphony.xwork.TextProvider;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.defaultanswers.ReturnsMocks;
import org.testng.Assert;
import org.testng.annotations.*;

import java.util.List;

import static com.atlassian.bamboo.plugins.git.GitAuthenticationType.NONE;
import static com.atlassian.bamboo.plugins.git.GitAuthenticationType.PASSWORD;
import static com.atlassian.bamboo.plugins.git.GitAuthenticationType.SSH_KEYPAIR;
import static com.google.common.collect.Lists.newArrayList;

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
    Object[][] urlUsernamePasswordCombinations()
    {
        return new Object [][] {
                { "http://host/repo", null, null, NONE, null},
                { "http://host/repo", null, null, PASSWORD, null}, // let's accept empty username and passwords so far
                { "http://host/repo", null, null, SSH_KEYPAIR, newArrayList("repository.git.authenticationType")}, // invalid - you can't use http with keyfile
                { "http://host/repo", "username", null, NONE, null}, // set but ignored - no reason to complain
                { "http://host/repo", "username", null, PASSWORD, null},
                { "http://host/repo", "username", null, SSH_KEYPAIR, newArrayList("repository.git.authenticationType")}, // invalid - you can't use http with keyfile
                { "http://host/repo", null, "password", NONE, null}, // set but ignored - no reason to complain
                { "http://host/repo", null, "password", PASSWORD, null},
                { "http://host/repo", null, "password", SSH_KEYPAIR, newArrayList("repository.git.authenticationType")}, // invalid - you can't use http with keyfile
                { "http://host/repo", "username", "password", NONE, null}, // set but ignored - no reason to complain
                { "http://host/repo", "username", "password", PASSWORD, null},
                { "http://host/repo", "username", "password", SSH_KEYPAIR, newArrayList("repository.git.authenticationType")}, // invalid - you can't use http with keyfile
                { "http://username@host/repo", null, null, NONE, null},
                { "http://username@host/repo", null, null, PASSWORD, null},
                { "http://username@host/repo", null, null, SSH_KEYPAIR, newArrayList("repository.git.authenticationType")}, // invalid - you can't use http with keyfile
                { "http://username@host/repo", "username", null, NONE, null}, // duplicate username, but ignored - no reason to complain
                { "http://username@host/repo", "username", null, PASSWORD, newArrayList("repository.git.repositoryUrl", "repository.git.username")}, //duplicate username
                { "http://username@host/repo", "username", null, SSH_KEYPAIR, newArrayList("repository.git.authenticationType")}, // invalid - you can't use http with keyfile
                { "http://username@host/repo", null, "password", NONE, null}, // password set but ignored - no reason to complain
                { "http://username@host/repo", null, "password", PASSWORD, null},
                { "http://username@host/repo", null, "password", SSH_KEYPAIR, newArrayList("repository.git.authenticationType")}, // invalid - you can't use http with keyfile
                { "http://username@host/repo", "username", "password", NONE, null}, // duplicate username but ignored - no reason to complain
                { "http://username@host/repo", "username", "password", PASSWORD, newArrayList("repository.git.repositoryUrl", "repository.git.username")}, //duplicate username},
                { "http://username@host/repo", "username", "password", SSH_KEYPAIR, newArrayList("repository.git.authenticationType")}, // invalid - you can't use http with keyfile
                { "http://username:password@host/repo", null, null, NONE, null},
                { "http://username:password@host/repo", null, null, PASSWORD, null},
                { "http://username:password@host/repo", null, null, SSH_KEYPAIR, newArrayList("repository.git.authenticationType")}, // invalid - you can't use http with keyfile
                { "http://username:password@host/repo", "username", null, NONE, null}, // duplicate username, but ignored - no reason to complain
                { "http://username:password@host/repo", "username", null, PASSWORD, newArrayList("repository.git.repositoryUrl", "repository.git.username")}, //duplicate username
                { "http://username:password@host/repo", "username", null, SSH_KEYPAIR, newArrayList("repository.git.authenticationType")}, // invalid - you can't use http with keyfile
                { "http://username:password@host/repo", null, "password", NONE, null}, // duplicate password but ignored - no reason to complain
                { "http://username:password@host/repo", null, "password", PASSWORD, newArrayList("repository.git.repositoryUrl", "temporary.git.password.change")}, //duplicate password
                { "http://username:password@host/repo", null, "password", SSH_KEYPAIR, newArrayList("repository.git.authenticationType")}, // invalid - you can't use http with keyfile
                { "http://username:password@host/repo", "username", "password", NONE, null}, // duplicate username and password but ignored - no reason to complain
                { "http://username:password@host/repo", "username", "password", PASSWORD, newArrayList("repository.git.repositoryUrl", "repository.git.username", "temporary.git.password.change")}, //duplicate username and password
                { "http://username:password@host/repo", "username", "password", SSH_KEYPAIR, newArrayList("repository.git.authenticationType")}, // invalid - you can't use http with keyfile

                // same as above but https
                { "https://host/repo", null, null, NONE, null},
                { "https://host/repo", null, null, PASSWORD, null}, // let's accept empty username and passwords so far
                { "https://host/repo", null, null, SSH_KEYPAIR, newArrayList("repository.git.authenticationType")}, // invalid - you can't use http with keyfile
                { "https://host/repo", "username", null, NONE, null}, // set but ignored - no reason to complain
                { "https://host/repo", "username", null, PASSWORD, null},
                { "https://host/repo", "username", null, SSH_KEYPAIR, newArrayList("repository.git.authenticationType")}, // invalid - you can't use http with keyfile
                { "https://host/repo", null, "password", NONE, null}, // set but ignored - no reason to complain
                { "https://host/repo", null, "password", PASSWORD, null},
                { "https://host/repo", null, "password", SSH_KEYPAIR, newArrayList("repository.git.authenticationType")}, // invalid - you can't use http with keyfile
                { "https://host/repo", "username", "password", NONE, null}, // set but ignored - no reason to complain
                { "https://host/repo", "username", "password", PASSWORD, null},
                { "https://host/repo", "username", "password", SSH_KEYPAIR, newArrayList("repository.git.authenticationType")}, // invalid - you can't use http with keyfile
                { "https://username@host/repo", null, null, NONE, null},
                { "https://username@host/repo", null, null, PASSWORD, null},
                { "https://username@host/repo", null, null, SSH_KEYPAIR, newArrayList("repository.git.authenticationType")}, // invalid - you can't use http with keyfile
                { "https://username@host/repo", "username", null, NONE, null}, // duplicate username, but ignored - no reason to complain
                { "https://username@host/repo", "username", null, PASSWORD, newArrayList("repository.git.repositoryUrl", "repository.git.username")}, //duplicate username
                { "https://username@host/repo", "username", null, SSH_KEYPAIR, newArrayList("repository.git.authenticationType")}, // invalid - you can't use http with keyfile
                { "https://username@host/repo", null, "password", NONE, null}, // password set but ignored - no reason to complain
                { "https://username@host/repo", null, "password", PASSWORD, null},
                { "https://username@host/repo", null, "password", SSH_KEYPAIR, newArrayList("repository.git.authenticationType")}, // invalid - you can't use http with keyfile
                { "https://username@host/repo", "username", "password", NONE, null}, // duplicate username but ignored - no reason to complain
                { "https://username@host/repo", "username", "password", PASSWORD, newArrayList("repository.git.repositoryUrl", "repository.git.username")}, //duplicate username},
                { "https://username@host/repo", "username", "password", SSH_KEYPAIR, newArrayList("repository.git.authenticationType")}, // invalid - you can't use http with keyfile
                { "https://username:password@host/repo", null, null, NONE, null},
                { "https://username:password@host/repo", null, null, PASSWORD, null},
                { "https://username:password@host/repo", null, null, SSH_KEYPAIR, newArrayList("repository.git.authenticationType")}, // invalid - you can't use http with keyfile
                { "https://username:password@host/repo", "username", null, NONE, null}, // duplicate username, but ignored - no reason to complain
                { "https://username:password@host/repo", "username", null, PASSWORD, newArrayList("repository.git.repositoryUrl", "repository.git.username")}, //duplicate username
                { "https://username:password@host/repo", "username", null, SSH_KEYPAIR, newArrayList("repository.git.authenticationType")}, // invalid - you can't use http with keyfile
                { "https://username:password@host/repo", null, "password", NONE, null}, // duplicate password but ignored - no reason to complain
                { "https://username:password@host/repo", null, "password", PASSWORD, newArrayList("repository.git.repositoryUrl", "temporary.git.password.change")}, //duplicate password
                { "https://username:password@host/repo", null, "password", SSH_KEYPAIR, newArrayList("repository.git.authenticationType")}, // invalid - you can't use http with keyfile
                { "https://username:password@host/repo", "username", "password", NONE, null}, // duplicate username and password but ignored - no reason to complain
                { "https://username:password@host/repo", "username", "password", PASSWORD, newArrayList("repository.git.repositoryUrl", "repository.git.username", "temporary.git.password.change")}, //duplicate username and password
                { "https://username:password@host/repo", "username", "password", SSH_KEYPAIR, newArrayList("repository.git.authenticationType")}, // invalid - you can't use http with keyfile

                { "ssh://host/repo", null, null, NONE, null},
                { "ssh://host/repo", null, null, PASSWORD, null}, // let's accept empty username and passwords so far
                { "ssh://host/repo", null, null, SSH_KEYPAIR, null},
                { "ssh://host/repo", "username", null, NONE, null}, // set but ignored - no reason to complain
                { "ssh://host/repo", "username", null, PASSWORD, null},
                { "ssh://host/repo", "username", null, SSH_KEYPAIR, null}, // set but ignored - no reason to complain
                { "ssh://host/repo", null, "password", NONE, null}, // set but ignored - no reason to complain
                { "ssh://host/repo", null, "password", PASSWORD, null},
                { "ssh://host/repo", null, "password", SSH_KEYPAIR, null}, // set but ignored - no reason to complain
                { "ssh://host/repo", "username", "password", NONE, null}, // set but ignored - no reason to complain
                { "ssh://host/repo", "username", "password", PASSWORD, null},
                { "ssh://host/repo", "username", "password", SSH_KEYPAIR, null}, // set but ignored - no reason to complain
                { "ssh://username@host/repo", null, null, NONE, null},
                { "ssh://username@host/repo", null, null, PASSWORD, null},
                { "ssh://username@host/repo", null, null, SSH_KEYPAIR, null},
                { "ssh://username@host/repo", "username", null, NONE, null}, // duplicate username, but ignored - no reason to complain
                { "ssh://username@host/repo", "username", null, PASSWORD, newArrayList("repository.git.repositoryUrl", "repository.git.username")}, //duplicate username
                { "ssh://username@host/repo", "username", null, SSH_KEYPAIR, null}, // duplicate username but ignored - no reason to complain
                { "ssh://username@host/repo", null, "password", NONE, null}, // password set but ignored - no reason to complain
                { "ssh://username@host/repo", null, "password", PASSWORD, null},
                { "ssh://username@host/repo", null, "password", SSH_KEYPAIR, null},
                { "ssh://username@host/repo", "username", "password", NONE, null}, // duplicate username but ignored - no reason to complain
                { "ssh://username@host/repo", "username", "password", PASSWORD, newArrayList("repository.git.repositoryUrl", "repository.git.username")}, //duplicate username},
                { "ssh://username@host/repo", "username", "password", SSH_KEYPAIR, null}, // duplicate username but ignored - no reason to complain
                { "ssh://username:password@host/repo", null, null, NONE, null},
                { "ssh://username:password@host/repo", null, null, PASSWORD, null},
                { "ssh://username:password@host/repo", null, null, SSH_KEYPAIR, null},
                { "ssh://username:password@host/repo", "username", null, NONE, null}, // duplicate username, but ignored - no reason to complain
                { "ssh://username:password@host/repo", "username", null, PASSWORD, newArrayList("repository.git.repositoryUrl", "repository.git.username")}, //duplicate username
                { "ssh://username:password@host/repo", "username", null, SSH_KEYPAIR, null}, // duplicate username, but ignored - no reason to complain
                { "ssh://username:password@host/repo", null, "password", NONE, null}, // duplicate password but ignored - no reason to complain
                { "ssh://username:password@host/repo", null, "password", PASSWORD, newArrayList("repository.git.repositoryUrl", "temporary.git.password.change")}, //duplicate password
                { "ssh://username:password@host/repo", null, "password", SSH_KEYPAIR, null}, // duplicate password but ignored - no reason to complain
                { "ssh://username:password@host/repo", "username", "password", NONE, null}, // duplicate username and password but ignored - no reason to complain
                { "ssh://username:password@host/repo", "username", "password", PASSWORD, newArrayList("repository.git.repositoryUrl", "repository.git.username", "temporary.git.password.change")}, //duplicate username and password
                { "ssh://username:password@host/repo", "username", "password", SSH_KEYPAIR, null}, // duplicate username and password but ignored - no reason to complain

                { "username@host:repo", null, null, NONE, null},
                { "username@host:repo", null, null, PASSWORD, null},
                { "username@host:repo", null, null, SSH_KEYPAIR, null},
                { "username@host:repo", "username", null, NONE, null}, // duplicate username but ignored - no reason to complain
                { "username@host:repo", "username", null, PASSWORD, newArrayList("repository.git.repositoryUrl", "repository.git.username")}, //duplicate username
                { "username@host:repo", "username", null, SSH_KEYPAIR, null}, // duplicate username but ignored - no reason to complain
                { "username@host:repo", null, "password", NONE, null}, // password set but ignored - no reason to complain
                { "username@host:repo", null, "password", PASSWORD, null},
                { "username@host:repo", null, "password", SSH_KEYPAIR, null}, // password set but ignored - no reason to complain
                { "username@host:repo", "username", "password", NONE, null}, // duplicate username but ignored - no reason to complain
                { "username@host:repo", "username", "password", PASSWORD, newArrayList("repository.git.repositoryUrl", "repository.git.username")}, //duplicate username
                { "username@host:repo", "username", "password", SSH_KEYPAIR, null}, // duplicate username but ignored - no reason to complain

                // invalid ones:
                { "host/repo", "username", null, PASSWORD, newArrayList("repository.git.username")},
                { "/host/repo", "username", null, PASSWORD, newArrayList("repository.git.username")},
        };
    }

    @Test(dataProvider = "urlUsernamePasswordCombinations")
    public void testUrlUsernamePasswordCombinations(String url, String username, String password, GitAuthenticationType authenticationType, List<String> expectedErrorFields) throws Exception
    {
        ErrorCollection errorCollection = doValidateConfiguration(url, username, password, authenticationType);

        if (expectedErrorFields != null)
        {
            Assert.assertTrue(errorCollection.hasAnyErrors());
            Assert.assertEquals(errorCollection.getTotalErrors(), expectedErrorFields.size());
            for (String field : expectedErrorFields)
            {
                Assert.assertNotNull(errorCollection.getFieldErrors().get(field), errorCollection.toString());
            }
        }
        else
        {
            Assert.assertFalse(errorCollection.hasAnyErrors());
        }
    }

    @Test
    public void testInvalidAuthForHttp() throws Exception
    {
        ErrorCollection errorCollection = doValidateConfiguration("http://host/repo", null, null, SSH_KEYPAIR);

        Assert.assertTrue(errorCollection.hasAnyErrors());
        Assert.assertNotNull(errorCollection.getFieldErrors().get("repository.git.authenticationType"));
    }

    private ErrorCollection doValidateConfiguration(String url, String username, String password, GitAuthenticationType authenticationType)
    {
        GitRepository repository = createRepository();
        BuildConfiguration buildConfiguration = new BuildConfiguration();

        buildConfiguration.setProperty("repository.git.repositoryUrl", url);
        buildConfiguration.setProperty("repository.git.username", username);
        buildConfiguration.setProperty("repository.git.password", password);
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
