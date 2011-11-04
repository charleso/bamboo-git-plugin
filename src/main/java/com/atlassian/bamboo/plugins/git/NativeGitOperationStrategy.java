package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.repository.RepositoryException;
import com.opensymphony.xwork.TextProvider;
import org.eclipse.jgit.transport.RefSpec;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class NativeGitOperationStrategy implements GitOperationStrategy
{
    private final BuildLogger buildLogger;
    private final TextProvider textProvider;
    private final String gitCapability;

    public NativeGitOperationStrategy(final BuildLogger buildLogger, final TextProvider textProvider, final String gitCapability)
    {
        this.buildLogger = buildLogger;
        this.textProvider = textProvider;
        this.gitCapability = gitCapability;
    }

    @Override
    public void fetch(@NotNull final File sourceDirectory, @NotNull final GitRepository.GitRepositoryAccessData accessData, RefSpec refSpec, final boolean useShallow) throws RepositoryException
    {
        GitCommandProcessor gitCommandProcessor = new GitCommandProcessor(gitCapability, buildLogger, 1);
        gitCommandProcessor.runFetchCommand(sourceDirectory, accessData, refSpec, useShallow);
    }
}
