package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.build.logger.NullBuildLogger;
import com.atlassian.bamboo.repository.MavenPomAccessorAbstract;
import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.ssh.SshProxyService;
import com.opensymphony.xwork.TextProvider;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Arrays;

public class GitMavenPomAccessor extends MavenPomAccessorAbstract<GitRepository>
{
    private static final Logger log = Logger.getLogger(GitRepository.class);
    // ------------------------------------------------------------------------------------------------------- Constants
    public static final String POM_XML = "pom.xml";
    // ------------------------------------------------------------------------------------------------- Type Properties
    private final GitRepository repository;
    private String pathToPom = POM_XML;

    @Nullable
    private final String gitCapability;
    // ---------------------------------------------------------------------------------------------------- Dependencies
    private final SshProxyService sshProxyService;
    private final TextProvider textProvider;
    // ---------------------------------------------------------------------------------------------------- Constructors
    // ----------------------------------------------------------------------------------------------- Interface Methods
    // -------------------------------------------------------------------------------------------------- Action Methods
    // -------------------------------------------------------------------------------------------------- Public Methods
    // -------------------------------------------------------------------------------------- Basic Accessors / Mutators

    protected GitMavenPomAccessor(GitRepository repository,
                                  @NotNull final SshProxyService sshProxyService,
                                  @NotNull final TextProvider textProvider,
                                  @Nullable String gitCapability)
    {
        super(repository);
        this.repository = repository;
        this.sshProxyService = sshProxyService;
        this.textProvider = textProvider;
        this.gitCapability = gitCapability;
    }

    GitMavenPomAccessor withPath(String pathToProjectRoot)
    {
        if (StringUtils.isNotBlank(pathToProjectRoot))
        {
            if (pathToProjectRoot.contains(".."))
            {
                throw new IllegalArgumentException(textProvider.getText("repository.git.messages.invalidPomPath"));
            }
            this.pathToPom = pathToProjectRoot;
        }
        return this;
    }

    @NotNull
    public String getMavenScmProviderKey()
    {
        return "git";
    }

    public void parseMavenScmUrl(@NotNull String mavenScmUrl) throws IllegalArgumentException
    {
        repository.accessData.repositoryUrl = mavenScmUrl;
    }

    @NotNull
    public File checkoutMavenPom(@NotNull File destinationPath) throws RepositoryException
    {
        log.info("checkoutMavenPom to: " + destinationPath);
        GitOperationHelper helper = new JGitOperationHelper(repository.getSubstitutedAccessData(), new NullBuildLogger(), textProvider);
        String targetRevision = helper.obtainLatestRevision();
        helper.fetch(destinationPath, true);
        helper.checkout(null, destinationPath, targetRevision, helper.getCurrentRevision(destinationPath));
        final File pomLocation = new File(destinationPath, pathToPom);
        if (pomLocation.isFile())
        {
            return pomLocation;
        }

        if (pomLocation.isDirectory())
        {
            File candidate = new File(pomLocation, POM_XML);
            if (candidate.isFile())
            {
                return candidate;
            }
        }

        throw new RepositoryException(textProvider.getText("repository.git.messages.cannotFindPom", Arrays.asList(pathToPom)));
    }
}
