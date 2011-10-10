package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.build.fileserver.BuildDirectoryManager;
import com.atlassian.bamboo.repository.NameValuePair;
import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.security.StringEncrypter;
import com.atlassian.bamboo.v2.build.BuildRepositoryChanges;
import com.atlassian.bamboo.v2.build.agent.remote.RemoteBuildDirectoryManager;
import com.atlassian.testtools.ZipResourceDirectory;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.errors.TransportException;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class GitRepositoryTest extends GitAbstractTest
{
    @Test
    public void testBasicFunctionality() throws Exception
    {
        GitRepository gitRepository = createGitRepository();
        setRepositoryProperties(gitRepository, "git://github.com/cixot/test.git", "master");

        BuildRepositoryChanges changes = gitRepository.collectChangesSinceLastBuild(PLAN_KEY.getKey(), null);

        gitRepository.retrieveSourceCode(mockBuildContext(), changes.getVcsRevisionKey(), getCheckoutDir(gitRepository));
    }

    @Test
    public void testNetworkErrorsDontRemoveCache() throws Exception
    {
        GitRepository gitRepository = createGitRepository();
        setRepositoryProperties(gitRepository, "git://localhost:3200/cixot/test.git", "master");

        final File cacheDirectory = gitRepository.getCacheDirectory();
        cacheDirectory.mkdirs();
        final File file = File.createTempFile("git-plugin", null, cacheDirectory);
        assertTrue(file.exists());
        try
        {
            gitRepository.retrieveSourceCode(mockBuildContext(), "foobar", getCheckoutDir(gitRepository));
        }
        catch (Exception e)
        {
            if (e instanceof RepositoryException && e.getCause() instanceof TransportException)
            {
                assertTrue(file.exists());
            }
            else
            {
                fail("Unexpected exception", e);
            }
        }
    }

    @DataProvider(parallel = false)
    Object[][] testSourceCodeRetrievalData()
    {
        return new Object[][]{
                {"a26ff19c3c63e19d6a57a396c764b140f48c530a", "master",   "basic-repo-contents-a26ff19c3c63e19d6a57a396c764b140f48c530a.zip"},
                {"2e20b0733759facbeb0dec6ee345d762dbc8eed8", "master",   "basic-repo-contents-2e20b0733759facbeb0dec6ee345d762dbc8eed8.zip"},
                {"55676cfa3db13bcf659b2a35e5d61eba478ed54d", "master",   "basic-repo-contents-55676cfa3db13bcf659b2a35e5d61eba478ed54d.zip"},
                {null,                                       "myBranch", "basic-repo-contents-4367e71d438f091a5e85304618a8f78f9db6738e.zip"},
        };
    }

    @Test(dataProvider = "testSourceCodeRetrievalData")
    public void testSourceCodeRetrieval(String targetRevision, String branch, String expectedContentsInZip) throws Exception
    {
        File testRepository = createTempDirectory();
        ZipResourceDirectory.copyZipResourceToDirectory("basic-repository.zip", testRepository);

        GitRepository gitRepository = createGitRepository();
        setRepositoryProperties(gitRepository, testRepository, branch);

        gitRepository.retrieveSourceCode(mockBuildContext(), targetRevision, getCheckoutDir(gitRepository));
        verifyContents(getCheckoutDir(gitRepository), expectedContentsInZip);
    }

    @Test(dataProvider = "testSourceCodeRetrievalData")
    public void testSourceCodeRetrievalOnRemoteAgent(String targetRevision, String branch, String expectedContentsInZip) throws Exception
    {
        File testRepository = createTempDirectory();
        ZipResourceDirectory.copyZipResourceToDirectory("basic-repository.zip", testRepository);

        GitRepository gitRepository = createGitRepository();
        File workingDirectory = gitRepository.getWorkingDirectory();
        BuildDirectoryManager buildDirectoryManager = new RemoteBuildDirectoryManager();
        gitRepository.setBuildDirectoryManager(buildDirectoryManager);
        gitRepository.setWorkingDir(workingDirectory);

        setRepositoryProperties(gitRepository, testRepository, branch);

        gitRepository.retrieveSourceCode(mockBuildContext(), targetRevision, getCheckoutDir(gitRepository));
        verifyContents(getCheckoutDir(gitRepository), expectedContentsInZip);
    }



    @DataProvider(parallel = true)
    Object[][] testSshConnectionToGitHubData()
    {
        return new Object[][]{
                {"git@github.com:bamboo-git-plugin-tests/test.git", "bamboo-git-plugin-tests-passphrased.id_rsa", "passphrase"},
                {"git@github.com:bamboo-git-plugin-tests/test.git", "bamboo-git-plugin-tests-passphraseless.id_rsa", null},
        };
    }

    @Test(dataProvider = "testSshConnectionToGitHubData")
    public void testSshConnectionToGitHub(String repositoryUrl, String sshKeyfile, String sshPassphrase) throws Exception
    {
        GitRepository gitRepository = createGitRepository();
        String sshKey = FileUtils.readFileToString(new File(Thread.currentThread().getContextClassLoader().getResource(sshKeyfile).toURI()));
        setRepositoryProperties(gitRepository, repositoryUrl, "master", sshKey, sshPassphrase);

        BuildRepositoryChanges changes = gitRepository.collectChangesSinceLastBuild(PLAN_KEY.getKey(), null);
        gitRepository.retrieveSourceCode(mockBuildContext(), changes.getVcsRevisionKey(), getCheckoutDir(gitRepository));
    }

    @Test
    public void testAuthenticationTypesHaveValidLabels() throws Exception
    {
        GitRepository gitRepository = createGitRepository();
        for (NameValuePair nameValuePair : gitRepository.getAuthenticationTypes())
        {
            Assert.assertNotNull(nameValuePair.getName());
            Assert.assertNotNull(nameValuePair.getLabel());

            Assert.assertFalse(nameValuePair.getLabel().startsWith("repository.git."), "Expecting human readable: " + nameValuePair.getLabel());
        }
    }

    @Test
    public void testGitRepositoryIsSerializable() throws Exception
    {
        GitRepository repository = createGitRepository();

        String repositoryUrl = "url";
        String branch = "master";
        String sshKey = "ssh_key";
        String sshPassphrase = "ssh passphrase";

        setRepositoryProperties(repository, repositoryUrl, branch, sshKey, sshPassphrase);
        assertEquals(repository.accessData.authenticationType, GitAuthenticationType.SSH_KEYPAIR, "Precondition");

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(os);
        oos.writeObject(repository);
        oos.close();

        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(os.toByteArray()));
        Object object = ois.readObject();

        GitRepository out = (GitRepository) object;

        StringEncrypter encrypter = new StringEncrypter();

        assertEquals(out.getRepositoryUrl(), repositoryUrl);
        assertEquals(out.getBranch(), branch);
        assertEquals(encrypter.decrypt(out.accessData.sshKey), sshKey);
        assertEquals(encrypter.decrypt(out.accessData.sshPassphrase), sshPassphrase);
        assertEquals(out.accessData.authenticationType, GitAuthenticationType.SSH_KEYPAIR);
    }

    @Test
    public void testRepositoryChangesetLimit() throws Exception
    {
        File tmp = createTempDirectory();
        ZipResourceDirectory.copyZipResourceToDirectory("150changes.zip", tmp);

        GitRepository repository = createGitRepository();
        setRepositoryProperties(repository, tmp);

        BuildRepositoryChanges buildChanges = repository.collectChangesSinceLastBuild(PLAN_KEY.getKey(), "1fea1bc1ff3a0a2a2ad5b15dc088323b906e81d7");

        assertEquals(buildChanges.getChanges().size(), 100);
        assertEquals(buildChanges.getSkippedCommitsCount(), 49);

        for (int i = 0; i < buildChanges.getChanges().size(); i++)
        {
            assertEquals(buildChanges.getChanges().get(i).getComment(), Integer.toString(150 - i) + "\n");
        }
    }

    @Test
    public void testRepositoryInitialDetectionDoesntReturnChangesets() throws Exception
    {
        File testRepository = createTempDirectory();
        ZipResourceDirectory.copyZipResourceToDirectory("basic-repository.zip", testRepository);

        GitRepository gitRepository = createGitRepository();
        setRepositoryProperties(gitRepository, testRepository, "master");

        BuildRepositoryChanges changes = gitRepository.collectChangesSinceLastBuild(PLAN_KEY.getKey(), null);
        assertEquals(changes.getChanges().size(), 0);
    }
}
