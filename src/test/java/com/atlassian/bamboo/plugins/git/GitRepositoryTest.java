package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.build.fileserver.BuildDirectoryManager;
import com.atlassian.bamboo.commit.CommitContext;
import com.atlassian.bamboo.repository.NameValuePair;
import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.v2.build.BuildRepositoryChanges;
import com.atlassian.bamboo.v2.build.agent.remote.RemoteBuildDirectoryManager;
import com.atlassian.testtools.ZipResourceDirectory;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.errors.TransportException;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.util.List;

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



    @DataProvider(parallel = false)
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

    //@Test Not Needed?
    //public void testGitRepositoryIsSerializable() throws Exception
    //{
    //    GitRepository repository = createGitRepository();
    //
    //    String repositoryUrl = "url";
    //    String branch = "master";
    //    String sshKey = "ssh_key";
    //    String sshPassphrase = "ssh passphrase";
    //
    //    setRepositoryProperties(repository, repositoryUrl, branch, sshKey, sshPassphrase);
    //    assertEquals(repository.accessData.authenticationType, GitAuthenticationType.SSH_KEYPAIR, "Precondition");
    //
    //    ByteArrayOutputStream os = new ByteArrayOutputStream();
    //    ObjectOutputStream oos = new ObjectOutputStream(os);
    //    oos.writeObject(repository);
    //    oos.close();
    //
    //    ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(os.toByteArray()));
    //    Object object = ois.readObject();
    //
    //    GitRepository out = (GitRepository) object;
    //
    //    StringEncrypter encrypter = new StringEncrypter();
    //
    //    assertEquals(out.getRepositoryUrl(), repositoryUrl);
    //    assertEquals(out.getBranch(), branch);
    //    assertEquals(encrypter.decrypt(out.accessData.sshKey), sshKey);
    //    assertEquals(encrypter.decrypt(out.accessData.sshPassphrase), sshPassphrase);
    //    assertEquals(out.accessData.authenticationType, GitAuthenticationType.SSH_KEYPAIR);
    //}

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
    
    @Test
    public void testPushingRevision() throws Exception
    {
        File testRepository = createTempDirectory();
        ZipResourceDirectory.copyZipResourceToDirectory("basic-repository.zip", testRepository);
        int commitsBeforePushCount = createJGitOperationHelper(null).extractCommits(testRepository, null, "HEAD").getChanges().size();

        GitRepository gitRepository = createGitRepository();
        setRepositoryProperties(gitRepository, testRepository, "master");

        BuildRepositoryChanges buildChanges = gitRepository.collectChangesSinceLastBuild(PLAN_KEY.getKey(), null);
        
        File checkoutDir = getCheckoutDir(gitRepository);

        gitRepository.retrieveSourceCode(mockBuildContext(), buildChanges.getVcsRevisionKey(), checkoutDir);

        GitTestRepository srcRepo = new GitTestRepository(checkoutDir);
        String commitedRevision = srcRepo.commitFileContents("contents").name();
        gitRepository.pushRevision(checkoutDir, commitedRevision);

        //verify somehow that testRepository contain commited revision...
        List<CommitContext> commitsAfterPush = createJGitOperationHelper(null).extractCommits(checkoutDir, null, "HEAD").getChanges();
        assertEquals(commitsAfterPush.size(), commitsBeforePushCount + 1);
        assertEquals(commitsAfterPush.get(0).getChangeSetId(), commitedRevision);
    }

    //@Test
    //public void testCommitting() throws Exception
    //{
    //    File testRepository = createTempDirectory();
    //    ZipResourceDirectory.copyZipResourceToDirectory("basic-repository.zip", testRepository);
    //    int commitsBeforeCommitCount = createJGitOperationHelper(null).extractCommits(testRepository, null, "HEAD").getChanges().size();
    //
    //    GitRepository gitRepository = createGitRepository();
    //    setRepositoryProperties(gitRepository, testRepository, "master");
    //
    //    BuildRepositoryChanges buildChanges = gitRepository.collectChangesSinceLastBuild(PLAN_KEY.getKey(), null);
    //    File checkoutDir = getCheckoutDir(gitRepository);
    //    gitRepository.retrieveSourceCode(mockBuildContext(), buildChanges.getVcsRevisionKey(), checkoutDir);
    //    String committedRevision1 = gitRepository.commit(checkoutDir, "Message");
    //
    //    //verify somehow that testRepository contain commited revision...
    //    List<CommitContext> commitsAfterCommit = createJGitOperationHelper(null).extractCommits(checkoutDir, null, "HEAD").getChanges();
    //    assertEquals(commitsAfterCommit.size(), commitsBeforeCommitCount + 1);
    //    assertEquals(commitsAfterCommit.get(0).getChangeSetId(), committedRevision1);
    //
    //    String committedRevision2 = gitRepository.commit(checkoutDir, "Message");
    //    assertEquals(committedRevision1, committedRevision1);
    //}

    @Test
    public void testCommittingWithNativeGit() throws Exception
    {
        final String author = COMITTER_NAME + " <" + COMITTER_EMAIL + ">";
        final String filename = "sparta.txt";
        final String commitMessage = "Message\n";

        File testRepository = createTempDirectory();
        ZipResourceDirectory.copyZipResourceToDirectory("basic-repository.zip", testRepository);

        GitRepository gitRepository = createNativeGitRepository();
        setRepositoryProperties(gitRepository, testRepository, "master");

        String revisionBeforeCommit = gitRepository.collectChangesSinceLastBuild(PLAN_KEY.getKey(), null).getVcsRevisionKey();
        File checkoutDir = getCheckoutDir(gitRepository);
        gitRepository.retrieveSourceCode(mockBuildContext(), revisionBeforeCommit, checkoutDir);
        
        File modifiedFile = new File(checkoutDir, filename);
        FileUtils.writeStringToFile(modifiedFile, "miej serce... i paczaj w serce...");
        String committedRevision = gitRepository.commit(checkoutDir, commitMessage);

        //verify
        List<CommitContext> commitsAfterCommit = createNativeGitOperationHelper(createAccessData(testRepository.getAbsolutePath())).extractCommits(checkoutDir, revisionBeforeCommit, "HEAD").getChanges();
        assertEquals(commitsAfterCommit.size(), 1);
        CommitContext commit = commitsAfterCommit.get(0);
        assertEquals(commit.getChangeSetId(), committedRevision);
        assertEquals(commit.getAuthor().getName(), author);
        assertEquals(commit.getComment(), commitMessage);
        assertEquals(commit.getFiles().get(0).getName(), filename);
    }

    @Test
    public void testPushingWithNativeGit() throws Exception
    {
        final String author = COMITTER_NAME + " <" + COMITTER_EMAIL + ">";
        final String filename = "sparta.txt";
        final String commitMessage = "Message\n";

        File testRepository = createTempDirectory();
        ZipResourceDirectory.copyZipResourceToDirectory("basic-repository.zip", testRepository);
        File gitConfig = new File(new File(testRepository, ".git"), "config");
        FileUtils.writeStringToFile(gitConfig, FileUtils.readFileToString(gitConfig) + "\n[receive]\n    denyCurrentBranch = ignore\n");

        GitRepository gitRepository = createNativeGitRepository();
        setRepositoryProperties(gitRepository, testRepository, "master");

        String revisionBeforeCommit = gitRepository.collectChangesSinceLastBuild(PLAN_KEY.getKey(), null).getVcsRevisionKey();
        File checkoutDir = getCheckoutDir(gitRepository);
        gitRepository.retrieveSourceCode(mockBuildContext(), revisionBeforeCommit, checkoutDir);

        File modifiedFile = new File(checkoutDir, filename);
        FileUtils.writeStringToFile(modifiedFile, "miej serce... i paczaj w serce...");
        String committedRevision = gitRepository.commit(checkoutDir, commitMessage);

        gitRepository.pushRevision(checkoutDir, committedRevision);

        //verify
        List<CommitContext> commitsAfterCommit = createNativeGitOperationHelper(createAccessData(testRepository.getAbsolutePath())).extractCommits(testRepository, revisionBeforeCommit, "HEAD").getChanges();
        assertEquals(commitsAfterCommit.size(), 1);
        CommitContext commit = commitsAfterCommit.get(0);
        assertEquals(commit.getChangeSetId(), committedRevision);
        assertEquals(commit.getAuthor().getName(), author);
        assertEquals(commit.getComment(), commitMessage);
        assertEquals(commit.getFiles().get(0).getName(), filename);
    }
}
