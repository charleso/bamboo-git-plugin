package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.repository.AbstractRepository;
import com.atlassian.bamboo.repository.Repository;
import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.security.StringEncrypter;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.v2.build.BuildChanges;
import com.atlassian.bamboo.v2.build.BuildChangesImpl;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.EqualsBuilder;
//import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public class GitRepository extends AbstractRepository
{
    // ------------------------------------------------------------------------------------------------------- Constants

    private static final String REPOSITORY_GIT_NAME = "repository.git.name";
    private static final String REPOSITORY_GIT_REPOSITORY_URL = "repository.git.repositoryUrl";
    private static final String REPOSITORY_GIT_BRANCH = "repository.git.branch";
    private static final String REPOSITORY_GIT_SSH_KEY = "repository.git.ssh.key";
    private static final String REPOSITORY_GIT_SSH_PASSPHRASE = "repository.git.ssh.passphrase";
    private static final String REPOSITORY_GIT_ERROR_MISSING_REPOSITORY_URL = "repository.git.error.missingRepositoryUrl";
    private static final String TEMPORARY_GIT_SSH_PASSPHRASE = "temporary.git.ssh.passphrase";
    private static final String TEMPORARY_GIT_SSH_PASSPHRASE_CHANGE = "temporary.git.ssh.passphrase.change";
    private static final String TEMPORARY_GIT_SSH_KEY_FROM_FILE = "temporary.git.ssh.keyfile";
    private static final String TEMPORARY_GIT_SSH_KEY_CHANGE = "temporary.git.ssh.key.change";

    // ------------------------------------------------------------------------------------------------- Type Properties

//    private static final Logger log = Logger.getLogger(GitRepository.class);

    private String repositoryUrl;
    private String branch;
    private String sshPassphrase;
    private String sshKey;

    // ---------------------------------------------------------------------------------------------------- Dependencies

    // ---------------------------------------------------------------------------------------------------- Constructors

    // ----------------------------------------------------------------------------------------------- Interface Methods

    @NotNull
    public String getName()
    {
        return textProvider.getText(REPOSITORY_GIT_NAME);
    }

    public String getHost() {
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
        BuildChanges changes = new BuildChangesImpl();

        StringEncrypter encrypter = new StringEncrypter();
        String targetRevision = new GitOperationHelper().obtainLatestRevision(repositoryUrl, branch, encrypter.decrypt(sshKey), encrypter.decrypt(sshPassphrase));
        changes.setVcsRevisionKey(targetRevision);

        if (targetRevision.equals(lastVcsRevisionKey))
        {
            return changes;
        }

        File cacheDirectory = GitCacheDirectory.getCacheDirectory(getWorkingDirectory(), repositoryUrl);
        new GitOperationHelper().fetch(cacheDirectory, repositoryUrl, branch, encrypter.decrypt(sshKey), encrypter.decrypt(sshPassphrase));

        changes.setChanges(new GitOperationHelper().extractCommits(cacheDirectory, lastVcsRevisionKey, targetRevision));
        return changes;
    }

    @NotNull
    public String retrieveSourceCode(@NotNull BuildContext buildContext, @Nullable final String targetRevision) throws RepositoryException
    {
        final String planKey = buildContext.getPlanKey();
        final File sourceDirectory = getSourceCodeDirectory(planKey);

        StringEncrypter encrypter = new StringEncrypter();
        String previousRevision = new GitOperationHelper().fetch(sourceDirectory, repositoryUrl, branch, encrypter.decrypt(sshKey), encrypter.decrypt(sshPassphrase));

        return new GitOperationHelper().checkout(sourceDirectory, targetRevision, previousRevision);
    }

    @NotNull
    public String retrieveSourceCode(@NotNull String planKey, @Nullable String vcsRevisionKey) throws RepositoryException
    {
        //deprecated!
        throw new UnsupportedOperationException();
    }

    public void prepareConfigObject(@NotNull BuildConfiguration buildConfiguration)
    {
        StringEncrypter encrypter = null;
        if (buildConfiguration.getBoolean(TEMPORARY_GIT_SSH_PASSPHRASE_CHANGE))
        {
            encrypter = new StringEncrypter();
            buildConfiguration.setProperty(REPOSITORY_GIT_SSH_PASSPHRASE, encrypter.encrypt(buildConfiguration.getString(TEMPORARY_GIT_SSH_PASSPHRASE)));
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
//                    log.error("Cannot read uploaded ssh key file", e);
                    return;
                }
                encrypter = encrypter == null ? new StringEncrypter() : encrypter;
                buildConfiguration.setProperty(REPOSITORY_GIT_SSH_KEY, encrypter.encrypt(key));
            }
        }
    }

    @Override
    public void populateFromConfig(@NotNull HierarchicalConfiguration config)
    {
        super.populateFromConfig(config);
        repositoryUrl = config.getString(REPOSITORY_GIT_REPOSITORY_URL);
        branch = config.getString(REPOSITORY_GIT_BRANCH);
        sshKey = config.getString(REPOSITORY_GIT_SSH_KEY);
        sshPassphrase = config.getString(REPOSITORY_GIT_SSH_PASSPHRASE);
    }

    @NotNull
    @Override
    public HierarchicalConfiguration toConfiguration()
    {
        HierarchicalConfiguration configuration = super.toConfiguration();
        configuration.setProperty(REPOSITORY_GIT_REPOSITORY_URL, repositoryUrl);
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
            errorCollection.addError(REPOSITORY_GIT_REPOSITORY_URL, textProvider.getText(REPOSITORY_GIT_ERROR_MISSING_REPOSITORY_URL));
        }
        return errorCollection;
    }

    // -------------------------------------------------------------------------------------------------- Public Methods

    // -------------------------------------------------------------------------------------------------- Helper Methods

    // -------------------------------------------------------------------------------------- Basic Accessors / Mutators

    public String getRepositoryUrl()
    {
        return repositoryUrl;
    }

    public String getBranch()
    {
        return branch;
    }

}
