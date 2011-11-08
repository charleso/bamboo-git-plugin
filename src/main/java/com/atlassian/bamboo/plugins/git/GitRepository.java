package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.author.Author;
import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.commit.CommitContext;
import com.atlassian.bamboo.commit.CommitContextImpl;
import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.repository.AbstractRepository;
import com.atlassian.bamboo.repository.AdvancedConfigurationAwareRepository;
import com.atlassian.bamboo.repository.CustomVariableProviderRepository;
import com.atlassian.bamboo.repository.MavenPomAccessor;
import com.atlassian.bamboo.repository.MavenPomAccessorCapableRepository;
import com.atlassian.bamboo.repository.NameValuePair;
import com.atlassian.bamboo.repository.Repository;
import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.repository.SelectableAuthenticationRepository;
import com.atlassian.bamboo.security.StringEncrypter;
import com.atlassian.bamboo.ssh.SshProxyService;
import com.atlassian.bamboo.utils.SystemProperty;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.BuildRepositoryChanges;
import com.atlassian.bamboo.v2.build.BuildRepositoryChangesImpl;
import com.atlassian.bamboo.v2.build.agent.capability.CapabilityContext;
import com.atlassian.bamboo.v2.build.agent.capability.Requirement;
import com.atlassian.bamboo.v2.build.agent.remote.RemoteBuildDirectoryManager;
import com.atlassian.bamboo.v2.build.repository.CustomSourceDirectoryAwareRepository;
import com.atlassian.bamboo.v2.build.repository.RequirementsAwareRepository;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;
import com.atlassian.util.concurrent.LazyReference;
import com.atlassian.util.concurrent.Supplier;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.opensymphony.xwork.TextProvider;
import com.opensymphony.xwork.util.LocalizedTextUtil;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.log4j.Logger;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

public class GitRepository extends AbstractRepository implements MavenPomAccessorCapableRepository,
                                                                 SelectableAuthenticationRepository,
                                                                 CustomVariableProviderRepository,
                                                                 CustomSourceDirectoryAwareRepository,
                                                                 RequirementsAwareRepository,
                                                                 AdvancedConfigurationAwareRepository
{
    // ------------------------------------------------------------------------------------------------------- Constants

    private static final String REPOSITORY_GIT_NAME = "repository.git.name";
    private static final String REPOSITORY_GIT_REPOSITORY_URL = "repository.git.repositoryUrl";
    private static final String REPOSITORY_GIT_AUTHENTICATION_TYPE = "repository.git.authenticationType";
    private static final String REPOSITORY_GIT_USERNAME = "repository.git.username";
    private static final String REPOSITORY_GIT_PASSWORD = "repository.git.password";
    private static final String REPOSITORY_GIT_BRANCH = "repository.git.branch";
    private static final String REPOSITORY_GIT_SSH_KEY = "repository.git.ssh.key";
    private static final String REPOSITORY_GIT_SSH_PASSPHRASE = "repository.git.ssh.passphrase";
    private static final String REPOSITORY_GIT_USE_SHALLOW_CLONES = "repository.git.useShallowClones";
    private static final String REPOSITORY_GIT_MAVEN_PATH = "repository.git.maven.path";
    private static final String REPOSITORY_GIT_COMMAND_TIMEOUT = "repository.git.commandTimeout";
    private static final String REPOSITORY_GIT_VERBOSE_LOGS = "repository.git.verbose.logs";
    private static final String TEMPORARY_GIT_PASSWORD = "temporary.git.password";
    private static final String TEMPORARY_GIT_PASSWORD_CHANGE = "temporary.git.password.change";
    private static final String TEMPORARY_GIT_SSH_PASSPHRASE = "temporary.git.ssh.passphrase";
    private static final String TEMPORARY_GIT_SSH_PASSPHRASE_CHANGE = "temporary.git.ssh.passphrase.change";
    private static final String TEMPORARY_GIT_SSH_KEY_FROM_FILE = "temporary.git.ssh.keyfile";
    private static final String TEMPORARY_GIT_SSH_KEY_CHANGE = "temporary.git.ssh.key.change";

    private static final GitAuthenticationType defaultAuthenticationType = GitAuthenticationType.NONE;
    private static boolean USE_SHALLOW_CLONES = new SystemProperty(false, "atlassian.bamboo.git.useShallowClones", "ATLASSIAN_BAMBOO_GIT_USE_SHALLOW_CLONES").getValue(true);

    final static int DEFAULT_COMMAND_TIMEOUT_IN_MINUTES = 180;

    // ------------------------------------------------------------------------------------------------- Type Properties

    private static final Logger log = Logger.getLogger(GitRepository.class);

    static class GitRepositoryAccessData implements Serializable
    {
        String repositoryUrl;
        String branch;
        String username;
        String password;
        String sshKey;
        String sshPassphrase;
        GitAuthenticationType authenticationType;
        boolean useShallowClones;
        int commandTimeout;
        boolean verboseLogs;
    }

    final GitRepositoryAccessData accessData = new GitRepositoryAccessData();

    // Maven 2 import
    private transient String pathToPom;

    //todo: Spring-inject StringEncrypter singleton, https://atlaseye.atlassian.com/cru/CR-BAM-2232#c37222
    private final transient LazyReference<StringEncrypter> encrypterRef = new LazyReference<StringEncrypter>()
    {
        @Override
        protected StringEncrypter create() throws Exception
        {
            return new StringEncrypter();
        }
    };

    // ---------------------------------------------------------------------------------------------------- Dependencies
    private transient CapabilityContext capabilityContext;
    private transient SshProxyService sshProxyService;
    // ---------------------------------------------------------------------------------------------------- Constructors

    // ----------------------------------------------------------------------------------------------- Interface Methods

    @NotNull
    public String getName()
    {
        return textProvider.getText(REPOSITORY_GIT_NAME);
    }

    public String getHost()
    {
        return null;
    }

    public boolean isRepositoryDifferent(@NotNull Repository repository)
    {
        if (repository instanceof GitRepository)
        {
            GitRepository gitRepo = (GitRepository) repository;
            return !new EqualsBuilder()
                    .append(accessData.repositoryUrl, gitRepo.accessData.repositoryUrl)
                    .append(accessData.branch, gitRepo.accessData.branch)
                    .append(accessData.username, gitRepo.accessData.username)
                    .append(accessData.sshKey, gitRepo.accessData.sshKey)
                    .isEquals();
        }
        else
        {
            return true;
        }
    }

    @NotNull
    public BuildRepositoryChanges collectChangesSinceLastBuild(@NotNull String planKey, @Nullable final String lastVcsRevisionKey) throws RepositoryException
    {
        try
        {
            final GitRepositoryAccessData substitutedAccessData = getSubstitutedAccessData();
            final BuildLogger buildLogger = buildLoggerManager.getBuildLogger(PlanKeys.getPlanKey(planKey));
            final GitOperationHelper helper = GitOperationHelperFactory.createGitOperationHelper(this, substitutedAccessData, sshProxyService, buildLogger, textProvider);

            final String targetRevision = helper.obtainLatestRevision(substitutedAccessData);

            if (targetRevision.equals(lastVcsRevisionKey))
            {
                return new BuildRepositoryChangesImpl(targetRevision);
            }

            final File cacheDirectory = getCacheDirectory();
            if (lastVcsRevisionKey == null)
            {
                buildLogger.addBuildLogEntry(textProvider.getText("repository.git.messages.ccRepositoryNeverChecked", Arrays.asList(targetRevision)));
                try
                {
                    GitCacheDirectory.getCacheLock(cacheDirectory).withLock(new Callable<Void>()
                    {
                        public Void call() throws RepositoryException
                        {
                            boolean doShallowFetch = USE_SHALLOW_CLONES && substitutedAccessData.useShallowClones && !cacheDirectory.isDirectory();
                            helper.fetch(cacheDirectory, substitutedAccessData, doShallowFetch);
                            return null;
                        }
                    });
                }
                catch (Exception e)
                {
                    throw new RepositoryException(e.getMessage(), e);
                }
                return new BuildRepositoryChangesImpl(targetRevision);
            }

            final BuildRepositoryChanges buildChanges = GitCacheDirectory.getCacheLock(cacheDirectory).withLock(new Supplier<BuildRepositoryChanges>()
            {
                public BuildRepositoryChanges get()
                {
                    try
                    {
                        helper.fetch(cacheDirectory, substitutedAccessData, false);
                        return helper.extractCommits(cacheDirectory, lastVcsRevisionKey, targetRevision);
                    }
                    catch (Exception e) // not just RepositoryException - see HandlingSwitchingRepositoriesToUnrelatedOnesTest.testCollectChangesWithUnrelatedPreviousRevision
                    {
                        try
                        {
                            rethrowOrRemoveDirectory(e, buildLogger, cacheDirectory, "repository.git.messages.ccRecover.failedToCollectChangesets");
                            buildLogger.addBuildLogEntry(textProvider.getText("repository.git.messages.ccRecover.cleanedCacheDirectory", Arrays.asList(cacheDirectory)));
                            helper.fetch(cacheDirectory, substitutedAccessData, false);
                            buildLogger.addBuildLogEntry(textProvider.getText("repository.git.messages.ccRecover.fetchedRemoteRepository", Arrays.asList(cacheDirectory)));
                            BuildRepositoryChanges extractedChanges = helper.extractCommits(cacheDirectory, lastVcsRevisionKey, targetRevision);
                            buildLogger.addBuildLogEntry(textProvider.getText("repository.git.messages.ccRecover.completed"));
                            return extractedChanges;
                        }
                        catch (Exception e2)
                        {
                            log.error(buildLogger.addBuildLogEntry(textProvider.getText("repository.git.messages.ccRecover.failedToExtractChangesets")), e2);
                            return null;
                        }
                    }
                }
            });

            if (buildChanges != null && !buildChanges.getChanges().isEmpty())
            {
                return buildChanges;
            }
            else
            {
                return new BuildRepositoryChangesImpl(targetRevision, Collections.singletonList((CommitContext) CommitContextImpl.builder()
                        .author(Author.UNKNOWN_AUTHOR)
                        .comment(textProvider.getText("repository.git.messages.unknownChanges", Arrays.asList(lastVcsRevisionKey, targetRevision)))
                        .date(new Date())
                        .build()));
            }
        }
        catch (RuntimeException e)
        {
            throw new RepositoryException(textProvider.getText("repository.git.messages.runtimeException"), e);
        }
    }

    @Deprecated
    @NotNull
    public String retrieveSourceCode(@NotNull final BuildContext buildContext, @Nullable final String vcsRevisionKey) throws RepositoryException {
        throw new NotImplementedException("Not implemented - use instead retrieveSourceCode(bctx, rev, src)");
    }

    @NotNull
    public String retrieveSourceCode(@NotNull final BuildContext buildContext, @Nullable final String nullableTargetRevision, @NotNull final File sourceDirectory) throws RepositoryException
    {
        try
        {
            final GitRepositoryAccessData substitutedAccessData = getSubstitutedAccessData();
            final BuildLogger buildLogger = buildLoggerManager.getBuildLogger(buildContext.getPlanResultKey());
            final boolean doShallowFetch = USE_SHALLOW_CLONES && substitutedAccessData.useShallowClones;
            final boolean isOnLocalAgent = !(buildDirectoryManager instanceof RemoteBuildDirectoryManager);
            final GitOperationHelper helper = GitOperationHelperFactory.createGitOperationHelper(this, substitutedAccessData, sshProxyService, buildLogger, textProvider);
            final String targetRevision = nullableTargetRevision != null ? nullableTargetRevision : helper.obtainLatestRevision(substitutedAccessData);
            final String previousRevision = helper.getCurrentRevision(sourceDirectory);

            if (isOnLocalAgent)
            {
                final File cacheDirectory = getCacheDirectory();
                return GitCacheDirectory.getCacheLock(cacheDirectory).withLock(new Callable<String>()
                {
                    public String call() throws Exception
                    {
                        try
                        {
                            helper.fetch(cacheDirectory, substitutedAccessData, doShallowFetch);
                        }
                        catch (Exception e)
                        {
                            rethrowOrRemoveDirectory(e, buildLogger, cacheDirectory, "repository.git.messages.rsRecover.failedToFetchCache");
                            buildLogger.addBuildLogEntry(textProvider.getText("repository.git.messages.rsRecover.cleanedCacheDirectory", Arrays.asList(cacheDirectory)));
                            helper.fetch(cacheDirectory, substitutedAccessData, doShallowFetch);
                            buildLogger.addBuildLogEntry(textProvider.getText("repository.git.messages.rsRecover.fetchingCacheCompleted", Arrays.asList(cacheDirectory)));
                        }

                        try
                        {
                            return helper.checkout(cacheDirectory, sourceDirectory, targetRevision, previousRevision);
                        }
                        catch (Exception e)
                        {
                            rethrowOrRemoveDirectory(e, buildLogger, sourceDirectory, "repository.git.messages.rsRecover.failedToCheckout");
                            buildLogger.addBuildLogEntry(textProvider.getText("repository.git.messages.rsRecover.cleanedSourceDirectory", Arrays.asList(sourceDirectory)));
                            String returnRevision = helper.checkout(cacheDirectory, sourceDirectory, targetRevision, null);
                            buildLogger.addBuildLogEntry(textProvider.getText("repository.git.messages.rsRecover.checkoutCompleted"));
                            return returnRevision;
                        }
                    }
                });

            }
            else //isOnRemoteAgent
            {
                try
                {
                    helper.fetch(sourceDirectory, substitutedAccessData, doShallowFetch);
                    return helper.checkout(null, sourceDirectory, targetRevision, previousRevision);
                }
                catch (Exception e)
                {
                    rethrowOrRemoveDirectory(e, buildLogger, sourceDirectory, "repository.git.messages.rsRecover.failedToCheckout");
                    buildLogger.addBuildLogEntry(textProvider.getText("repository.git.messages.rsRecover.cleanedSourceDirectory", Arrays.asList(sourceDirectory)));
                    helper.fetch(sourceDirectory, substitutedAccessData, false);
                    buildLogger.addBuildLogEntry(textProvider.getText("repository.git.messages.rsRecover.fetchingCompleted", Arrays.asList(sourceDirectory)));
                    String returnRevision = helper.checkout(null, sourceDirectory, targetRevision, null);
                    buildLogger.addBuildLogEntry(textProvider.getText("repository.git.messages.rsRecover.checkoutCompleted"));
                    return returnRevision;
                }
            }
        }
        catch (RepositoryException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new RepositoryException(textProvider.getText("repository.git.messages.runtimeException"), e);
        }
    }

    private void rethrowOrRemoveDirectory(final Exception originalException, final BuildLogger buildLogger, final File directory, final String key) throws Exception
    {
        Throwable e = originalException;
        do
        {
            if (e instanceof TransportException)
            {
                throw originalException;
            }
            e = e.getCause();
        } while (e!=null);

        buildLogger.addBuildLogEntry(textProvider.getText(key, Arrays.asList(directory)));
        log.warn("Deleting directory " + directory, e);

        // This section does not really work on Windows (files open by antivirus software or leaked by jgit - and it does leak handles - will remain on the harddrive),
        // so it should be entered if we know that the cache has to be blown away
        FileUtils.deleteQuietly(directory);

        final String[] filesInDirectory = directory.list();
        if (filesInDirectory !=null)
        {
            log.error("Unable to delete files: " + Arrays.toString(filesInDirectory) + ", expect trouble");
        }
    }

    @Override
    public void addDefaultValues(@NotNull BuildConfiguration buildConfiguration)
    {
        buildConfiguration.setProperty(REPOSITORY_GIT_COMMAND_TIMEOUT, String.valueOf(DEFAULT_COMMAND_TIMEOUT_IN_MINUTES));
        buildConfiguration.clearTree(REPOSITORY_GIT_VERBOSE_LOGS);
        buildConfiguration.setProperty(REPOSITORY_GIT_USE_SHALLOW_CLONES, true);
    }

    public void prepareConfigObject(@NotNull BuildConfiguration buildConfiguration)
    {
        buildConfiguration.setProperty(REPOSITORY_GIT_COMMAND_TIMEOUT, buildConfiguration.getString(REPOSITORY_GIT_COMMAND_TIMEOUT, "").trim());
        if (buildConfiguration.getBoolean(TEMPORARY_GIT_PASSWORD_CHANGE))
        {
            buildConfiguration.setProperty(REPOSITORY_GIT_PASSWORD, encrypterRef.get().encrypt(buildConfiguration.getString(TEMPORARY_GIT_PASSWORD)));
        }
        if (buildConfiguration.getBoolean(TEMPORARY_GIT_SSH_PASSPHRASE_CHANGE))
        {
            buildConfiguration.setProperty(REPOSITORY_GIT_SSH_PASSPHRASE, encrypterRef.get().encrypt(buildConfiguration.getString(TEMPORARY_GIT_SSH_PASSPHRASE)));
        }
        if (buildConfiguration.getBoolean(TEMPORARY_GIT_SSH_KEY_CHANGE))
        {
            final Object o = buildConfiguration.getProperty(TEMPORARY_GIT_SSH_KEY_FROM_FILE);
            if (o instanceof File)
            {
                final String key;
                try
                {
                    key = FileUtils.readFileToString((File) o);
                }
                catch (IOException e)
                {
                    log.error("Cannot read uploaded ssh key file", e);
                    return;
                }
                buildConfiguration.setProperty(REPOSITORY_GIT_SSH_KEY, encrypterRef.get().encrypt(key));
            }
            else
            {
                buildConfiguration.clearProperty(REPOSITORY_GIT_SSH_KEY);
            }
        }
    }

    @Override
    public void populateFromConfig(@NotNull HierarchicalConfiguration config)
    {
        super.populateFromConfig(config);
        accessData.repositoryUrl = StringUtils.trimToEmpty(config.getString(REPOSITORY_GIT_REPOSITORY_URL));
        accessData.username = config.getString(REPOSITORY_GIT_USERNAME, "");
        accessData.password = config.getString(REPOSITORY_GIT_PASSWORD);
        accessData.branch = config.getString(REPOSITORY_GIT_BRANCH, "");
        accessData.sshKey = config.getString(REPOSITORY_GIT_SSH_KEY, "");
        accessData.sshPassphrase = config.getString(REPOSITORY_GIT_SSH_PASSPHRASE);
        accessData.authenticationType = safeParseAuthenticationType(config.getString(REPOSITORY_GIT_AUTHENTICATION_TYPE));
        accessData.useShallowClones = config.getBoolean(REPOSITORY_GIT_USE_SHALLOW_CLONES);
        accessData.commandTimeout = config.getInt(REPOSITORY_GIT_COMMAND_TIMEOUT, DEFAULT_COMMAND_TIMEOUT_IN_MINUTES);
        accessData.verboseLogs = config.getBoolean(REPOSITORY_GIT_VERBOSE_LOGS, false);

        pathToPom = config.getString(REPOSITORY_GIT_MAVEN_PATH);
    }

    @NotNull
    @Override
    public HierarchicalConfiguration toConfiguration()
    {
        HierarchicalConfiguration configuration = super.toConfiguration();
        configuration.setProperty(REPOSITORY_GIT_REPOSITORY_URL, accessData.repositoryUrl);
        configuration.setProperty(REPOSITORY_GIT_USERNAME, accessData.username);
        configuration.setProperty(REPOSITORY_GIT_PASSWORD, accessData.password);
        configuration.setProperty(REPOSITORY_GIT_BRANCH, accessData.branch);
        configuration.setProperty(REPOSITORY_GIT_SSH_KEY, accessData.sshKey);
        configuration.setProperty(REPOSITORY_GIT_SSH_PASSPHRASE, accessData.sshPassphrase);
        configuration.setProperty(REPOSITORY_GIT_AUTHENTICATION_TYPE, accessData.authenticationType != null ? accessData.authenticationType.name() : null);
        configuration.setProperty(REPOSITORY_GIT_USE_SHALLOW_CLONES, accessData.useShallowClones);
        configuration.setProperty(REPOSITORY_GIT_COMMAND_TIMEOUT, accessData.commandTimeout);
        configuration.setProperty(REPOSITORY_GIT_VERBOSE_LOGS, accessData.verboseLogs);
        return configuration;
    }

    @Override
    @NotNull
    public ErrorCollection validate(@NotNull BuildConfiguration buildConfiguration)
    {
        ErrorCollection errorCollection = super.validate(buildConfiguration);

        final String repositoryUrl = StringUtils.trim(buildConfiguration.getString(REPOSITORY_GIT_REPOSITORY_URL));
        final GitAuthenticationType authenticationType = safeParseAuthenticationType(buildConfiguration.getString(REPOSITORY_GIT_AUTHENTICATION_TYPE));

        if (StringUtils.isBlank(repositoryUrl))
        {
            errorCollection.addError(REPOSITORY_GIT_REPOSITORY_URL, textProvider.getText("repository.git.messages.missingRepositoryUrl"));
        }
        else
        {
            final boolean hasUsername = StringUtils.isNotBlank(buildConfiguration.getString(REPOSITORY_GIT_USERNAME));
            final boolean hasPassword = StringUtils.isNotBlank(buildConfiguration.getString(REPOSITORY_GIT_PASSWORD));
            try
            {
                final URIish uri = new URIish(repositoryUrl);
                if (authenticationType == GitAuthenticationType.SSH_KEYPAIR && ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())))
                {
                    errorCollection.addError(REPOSITORY_GIT_AUTHENTICATION_TYPE, textProvider.getText("repository.git.messages.unsupportedHttpAuthenticationType"));
                }
                else if (authenticationType == GitAuthenticationType.PASSWORD)
                {
                    boolean duplicateUsername = hasUsername && StringUtils.isNotBlank(uri.getUser());
                    boolean duplicatePassword = hasPassword && StringUtils.isNotBlank(uri.getPass());
                    if (duplicateUsername || duplicatePassword)
                    {
                        errorCollection.addError(REPOSITORY_GIT_REPOSITORY_URL,
                                (duplicateUsername ? textProvider.getText("repository.git.messages.duplicateUsernameField") : "")
                                        + ((duplicateUsername && duplicatePassword) ? " " : "")
                                        + (duplicatePassword ? textProvider.getText("repository.git.messages.duplicatePasswordField") : ""));
                    }
                    if (duplicateUsername)
                    {
                        errorCollection.addError(REPOSITORY_GIT_USERNAME, textProvider.getText("repository.git.messages.duplicateUsernameField"));
                    }
                    if (duplicatePassword)
                    {
                        errorCollection.addError(TEMPORARY_GIT_PASSWORD_CHANGE, textProvider.getText("repository.git.messages.duplicatePasswordField"));
                    }
                    if (uri.getHost() == null && hasUsername)
                    {
                        errorCollection.addError(REPOSITORY_GIT_USERNAME, textProvider.getText("repository.git.messages.unsupportedUsernameField"));
                    }
                }
            }
            catch (URISyntaxException e)
            {
                if (hasUsername)
                {
                    errorCollection.addError(REPOSITORY_GIT_USERNAME, textProvider.getText("repository.git.messages.unsupportedUsernameField"));
                }
            }
        }

        if (buildConfiguration.getString(REPOSITORY_GIT_MAVEN_PATH, "").contains(".."))
        {
            errorCollection.addError(REPOSITORY_GIT_MAVEN_PATH, textProvider.getText("repository.git.messages.invalidPomPath"));
        }

        return errorCollection;
    }

    @NotNull
    public Map<String, String> getCustomVariables()
    {
        Map<String, String> variables = Maps.newHashMap();
        variables.put(REPOSITORY_GIT_REPOSITORY_URL, accessData.repositoryUrl);
        variables.put(REPOSITORY_GIT_BRANCH, accessData.branch);
        variables.put(REPOSITORY_GIT_USERNAME, accessData.username);
        return variables;
    }

    @NotNull
    public MavenPomAccessor getMavenPomAccessor()
    {
        return new GitMavenPomAccessor(this, sshProxyService, textProvider, getGitCapability()).withPath(pathToPom);
    }

    @NotNull
    public List<NameValuePair> getAuthenticationTypes()
    {
        return Lists.transform(Arrays.asList(GitAuthenticationType.values()), new Function<GitAuthenticationType, NameValuePair>()
        {
            public NameValuePair apply(GitAuthenticationType from)
            {
                final String typeName = from.name();
                return new NameValuePair(typeName, getAuthTypeName(typeName));
            }
        });
    }

    public String getAuthType()
    {
        return accessData.authenticationType != null ? accessData.authenticationType.name() : defaultAuthenticationType.name();
    }

    // -------------------------------------------------------------------------------------------------- Public Methods

    // -------------------------------------------------------------------------------------------------- Helper Methods

    GitAuthenticationType safeParseAuthenticationType(String typeName)
    {
        if (typeName == null)
        {
            return defaultAuthenticationType;
        }
        try
        {
            return GitAuthenticationType.valueOf(typeName);
        }
        catch (IllegalArgumentException e)
        {
            return defaultAuthenticationType;
        }
    }

    String getAuthTypeName(String authType)
    {
        return textProvider.getText("repository.git.authenticationType." + StringUtils.lowerCase(authType));
    }

    GitRepositoryAccessData getSubstitutedAccessData()
    {
        GitRepositoryAccessData substituted = new GitRepositoryAccessData();
        substituted.repositoryUrl = substituteString(accessData.repositoryUrl);
        substituted.branch = substituteString(accessData.branch);
        substituted.username = substituteString(accessData.username);
        substituted.password = encrypterRef.get().decrypt(accessData.password);
        substituted.sshKey = encrypterRef.get().decrypt(accessData.sshKey);
        substituted.sshPassphrase = encrypterRef.get().decrypt(accessData.sshPassphrase);
        substituted.authenticationType = accessData.authenticationType;
        substituted.useShallowClones = accessData.useShallowClones;
        substituted.commandTimeout = accessData.commandTimeout;
        substituted.verboseLogs = accessData.verboseLogs;
        return substituted;
    }

    // -------------------------------------------------------------------------------------- Basic Accessors / Mutators

    public boolean isUseShallowClones()
    {
        return accessData.useShallowClones;
    }

    public String getRepositoryUrl()
    {
        return accessData.repositoryUrl;
    }

    public String getBranch()
    {
        return accessData.branch;
    }

    public int getCommandTimeout()
    {
        return accessData.commandTimeout;
    }

    public boolean getVerboseLogs()
    {
        return accessData.verboseLogs;
    }

    public String getAuthTypeName()
    {
        return getAuthTypeName(getAuthType());
    }

    public File getCacheDirectory()
    {
        return GitCacheDirectory.getCacheDirectory(buildDirectoryManager.getBaseBuildWorkingDirectory(), getSubstitutedAccessData());
    }

    @Override
    public synchronized void setTextProvider(TextProvider textProvider) {
        super.setTextProvider(textProvider);
        if (textProvider.getText(REPOSITORY_GIT_NAME) == null)
        {
            LocalizedTextUtil.addDefaultResourceBundle("com.atlassian.bamboo.plugins.git.i18n");
        }
    }

    // Git capability is optional, so we don't enforce it here
    @Override
    public Set<Requirement> getRequirements()
    {
        return Sets.newHashSet();
    }

    public boolean isGitExecutableSet()
    {
        return (StringUtils.isNotBlank(getGitCapability()));
    }

    @Nullable
    public String getGitCapability()
    {
        return capabilityContext.getCapabilityValue(GitCapabilityTypeModule.GIT_CAPABILITY);
    }

    @Nullable
    public String getSshCapability()
    {
        return capabilityContext.getCapabilityValue(GitCapabilityTypeModule.GIT_SSH_CAPABILITY);
    }

    public void setCapabilityContext(final CapabilityContext capabilityContext)
    {
        this.capabilityContext = capabilityContext;
    }

    public void setSshProxyService(SshProxyService sshProxyService)
    {
        this.sshProxyService = sshProxyService;
    }
}
