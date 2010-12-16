package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.build.logger.NullBuildLogger;
import com.atlassian.bamboo.repository.MavenPomAccessorAbstract;
import com.atlassian.bamboo.repository.RepositoryException;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class GitMavenPomAccessor extends MavenPomAccessorAbstract
{
    public static final String POM_XML = "pom.xml";
        private static final Logger log = Logger.getLogger(GitRepository.class);
        private final GitRepository repository;
        private String pathToPom = POM_XML;

    protected GitMavenPomAccessor(GitRepository repository)
    {
        super(repository);
        this.repository = repository;
    }

    GitMavenPomAccessor withPath(String pathToProjectRoot)
    {
        if (StringUtils.isNotBlank(pathToProjectRoot))
        {
            if (pathToProjectRoot.contains(".."))
            {
                throw new IllegalArgumentException("Path to project cannot contain '..' sequence");
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
        repository.setRepositoryUrl(mavenScmUrl);
    }

    @NotNull
    public File checkoutMavenPom(@NotNull File destinationPath) throws RepositoryException
    {
        log.info("checkoutMavenPom to: " + destinationPath);
        new GitOperationHelper(new NullBuildLogger()).fetchAndCheckout(destinationPath, repository.getRepositoryUrl(), repository.getBranch(), null, repository.getSshKey(), repository.getSshPassphrase());
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

        throw new RepositoryException("Cannot find pom file in the specified location (" + pathToPom + ")");
    }
}
