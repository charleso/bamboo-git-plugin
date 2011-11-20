package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.build.BuildLoggerManager;
import com.atlassian.bamboo.build.fileserver.BuildDirectoryManager;
import com.atlassian.bamboo.repository.AbstractRepository;
import com.atlassian.bamboo.repository.AdvancedConfigurationAwareRepository;
import com.atlassian.bamboo.repository.Repository;
import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.security.StringEncrypter;
import com.atlassian.bamboo.ssh.SshProxyService;
import com.atlassian.bamboo.template.TemplateRenderer;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.BuildRepositoryChanges;
import com.atlassian.bamboo.v2.build.agent.capability.CapabilityContext;
import com.atlassian.bamboo.v2.build.agent.capability.Requirement;
import com.atlassian.bamboo.v2.build.repository.CustomSourceDirectoryAwareRepository;
import com.atlassian.bamboo.v2.build.repository.RequirementsAwareRepository;
import com.atlassian.bamboo.variable.CustomVariableContext;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;
import com.google.common.collect.Sets;
import com.opensymphony.xwork.TextProvider;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Set;

public class GitHubRepository extends AbstractRepository implements CustomSourceDirectoryAwareRepository,
                                                                    AdvancedConfigurationAwareRepository
{
    // ------------------------------------------------------------------------------------------------------- Constants

    private static final String REPOSITORY_GITHUB_USERNAME = "repository.github.username";
    private static final String REPOSITORY_GITHUB_PASSWORD = "repository.github.password";
    private static final String REPOSITORY_GITHUB_REPOSITORY = "repository.github.repository";
    private static final String REPOSITORY_GITHUB_BRANCH = "repository.github.branch";
    private static final String REPOSITORY_GITHUB_USE_SHALLOW_CLONES = "repository.github.useShallowClones";
    private static final String REPOSITORY_GITHUB_USE_SUBMODULES = "repository.github.useSubmodules";
    private static final String REPOSITORY_GITHUB_COMMAND_TIMEOUT = "repository.github.commandTimeout";
    private static final String REPOSITORY_GITHUB_VERBOSE_LOGS = "repository.github.verbose.logs";

    private static final String REPOSITORY_GITHUB_TEMPORARY_PASSWORD = "repository.github.temporary.password";
    private static final String TEMPORARY_GITHUB_PASSWORD_CHANGE = "temporary.github.password.change";

    private static final String REPOSITORY_GITHUB_ERROR_MISSING_REPOSITORY = "repository.github.error.missingRepository";

    // ------------------------------------------------------------------------------------------------- Type Properties
    private static final Logger log = Logger.getLogger(GitHubRepository.class);

    private GitRepository gitRepository = new GitRepository();

    private String username;
    private String password;
    private String repository;
    private String branch;
    private boolean useShallowClones;
    private boolean useSubmodules;
    private boolean verboseLogs;
    private int commandTimeout;


    // ---------------------------------------------------------------------------------------------------- Dependencies

    public void setBuildDirectoryManager(BuildDirectoryManager buildDirectoryManager)
    {
        super.setBuildDirectoryManager(buildDirectoryManager);
        gitRepository.setBuildDirectoryManager(buildDirectoryManager);
    }

    public void setBuildLoggerManager(BuildLoggerManager buildLoggerManager)
    {
        super.setBuildLoggerManager(buildLoggerManager);
        gitRepository.setBuildLoggerManager(buildLoggerManager);
    }

    @Override
    public void setTextProvider(TextProvider textProvider) {
        super.setTextProvider(textProvider);
        gitRepository.setTextProvider(textProvider);
    }

    @Override
    public void setCustomVariableContext(CustomVariableContext customVariableContext)
    {
        super.setCustomVariableContext(customVariableContext);
        gitRepository.setCustomVariableContext(customVariableContext);
    }

    public void setCapabilityContext(final CapabilityContext capabilityContext)
    {
        gitRepository.setCapabilityContext(capabilityContext);
    }

    public void setSshProxyService(SshProxyService sshProxyService)
    {
        gitRepository.setSshProxyService(sshProxyService);
    }

    @Override
    public void setTemplateRenderer(TemplateRenderer templateRenderer)
    {
        super.setTemplateRenderer(templateRenderer);
        gitRepository.setTemplateRenderer(templateRenderer);
    }

    // ---------------------------------------------------------------------------------------------------- Constructors
    // ----------------------------------------------------------------------------------------------- Interface Methods

    @NotNull
    public String getName()
    {
        return "GitHub";
    }

    public String getHost()
    {
        return null;
    }

    public boolean isRepositoryDifferent(@NotNull Repository repository)
    {
        if (repository instanceof GitHubRepository)
        {
            GitHubRepository ghRepo = (GitHubRepository) repository;
            return !new EqualsBuilder()
                    .append(this.repository, ghRepo.getRepository())
                    .append(this.branch, ghRepo.getBranch())
                    .isEquals();
        }
        else
        {
            return true;
        }
    }

    @Override
    public void addDefaultValues(@NotNull BuildConfiguration buildConfiguration)
    {
        buildConfiguration.setProperty(REPOSITORY_GITHUB_COMMAND_TIMEOUT, String.valueOf(GitRepository.DEFAULT_COMMAND_TIMEOUT_IN_MINUTES));
        buildConfiguration.clearTree(REPOSITORY_GITHUB_VERBOSE_LOGS);
        buildConfiguration.setProperty(REPOSITORY_GITHUB_USE_SHALLOW_CLONES, true);
        buildConfiguration.clearTree(REPOSITORY_GITHUB_USE_SUBMODULES);

    }

    public void prepareConfigObject(@NotNull BuildConfiguration buildConfiguration)
    {
        buildConfiguration.setProperty(REPOSITORY_GITHUB_USERNAME, buildConfiguration.getString(REPOSITORY_GITHUB_USERNAME, "").trim());
        if (buildConfiguration.getBoolean(TEMPORARY_GITHUB_PASSWORD_CHANGE))
        {
            buildConfiguration.setProperty(REPOSITORY_GITHUB_PASSWORD, new StringEncrypter().encrypt(buildConfiguration.getString(REPOSITORY_GITHUB_TEMPORARY_PASSWORD)));
        }
        buildConfiguration.setProperty(REPOSITORY_GITHUB_REPOSITORY, buildConfiguration.getString(REPOSITORY_GITHUB_REPOSITORY, "").trim());
        buildConfiguration.setProperty(REPOSITORY_GITHUB_BRANCH, buildConfiguration.getString(REPOSITORY_GITHUB_BRANCH, "").trim());
    }

    @Override
    public void populateFromConfig(@NotNull HierarchicalConfiguration config)
    {
        super.populateFromConfig(config);
        username = config.getString(REPOSITORY_GITHUB_USERNAME);
        password = config.getString(REPOSITORY_GITHUB_PASSWORD);
        repository = config.getString(REPOSITORY_GITHUB_REPOSITORY);
        branch = config.getString(REPOSITORY_GITHUB_BRANCH);
        useShallowClones = config.getBoolean(REPOSITORY_GITHUB_USE_SHALLOW_CLONES);
        useSubmodules = config.getBoolean(REPOSITORY_GITHUB_USE_SUBMODULES);
        commandTimeout = config.getInt(REPOSITORY_GITHUB_COMMAND_TIMEOUT, GitRepository.DEFAULT_COMMAND_TIMEOUT_IN_MINUTES);
        verboseLogs = config.getBoolean(REPOSITORY_GITHUB_VERBOSE_LOGS, false);

        gitRepository.accessData.repositoryUrl = "https://github.com/" + repository + ".git";
        gitRepository.accessData.username = username;
        gitRepository.accessData.password = password;
        gitRepository.accessData.branch = branch;
        gitRepository.accessData.sshKey = "";
        gitRepository.accessData.sshPassphrase = "";
        gitRepository.accessData.authenticationType = GitAuthenticationType.PASSWORD;
        gitRepository.accessData.useShallowClones = useShallowClones;
        gitRepository.accessData.useSubmodules = useSubmodules;
        gitRepository.accessData.commandTimeout = commandTimeout;
        gitRepository.accessData.verboseLogs = verboseLogs;
    }

    @NotNull
    @Override
    public HierarchicalConfiguration toConfiguration()
    {
        HierarchicalConfiguration configuration = super.toConfiguration();
        configuration.setProperty(REPOSITORY_GITHUB_USERNAME, username);
        configuration.setProperty(REPOSITORY_GITHUB_PASSWORD, password);
        configuration.setProperty(REPOSITORY_GITHUB_REPOSITORY, repository);
        configuration.setProperty(REPOSITORY_GITHUB_BRANCH, branch);
        configuration.setProperty(REPOSITORY_GITHUB_USE_SHALLOW_CLONES, useShallowClones);
        configuration.setProperty(REPOSITORY_GITHUB_USE_SUBMODULES, useSubmodules);
        configuration.setProperty(REPOSITORY_GITHUB_COMMAND_TIMEOUT, commandTimeout);
        configuration.setProperty(REPOSITORY_GITHUB_VERBOSE_LOGS, verboseLogs);

        return configuration;
    }

    @Override
    @NotNull
    public ErrorCollection validate(@NotNull BuildConfiguration buildConfiguration)
    {
        ErrorCollection errorCollection = super.validate(buildConfiguration);

        if (StringUtils.isBlank(buildConfiguration.getString(REPOSITORY_GITHUB_REPOSITORY)))
        {
            errorCollection.addError(REPOSITORY_GITHUB_REPOSITORY, textProvider.getText(REPOSITORY_GITHUB_ERROR_MISSING_REPOSITORY));
        }
        return errorCollection;
    }

    @NotNull
    public BuildRepositoryChanges collectChangesSinceLastBuild(@NotNull String planKey, @Nullable String lastVcsRevisionKey) throws RepositoryException
    {
        return gitRepository.collectChangesSinceLastBuild(planKey, lastVcsRevisionKey);
    }

    @NotNull
    public String retrieveSourceCode(@NotNull BuildContext buildContext, @Nullable final String vcsRevision) throws RepositoryException
    {
        return gitRepository.retrieveSourceCode(buildContext, vcsRevision, getSourceCodeDirectory(buildContext.getPlanResultKey().getPlanKey()));
    }

    @NotNull
    public String retrieveSourceCode(@NotNull final BuildContext buildContext, @Nullable final String vcsRevision, @NotNull final File sourceDirectory) throws RepositoryException
    {
        return gitRepository.retrieveSourceCode(buildContext, vcsRevision, sourceDirectory);
    }

    // -------------------------------------------------------------------------------------------------- Public Methods
    // -------------------------------------------------------------------------------------------------- Helper Methods
    // -------------------------------------------------------------------------------------- Basic Accessors / Mutators

    public String getUsername()
    {
        return username;
    }

    public String getRepository()
    {
        return repository;
    }

    public String getBranch()
    {
        return branch;
    }

    public boolean isUseShallowClones()
    {
        return useShallowClones;
    }

    String getPassword()
    {
        return password;
    }

    public boolean isUseSubmodules()
    {
        return useSubmodules;
    }

    public int getCommandTimeout()
    {
        return commandTimeout;
    }

    public boolean getVerboseLogs()
    {
        return verboseLogs;
    }
}
