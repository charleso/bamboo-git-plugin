package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.build.BuildLoggerManager;
import com.atlassian.bamboo.build.fileserver.BuildDirectoryManager;
import com.atlassian.bamboo.build.logger.NullBuildLogger;
import com.atlassian.bamboo.security.StringEncrypter;
import com.atlassian.bamboo.util.BambooFileUtils;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.answers.Returns;
import org.testng.annotations.AfterClass;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.apache.commons.io.FileUtils.listFiles;
import static org.testng.Assert.assertEquals;

public class GitAbstractTest {
    protected final Collection<File> filesToCleanUp = Collections.synchronizedCollection(new ArrayList<File>());

    static void setRepositoryProperties(GitRepository gitRepository, String repositoryUrl, String branch, String sshKey, String sshPassphrase) throws Exception
    {
        setRepositoryProperties(gitRepository, repositoryUrl, branch, sshKey, sshPassphrase, Collections.<String, String>emptyMap());
    }

    static void setRepositoryProperties(GitRepository gitRepository, String repositoryUrl, String branch, String sshKey, String sshPassphrase, Map<String, String> paramMap) throws Exception
    {
        StringEncrypter encrypter = new StringEncrypter();

        Map<String, String> params = new HashMap<String, String>(paramMap);

        params.put("repository.git.branch", branch);
        if (sshKey != null)
        {
            params.put("repository.git.ssh.key", encrypter.encrypt(sshKey));
        }
        if (sshPassphrase != null)
        {
            params.put("repository.git.ssh.passphrase", encrypter.encrypt(sshPassphrase));
        }
        setRepositoryProperties(gitRepository, repositoryUrl, params);
    }

    static void setRepositoryProperties(GitRepository gitRepository, String repositoryUrl, Map<String, String> paramMap) throws Exception
    {
        BuildConfiguration buildConfiguration = new BuildConfiguration();
        buildConfiguration.setProperty("repository.git.repositoryUrl", repositoryUrl);

        for (Map.Entry<String, String> entry : paramMap.entrySet())
        {
            buildConfiguration.setProperty(entry.getKey(), entry.getValue());
        }

        if (gitRepository.validate(buildConfiguration).hasAnyErrors())
        {
            throw new Exception("validation failed");
        }
        gitRepository.populateFromConfig(buildConfiguration);
    }

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

    File createTempDirectory() throws IOException
    {
        File tmp = BambooFileUtils.createTempDirectory("bamboo-git-plugin-test");
        FileUtils.forceDeleteOnExit(tmp);
        filesToCleanUp.add(tmp);
        return tmp;
    }

    static void verifyContents(File directory, final String expectedZip) throws IOException
    {
        final Enumeration<? extends ZipEntry> entries = new ZipFile(GitAbstractTest.class.getResource("/" + expectedZip).getFile()).entries();
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


    @AfterClass
    void cleanUpFiles()
    {
        for (File file : filesToCleanUp) {
            FileUtils.deleteQuietly(file);
        }
    }

}
