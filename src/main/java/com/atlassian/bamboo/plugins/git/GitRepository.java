package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.author.Author;
import com.atlassian.bamboo.author.AuthorImpl;
import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.commit.Commit;
import com.atlassian.bamboo.commit.CommitImpl;
import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.repository.AbstractRepository;
import com.atlassian.bamboo.repository.MavenPomAccessor;
import com.atlassian.bamboo.repository.MavenPomAccessorCapableRepository;
import com.atlassian.bamboo.repository.NameValuePair;
import com.atlassian.bamboo.repository.Repository;
import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.repository.SelectableAuthenticationRepository;
import com.atlassian.bamboo.security.StringEncrypter;
import com.atlassian.bamboo.utils.SystemProperty;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.v2.build.BuildChanges;
import com.atlassian.bamboo.v2.build.BuildChangesImpl;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;
import com.atlassian.util.concurrent.LazyReference;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.opensymphony.xwork.TextProvider;
import com.opensymphony.xwork.util.LocalizedTextUtil;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class GitRepository extends AbstractRepository implements MavenPomAccessorCapableRepository, SelectableAuthenticationRepository
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
    private static final String TEMPORARY_GIT_PASSWORD = "temporary.git.password";
    private static final String TEMPORARY_GIT_PASSWORD_CHANGE = "temporary.git.password.change";
    private static final String TEMPORARY_GIT_SSH_PASSPHRASE = "temporary.git.ssh.passphrase";
    private static final String TEMPORARY_GIT_SSH_PASSPHRASE_CHANGE = "temporary.git.ssh.passphrase.change";
    private static final String TEMPORARY_GIT_SSH_KEY_FROM_FILE = "temporary.git.ssh.keyfile";
    private static final String TEMPORARY_GIT_SSH_KEY_CHANGE = "temporary.git.ssh.key.change";

    private static final GitAuthenticationType defaultAuthenticationType = GitAuthenticationType.NONE;
    private static boolean USE_SHALLOW_CLONES = new SystemProperty(false, "atlassian.bamboo.git.useShallowClones", "ATLASSIAN_BAMBO_GIT_USE_SHALLOW_CLONES").getValue(true);

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
    }

    final GitRepositoryAccessData accessData = new GitRepositoryAccessData();

    private boolean useShallowClones;

    // Maven 2 import
    private transient String pathToPom;

    //todo: Spring-inject StringEncrypter singleton, https://atlaseye.atlassian.com/cru/CR-BAM-2232#c37222

    // ---------------------------------------------------------------------------------------------------- Dependencies

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
    public BuildChanges collectChangesSinceLastBuild(@NotNull String planKey, @Nullable final String lastVcsRevisionKey) throws RepositoryException
    {
        try
        {
            final BuildLogger buildLogger = buildLoggerManager.getBuildLogger(PlanKeys.getPlanKey(planKey));

            final String targetRevision = new GitOperationHelper(buildLogger, textProvider).obtainLatestRevision(accessData);

            if (targetRevision.equals(lastVcsRevisionKey))
            {
                return new BuildChangesImpl(targetRevision);
            }

            if (lastVcsRevisionKey == null)
            {
                buildLogger.addBuildLogEntry(textProvider.getText("repository.git.messages.ccRepositoryNeverChecked", Arrays.asList(targetRevision)));
                try
                {
                    GitCacheDirectory.callOnCacheWithLock(getCacheDirectory(), new AbstractGitCacheDirectoryOperation<Void>()
                    {
                        public Void call(@NotNull File cacheDirectory) throws RepositoryException
                        {
                            new GitOperationHelper(buildLogger, textProvider).fetch(cacheDirectory, accessData, USE_SHALLOW_CLONES & useShallowClones );
                            return null;
                        }
                    });
                }
                catch (Exception e)
                {
                    throw new RepositoryException(e.getMessage(), e);
                }
                return new BuildChangesImpl(targetRevision);
            }

            BuildChanges buildChanges;
            try
            {
                buildChanges = GitCacheDirectory.callOnCacheWithLock(getCacheDirectory(), new AbstractGitCacheDirectoryOperation<BuildChanges>()
                {
                    public BuildChanges call(@NotNull File cacheDirectory) throws Exception
                    {
                        new GitOperationHelper(buildLogger, textProvider).fetch(cacheDirectory, accessData);
                        return new GitOperationHelper(buildLogger, textProvider).extractCommits(cacheDirectory, lastVcsRevisionKey, targetRevision);
                    }
                });
            }
            catch (Exception e)
            {
                try
                {
                    buildChanges = GitCacheDirectory.callOnCacheWithLock(getCacheDirectory(), new AbstractGitCacheDirectoryOperation<BuildChanges>()
                    {
                        @Override
                        public BuildChanges call(@NotNull File cacheDirectory) throws Exception
                        {
                            buildLogger.addBuildLogEntry(textProvider.getText("repository.git.messages.ccRecover.failedToCollectChangesets", Arrays.asList(cacheDirectory)));
                            FileUtils.deleteQuietly(cacheDirectory);
                            buildLogger.addBuildLogEntry(textProvider.getText("repository.git.messages.ccRecover.cleanedCacheDirectory", Arrays.asList(cacheDirectory)));
                            new GitOperationHelper(buildLogger, textProvider).fetch(cacheDirectory, accessData);
                            buildLogger.addBuildLogEntry(textProvider.getText("repository.git.messages.ccRecover.fetchedRemoteRepository", Arrays.asList(cacheDirectory)));
                            BuildChanges extractedChanges = new GitOperationHelper(buildLogger, textProvider).extractCommits(cacheDirectory, lastVcsRevisionKey, targetRevision);
                            buildLogger.addBuildLogEntry(textProvider.getText("repository.git.messages.ccRecover.completed"));
                            return extractedChanges;
                        }
                    });
                }
                catch (Exception e2)
                {
                    log.error(buildLogger.addBuildLogEntry(textProvider.getText("repository.git.messages.ccRecover.failedToExtractChangesets")), e2);
                    buildChanges = null;
                }
            }

            if (buildChanges == null || buildChanges.getChanges().isEmpty())
            {
                return new BuildChangesImpl(targetRevision, Collections.singletonList((Commit) new CommitImpl(new AuthorImpl(Author.UNKNOWN_AUTHOR),
                        textProvider.getText("repository.git.messages.unknownChanges", Arrays.asList(lastVcsRevisionKey, targetRevision)), new Date())));
            }
            else
            {
                return buildChanges;
            }

        }
        catch (RuntimeException e)
        {
            throw new RepositoryException(textProvider.getText("repository.git.messages.runtimeException"), e);
        }
    }

    @NotNull
    public String retrieveSourceCode(@NotNull BuildContext buildContext, @Nullable final String targetRevision) throws RepositoryException
    {
        try
        {
            final BuildLogger buildLogger = buildLoggerManager.getBuildLogger(buildContext.getPlanResultKey());
            final String planKey = buildContext.getPlanKey();
            final File sourceDirectory = getSourceCodeDirectory(planKey);

            try
            {
                return (new GitOperationHelper(buildLogger, textProvider).fetchAndCheckout(sourceDirectory, accessData, targetRevision, USE_SHALLOW_CLONES & useShallowClones));
            }
            catch (Exception e)
            {
                buildLogger.addBuildLogEntry(textProvider.getText("repository.git.messages.rsRecover.failedToRetrieveSource", Arrays.asList(sourceDirectory)));
                FileUtils.deleteQuietly(sourceDirectory);
                buildLogger.addBuildLogEntry(textProvider.getText("repository.git.messages.rsRecover.cleanedSourceDirectory", Arrays.asList(sourceDirectory)));
                String returnRevision = new GitOperationHelper(buildLogger, textProvider).fetchAndCheckout(sourceDirectory, accessData, targetRevision);
                buildLogger.addBuildLogEntry(textProvider.getText("repository.git.messages.rsRecover.completed"));
                return returnRevision;
            }
        }
        catch (RuntimeException e)
        {
            throw new RepositoryException(textProvider.getText("repository.git.messages.runtimeException"), e);
        }
    }

    @NotNull
    public String retrieveSourceCode(@NotNull String planKey, @Nullable String vcsRevisionKey) throws RepositoryException
    {
        //deprecated!
        throw new UnsupportedOperationException();
    }

    @Override
    public void addDefaultValues(@NotNull BuildConfiguration buildConfiguration)
    {
        buildConfiguration.setProperty(REPOSITORY_GIT_USE_SHALLOW_CLONES, true);
    }

    public void prepareConfigObject(@NotNull BuildConfiguration buildConfiguration)
    {
        final LazyReference<StringEncrypter> encrypterRef = new LazyReference<StringEncrypter>()
        {
            @Override
            protected StringEncrypter create() throws Exception
            {
                return new StringEncrypter();
            }
        };

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
        accessData.username = config.getString(REPOSITORY_GIT_USERNAME);
        accessData.password = config.getString(REPOSITORY_GIT_PASSWORD);
        accessData.branch = config.getString(REPOSITORY_GIT_BRANCH);
        accessData.sshKey = config.getString(REPOSITORY_GIT_SSH_KEY);
        accessData.sshPassphrase = config.getString(REPOSITORY_GIT_SSH_PASSPHRASE);
        accessData.authenticationType = safeParseAuthenticationType(config.getString(REPOSITORY_GIT_AUTHENTICATION_TYPE));
        useShallowClones = config.getBoolean(REPOSITORY_GIT_USE_SHALLOW_CLONES);

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
        configuration.setProperty(REPOSITORY_GIT_USE_SHALLOW_CLONES, useShallowClones);
        return configuration;
    }

    @Override
    @NotNull
    public ErrorCollection validate(@NotNull BuildConfiguration buildConfiguration)
    {
        ErrorCollection errorCollection = super.validate(buildConfiguration);

        final String repoUrl = StringUtils.trim(buildConfiguration.getString(REPOSITORY_GIT_REPOSITORY_URL));
        final String username = buildConfiguration.getString(REPOSITORY_GIT_USERNAME);
        final GitAuthenticationType authenticationType = safeParseAuthenticationType(buildConfiguration.getString(REPOSITORY_GIT_AUTHENTICATION_TYPE));


        if (StringUtils.isBlank(repoUrl))
        {
            errorCollection.addError(REPOSITORY_GIT_REPOSITORY_URL, textProvider.getText("repository.git.messages.missingRepositoryUrl"));
        }
        else
        {
            final boolean isHttp = repoUrl.startsWith("http://") || repoUrl.startsWith("https://");
            if (isHttp && authenticationType == GitAuthenticationType.SSH_KEYPAIR)
            {
                errorCollection.addError(REPOSITORY_GIT_AUTHENTICATION_TYPE, textProvider.getText("repository.git.messages.unsupportedHttpAuthenticationType"));
            }
            if (!isHttp && StringUtils.isNotEmpty(username) && authenticationType == GitAuthenticationType.PASSWORD)
            {
                errorCollection.addError(REPOSITORY_GIT_USERNAME, textProvider.getText("repository.git.messages.unsupportedUsernameField"));
            }
        }

        if (buildConfiguration.getString(REPOSITORY_GIT_MAVEN_PATH, "").contains(".."))
        {
            errorCollection.addError(REPOSITORY_GIT_MAVEN_PATH, textProvider.getText("repository.git.messages.invalidPomPath"));
        }

        return errorCollection;
    }

    @NotNull
    public MavenPomAccessor getMavenPomAccessor()
    {
        return new GitMavenPomAccessor(this, textProvider).withPath(pathToPom);
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

    // -------------------------------------------------------------------------------------- Basic Accessors / Mutators

    public boolean getUseShallowClones()
    {
        return useShallowClones;
    }

    public String getRepositoryUrl()
    {
        return accessData.repositoryUrl;
    }

    public String getBranch()
    {
        return accessData.branch;
    }

    public String getAuthTypeName()
    {
        return getAuthTypeName(getAuthType());
    }

    public File getCacheDirectory()
    {
        return GitCacheDirectory.getCacheDirectory(getWorkingDirectory(), accessData);
    }

    @Override
    public synchronized void setTextProvider(TextProvider textProvider) {
        super.setTextProvider(textProvider);
        if (textProvider.getText(REPOSITORY_GIT_NAME) == null)
        {
            LocalizedTextUtil.addDefaultResourceBundle("com.atlassian.bamboo.plugins.git.i18n");
        }
    }

}
