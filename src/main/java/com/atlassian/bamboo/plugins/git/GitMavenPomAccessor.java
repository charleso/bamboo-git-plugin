package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.build.logger.NullBuildLogger;
import com.atlassian.bamboo.repository.MavenPomAccessorAbstract;
import com.atlassian.bamboo.repository.RepositoryException;
import com.opensymphony.xwork.TextProvider;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Arrays;

public class GitMavenPomAccessor extends MavenPomAccessorAbstract
{
    public static final String POM_XML = "pom.xml";
    private static final Logger log = Logger.getLogger(GitMavenPomAccessor.class);
    private final GitRepository repository;
    private String pathToPom = POM_XML;
    private final TextProvider textProvider;
    @Nullable
    private final String gitCapability;

    protected GitMavenPomAccessor(GitRepository repository, @NotNull final TextProvider textProvider, @Nullable String gitCapability)
    {
        super(repository);
        this.repository = repository;
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
        GitOperationHelper helper = new JGitOperationHelper(new NullBuildLogger(), textProvider);
        GitRepository.GitRepositoryAccessData substitutedAccessData = repository.getSubstitutedAccessData();
        String targetRevision = helper.obtainLatestRevision(substitutedAccessData);
        helper.fetch(destinationPath, substitutedAccessData, true);
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
