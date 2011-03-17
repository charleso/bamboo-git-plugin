package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.build.BuildLoggerManager;
import com.atlassian.bamboo.build.fileserver.BuildDirectoryManager;
import com.atlassian.bamboo.build.logger.NullBuildLogger;
import com.atlassian.bamboo.plan.Plan;
import com.atlassian.bamboo.project.Project;
import com.atlassian.bamboo.security.StringEncrypter;
import com.atlassian.bamboo.util.BambooFileUtils;
import com.atlassian.bamboo.utils.i18n.DefaultI18nBean;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.BuildContextImpl;
import com.atlassian.bamboo.variable.CustomVariableContextImpl;
import com.atlassian.bamboo.variable.CustomVariableContextThreadLocal;
import com.atlassian.bamboo.ww2.TextProviderAdapter;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;
import com.atlassian.plugin.PluginAccessor;
import com.opensymphony.xwork.TextProvider;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.eclipse.jgit.lib.Repository;
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
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.atlassian.bamboo.plugins.git.GitRepository.GitRepositoryAccessData;
import org.testng.annotations.BeforeClass;

import static org.apache.commons.io.FileUtils.listFiles;
import static org.testng.Assert.assertEquals;

public class GitAbstractTest
{
    public static final String PLAN_KEY = "PLAN-KEY";
    private final Collection<File> filesToCleanUp = Collections.synchronizedCollection(new ArrayList<File>());
    private final Collection<Repository> repositoriesToCleanUp = Collections.synchronizedCollection(new ArrayList<Repository>());
    private static final ResourceBundle resourceBundle = ResourceBundle.getBundle("com.atlassian.bamboo.plugins.git.i18n", Locale.US);

    public static void setRepositoryProperties(GitRepository gitRepository, File repositorySourceDir, String branch) throws Exception
    {
        setRepositoryProperties(gitRepository, repositorySourceDir.getAbsolutePath(), branch);
    }

    public static void setRepositoryProperties(GitRepository gitRepository, String repositoryUrl, String branch) throws Exception
    {
        setRepositoryProperties(gitRepository, repositoryUrl, branch, null, null);
    }

    public static void setRepositoryProperties(GitRepository gitRepository, String repositoryUrl, String branch, String sshKey, String sshPassphrase) throws Exception
    {
        setRepositoryProperties(gitRepository, repositoryUrl, branch, sshKey, sshPassphrase, Collections.<String, String>emptyMap());
    }

    public static void setRepositoryProperties(GitRepository gitRepository, String repositoryUrl, String branch, String sshKey, String sshPassphrase, Map<String, String> paramMap) throws Exception
    {
        StringEncrypter encrypter = new StringEncrypter();

        Map<String, Object> params = new HashMap<String, Object>(paramMap);

        params.put("repository.git.branch", branch);
        if (sshKey != null)
        {
            params.put("repository.git.ssh.key", encrypter.encrypt(sshKey));
            params.put("repository.git.authenticationType", GitAuthenticationType.SSH_KEYPAIR.name());
        }
        if (sshPassphrase != null)
        {
            params.put("repository.git.ssh.passphrase", encrypter.encrypt(sshPassphrase));
        }
        setRepositoryProperties(gitRepository, repositoryUrl, params);
    }

    public static void setRepositoryProperties(GitRepository gitRepository, File repositorySourceDir) throws Exception
    {
        setRepositoryProperties(gitRepository, repositorySourceDir.getAbsolutePath(), Collections.<String, Object>emptyMap());
    }

    public static void setRepositoryProperties(GitRepository gitRepository, String repositoryUrl) throws Exception
    {
        setRepositoryProperties(gitRepository, repositoryUrl, Collections.<String, Object>emptyMap());
    }

    public static void setRepositoryProperties(GitRepository gitRepository, String repositoryUrl, Map<String, ?> paramMap) throws Exception
    {
        BuildConfiguration buildConfiguration = new BuildConfiguration();
        buildConfiguration.setProperty("repository.git.repositoryUrl", repositoryUrl);

        for (Map.Entry<String, ?> entry : paramMap.entrySet())
        {
            buildConfiguration.setProperty(entry.getKey(), entry.getValue());
        }

        if (gitRepository.validate(buildConfiguration).hasAnyErrors())
        {
            throw new Exception("validation failed");
        }
        gitRepository.populateFromConfig(buildConfiguration);
    }

    public GitOperationHelper createGitOperationHelper()
    {
        TextProvider textProvider = Mockito.mock(TextProvider.class);
        return new GitOperationHelper(new NullBuildLogger(), textProvider);
    }

    public GitRepository createGitRepository() throws Exception
    {
        setupCustomVariableContext();
        File workingDirectory = createTempDirectory();

        final GitRepository gitRepository = new GitRepositoryFixture();

        BuildLoggerManager buildLoggerManager = Mockito.mock(BuildLoggerManager.class, new Returns(new NullBuildLogger()));
        gitRepository.setBuildLoggerManager(buildLoggerManager);

        BuildDirectoryManager buildDirectoryManager = Mockito.mock(BuildDirectoryManager.class, new Returns(workingDirectory));
        gitRepository.setBuildDirectoryManager(buildDirectoryManager);
        gitRepository.setTextProvider(getTextProvider());

        return gitRepository;
    }

    public File createTempDirectory() throws IOException
    {
        File tmp = BambooFileUtils.createTempDirectory("bamboo-git-plugin-test");
        FileUtils.forceDeleteOnExit(tmp);
        filesToCleanUp.add(tmp);
        return tmp;
    }

    protected <T extends Repository> T register(T repository)
    {
        repositoriesToCleanUp.add(repository);
        return repository;
    }

    public static void verifyContents(File directory, final String expectedZip) throws IOException
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

    public static TextProvider getTextProvider()
    {
        DefaultI18nBean bean = new DefaultI18nBean(Locale.US, Mockito.mock(PluginAccessor.class));
        bean.getI18nBundles().add(resourceBundle);
        return new TextProviderAdapter(bean);
    }

    protected static GitRepositoryAccessData createAccessData(String repositoryUrl)
    {
        return createAccessData(repositoryUrl, null);
    }

    protected static GitRepositoryAccessData createAccessData(File repositoryFile, String branch)
    {
        return createAccessData(repositoryFile.getAbsolutePath(), branch);
    }

    protected static GitRepositoryAccessData createAccessData(String repositoryUrl, String branch)
    {
        return createAccessData(repositoryUrl, branch, null, null, null, null);
    }

    protected static GitRepositoryAccessData createAccessData(String repositoryUrl, String branch, String username, String password, String sshKey, String sshPassphrase)
    {
        GitRepositoryAccessData accessData = new GitRepositoryAccessData();
        accessData.repositoryUrl = repositoryUrl;
        accessData.branch = branch;
        accessData.username = username;
        accessData.password = password;
        accessData.sshKey = sshKey;
        accessData.sshPassphrase = sshPassphrase;
        return accessData;
    }

    protected static BuildContext mockBuildContext()
    {
        Plan plan = Mockito.mock(Plan.class);
        Mockito.when(plan.getKey()).thenReturn(PLAN_KEY);
        Project project = Mockito.mock(Project.class);
        Mockito.when(plan.getProject()).thenReturn(project);
        return new BuildContextImpl(plan, 1, null, null, null);
    }

    @BeforeClass
    void setupCustomVariableContext()
    {
         CustomVariableContextThreadLocal.set(new CustomVariableContextImpl());
    }

    @AfterClass
    void cleanUpFiles()
    {
        for (File file : filesToCleanUp)
        {
            FileUtils.deleteQuietly(file);
        }
        for (Repository repository : repositoriesToCleanUp)
        {
            repository.close();
        }
    }

    static class GitRepositoryFixture extends GitRepository
    {
        @Override
        public File getCacheDirectory()
        {
            return GitCacheDirectory.getCacheDirectory(getWorkingDirectory(), accessData);
        }
    }
}
