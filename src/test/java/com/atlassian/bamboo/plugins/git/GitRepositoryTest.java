package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.build.BuildLoggerManager;
import com.atlassian.bamboo.build.fileserver.BuildDirectoryManager;
import com.atlassian.bamboo.build.logger.NullBuildLogger;
import com.atlassian.bamboo.plan.Plan;
import com.atlassian.bamboo.security.StringEncrypter;
import com.atlassian.bamboo.v2.build.BuildChanges;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.BuildContextImpl;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.answers.Returns;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import com.atlassian.testtools.ZipResourceDirectory;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.apache.commons.io.FileUtils.listFiles;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertEquals;

public class GitRepositoryTest extends GitAbstractTest
{
    static final String PLAN_KEY = "PLAN-KEY";
    GitRepository createGitRepository() throws Exception
    {
        File workingDirectory = createTempDirectory();

        final GitRepository gitRepository = new GitRepository();

        BuildLoggerManager buildLoggerManager = Mockito.mock(BuildLoggerManager.class, new Returns(new NullBuildLogger()));
        gitRepository.setBuildLoggerManager(buildLoggerManager);

        BuildDirectoryManager buildDirectoryManager = Mockito.mock(BuildDirectoryManager.class);
        Mockito.when(buildDirectoryManager.getBuildWorkingDirectory()).thenReturn(workingDirectory);
        gitRepository.setBuildDirectoryManager(buildDirectoryManager);
        return gitRepository;
    }

    void setRepositoryProperties(GitRepository gitRepository, String repositoryUrl, String branch, String sshKey, String sshPassphrase) throws Exception
    {
        StringEncrypter encrypter = new StringEncrypter();

        BuildConfiguration buildConfiguration = new BuildConfiguration();
        buildConfiguration.setProperty("repository.git.repositoryUrl", repositoryUrl);
        buildConfiguration.setProperty("repository.git.branch", branch);
        if (sshKey != null)
        {
            buildConfiguration.setProperty("repository.git.ssh.key", encrypter.encrypt(sshKey));
        }
        if (sshPassphrase != null)
        {
            buildConfiguration.setProperty("repository.git.ssh.passphrase", encrypter.encrypt(sshPassphrase));
        }
        if (gitRepository.validate(buildConfiguration).hasAnyErrors())
        {
            throw new Exception("validation failed");
        }
        gitRepository.populateFromConfig(buildConfiguration);
    }

    @Test
    public void testBasicFunctionality() throws Exception
    {
        GitRepository gitRepository = createGitRepository();
        setRepositoryProperties(gitRepository, "git://github.com/cixot/test.git", "master", null, null);

        BuildChanges changes = gitRepository.collectChangesSinceLastBuild(PLAN_KEY, null);
        assertFalse(changes.getChanges().isEmpty());

        gitRepository.retrieveSourceCode(mockBuildContext(), changes.getVcsRevisionKey());
    }

    @DataProvider(parallel = false)
    Object[][] sourceCodeRetrievalData()
    {
        return new Object[][]{
                {"a26ff19c3c63e19d6a57a396c764b140f48c530a"},
                {"2e20b0733759facbeb0dec6ee345d762dbc8eed8"},
                {"55676cfa3db13bcf659b2a35e5d61eba478ed54d"},
        };
    }

    @Test(dataProvider = "sourceCodeRetrievalData")
    public void testSourceCodeRetrieval(String targetRevision) throws Exception
    {
        File testRepository = createTempDirectory();
        ZipResourceDirectory.copyZipResourceToDirectory("basic-repository.zip", testRepository);

        GitRepository gitRepository = createGitRepository();
        setRepositoryProperties(gitRepository, testRepository.getAbsolutePath(), "master", null, null);

        gitRepository.retrieveSourceCode(mockBuildContext(), targetRevision);
        verifyContents(gitRepository.getSourceCodeDirectory(PLAN_KEY), "basic-repo-contents-" + targetRevision + ".zip");
    }

    BuildContext mockBuildContext()
    {
        Plan plan = Mockito.mock(Plan.class);
        Mockito.when(plan.getKey()).thenReturn(PLAN_KEY);
        return new BuildContextImpl(plan, 1, null, null, null);
    }

    void verifyContents(File directory, final String expectedZip) throws IOException
    {
        final Enumeration<? extends ZipEntry> entries = new ZipFile(getClass().getResource("/" + expectedZip).getFile()).entries();
        int fileCount = 0;
        while (entries.hasMoreElements())
        {
            ZipEntry zipEntry = entries.nextElement();
            if (!zipEntry.isDirectory())
            {
                fileCount++;
                String fileName = zipEntry.getName();
                final File file = new File(directory, fileName);
                assertEquals(FileUtils.checksumCRC32(file), zipEntry.getCrc(), "CRC for " + file);
            }
        }

        final Collection files = listFiles(directory, FileFilterUtils.trueFileFilter(), FileFilterUtils.notFileFilter(FileFilterUtils.nameFileFilter(".git")));
        assertEquals(files.size(), fileCount, "Number of files");
    }

}
