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
import com.atlassian.bamboo.repository.Repository;
import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.security.StringEncrypter;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.v2.build.BuildChanges;
import com.atlassian.bamboo.v2.build.BuildChangesImpl;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;
import com.atlassian.util.concurrent.LazyReference;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Arrays;

import com.atlassian.bamboo.plugins.git.GitOperationHelper.GitOperationRepositoryData;

public class GitRepository extends AbstractRepository implements MavenPomAccessorCapableRepository
{
    // ------------------------------------------------------------------------------------------------------- Constants

    private static final String REPOSITORY_GIT_NAME = "repository.git.name";
    private static final String REPOSITORY_GIT_REPOSITORY_URL = "repository.git.repositoryUrl";
    private static final String REPOSITORY_GIT_USERNAME = "repository.git.username";
    private static final String REPOSITORY_GIT_PASSWORD = "repository.git.password";
    private static final String REPOSITORY_GIT_BRANCH = "repository.git.branch";
    private static final String REPOSITORY_GIT_SSH_KEY = "repository.git.ssh.key";
    private static final String REPOSITORY_GIT_SSH_PASSPHRASE = "repository.git.ssh.passphrase";
    private static final String REPOSITORY_GIT_MAVEN_PATH = "repository.git.maven.path";
    private static final String TEMPORARY_GIT_PASSWORD = "temporary.git.password";
    private static final String TEMPORARY_GIT_PASSWORD_CHANGE = "temporary.git.password.change";
    private static final String TEMPORARY_GIT_SSH_PASSPHRASE = "temporary.git.ssh.passphrase";
    private static final String TEMPORARY_GIT_SSH_PASSPHRASE_CHANGE = "temporary.git.ssh.passphrase.change";
    private static final String TEMPORARY_GIT_SSH_KEY_FROM_FILE = "temporary.git.ssh.keyfile";
    private static final String TEMPORARY_GIT_SSH_KEY_CHANGE = "temporary.git.ssh.key.change";

    // ------------------------------------------------------------------------------------------------- Type Properties

    private static final Logger log = Logger.getLogger(GitRepository.class);

    private String repositoryUrl;
    private String username;
    private String password;
    private String branch;
    private String sshPassphrase;
    private String sshKey;

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
                    .append(this.repositoryUrl, gitRepo.getRepositoryUrl())
                    .append(this.branch, gitRepo.getBranch())
                    //.append(this.username, gitRepo.getUsername()) //todo: ask Slawek if this makes sense    //todo: this makes sense
                    .isEquals();
        }
        else
        {
            return true;
        }
    }

    @NotNull
    public BuildChanges collectChangesSinceLastBuild(@NotNull String planKey, @Nullable String lastVcsRevisionKey) throws RepositoryException
    {
        try
        {
            final GitOperationRepositoryData repositoryData = getRepositoryData();
            final BuildLogger buildLogger = buildLoggerManager.getBuildLogger(PlanKeys.getPlanKey(planKey));
            final BuildChanges changes = new BuildChangesImpl();
            
            final String targetRevision = new GitOperationHelper(buildLogger, textProvider).obtainLatestRevision(repositoryData);
            changes.setVcsRevisionKey(targetRevision);

            if (targetRevision.equals(lastVcsRevisionKey))
            {
                return changes;
            }

            if (lastVcsRevisionKey == null)
            {
                log.info("Never checked logs for '" + planKey + "' setting latest revision to " + targetRevision);
                return changes;
            }

            final File cacheDirectory = getCacheDirectory();
            if (cacheDirectory == null)
            {
                throw new RepositoryException(textProvider.getText("repository.git.messages.cacheIsNull"));
            }
            
            List<Commit> extractedChanges;
            try
            {
                new GitOperationHelper(buildLogger, textProvider).fetch(cacheDirectory, repositoryData);
                extractedChanges = new GitOperationHelper(buildLogger, textProvider).extractCommits(cacheDirectory, lastVcsRevisionKey, targetRevision);
            }
            catch (Exception e)
            {
                buildLogger.addBuildLogEntry(textProvider.getText("repository.git.messages.ccRecover.failedToCollectChangesets", Arrays.asList(cacheDirectory)));
                FileUtils.deleteQuietly(cacheDirectory);
                buildLogger.addBuildLogEntry(textProvider.getText("repository.git.messages.ccRecover.cleanedCacheDirectory", Arrays.asList(cacheDirectory)));
                new GitOperationHelper(buildLogger, textProvider).fetch(cacheDirectory, repositoryData);
                buildLogger.addBuildLogEntry(textProvider.getText("repository.git.messages.ccRecover.fetchedRemoteRepository", Arrays.asList(cacheDirectory)));
                try
                {
                    extractedChanges = new GitOperationHelper(buildLogger, textProvider).extractCommits(cacheDirectory, lastVcsRevisionKey, targetRevision);
                    buildLogger.addBuildLogEntry(textProvider.getText("repository.git.messages.ccRecover.completed"));
                }
                catch (Exception e2)
                {
                    log.error(buildLogger.addBuildLogEntry(textProvider.getText("repository.git.messages.ccRecover.failedToExtractChangesets")), e2);
                    extractedChanges = null;
                }
            }
            if (extractedChanges == null || extractedChanges.isEmpty())
            {
                changes.setChanges(Collections.singletonList((Commit) new CommitImpl(new AuthorImpl(Author.UNKNOWN_AUTHOR),
                        textProvider.getText("repository.git.messages.unknownChanges", Arrays.asList(lastVcsRevisionKey, targetRevision)), new Date())));
            }
            else
            {
                changes.setChanges(extractedChanges);
            }

            return changes;
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
            final GitOperationRepositoryData repositoryData = getRepositoryData();
            final BuildLogger buildLogger = buildLoggerManager.getBuildLogger(buildContext.getPlanResultKey());
            final String planKey = buildContext.getPlanKey();
            final File sourceDirectory = getSourceCodeDirectory(planKey);

            try
            {
                return (new GitOperationHelper(buildLogger, textProvider).fetchAndCheckout(sourceDirectory, repositoryData, targetRevision));
            }
            catch (Exception e)
            {
                buildLogger.addBuildLogEntry(textProvider.getText("repository.git.messages.rsRecover.failedToRetrieveSource", Arrays.asList(sourceDirectory)));
                FileUtils.deleteQuietly(sourceDirectory);
                buildLogger.addBuildLogEntry(textProvider.getText("repository.git.messages.rsRecover.cleanedSourceDirectory", Arrays.asList(sourceDirectory)));
                String returnRevision = new GitOperationHelper(buildLogger, textProvider).fetchAndCheckout(sourceDirectory, repositoryData, targetRevision);
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
        }
    }

    @Override
    public void populateFromConfig(@NotNull HierarchicalConfiguration config)
    {
        super.populateFromConfig(config);
        repositoryUrl = config.getString(REPOSITORY_GIT_REPOSITORY_URL);
        username = config.getString(REPOSITORY_GIT_USERNAME);
        password = config.getString(REPOSITORY_GIT_PASSWORD);
        branch = config.getString(REPOSITORY_GIT_BRANCH);
        sshKey = config.getString(REPOSITORY_GIT_SSH_KEY);
        sshPassphrase = config.getString(REPOSITORY_GIT_SSH_PASSPHRASE);

        pathToPom = config.getString(REPOSITORY_GIT_MAVEN_PATH);
    }

    @NotNull
    @Override
    public HierarchicalConfiguration toConfiguration()
    {
        HierarchicalConfiguration configuration = super.toConfiguration();
        configuration.setProperty(REPOSITORY_GIT_REPOSITORY_URL, repositoryUrl);
        configuration.setProperty(REPOSITORY_GIT_USERNAME, username);
        configuration.setProperty(REPOSITORY_GIT_PASSWORD, password);
        configuration.setProperty(REPOSITORY_GIT_BRANCH, branch);
        configuration.setProperty(REPOSITORY_GIT_SSH_KEY, sshKey);
        configuration.setProperty(REPOSITORY_GIT_SSH_PASSPHRASE, sshPassphrase);
        return configuration;
    }

    @Override
    @NotNull
    public ErrorCollection validate(@NotNull BuildConfiguration buildConfiguration)
    {
        ErrorCollection errorCollection = super.validate(buildConfiguration);

        String repoUrl = buildConfiguration.getString(REPOSITORY_GIT_REPOSITORY_URL);
        if (StringUtils.isBlank(repoUrl))
        {
            errorCollection.addError(REPOSITORY_GIT_REPOSITORY_URL, textProvider.getText("repository.git.messages.missingRepositoryUrl"));
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

    // -------------------------------------------------------------------------------------------------- Public Methods

    // -------------------------------------------------------------------------------------------------- Helper Methods

    GitOperationRepositoryData getRepositoryData()
    {
        StringEncrypter encrypter = new StringEncrypter();
        return new GitOperationRepositoryData(
                repositoryUrl,
                branch,
                username,
                encrypter.decrypt(password),
                encrypter.decrypt(sshKey),
                encrypter.decrypt(sshPassphrase)
        );
    }

    // -------------------------------------------------------------------------------------- Basic Accessors / Mutators

    public String getRepositoryUrl()
    {
        return repositoryUrl;
    }

    void setRepositoryUrl(String repositoryUrl)
    {
        this.repositoryUrl = repositoryUrl;
    }

    public String getBranch()
    {
        return branch;
    }

    public File getCacheDirectory()
    {
        return GitCacheDirectory.getCacheDirectory(getWorkingDirectory(), repositoryUrl);
    }
}
