package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.repository.RepositoryException;
import com.opensymphony.xwork.TextProvider;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.Transport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class NativeGitOperationHelper extends GitOperationHelper
{
    private String gitCapability;
    private GitCommandProcessor gitCommandProcessor;

    public NativeGitOperationHelper(final @NotNull GitRepository repository, final @NotNull GitRepository.GitRepositoryAccessData accessData, final @NotNull BuildLogger buildLogger, final @NotNull TextProvider textProvider, final @Nullable String gitCapability) throws RepositoryException
    {
        super(buildLogger, textProvider);
        this.gitCapability = gitCapability;
        gitCommandProcessor = new GitCommandProcessor(gitCapability, buildLogger, accessData.commandTimeout, accessData.verboseLogs);
        gitCommandProcessor.checkGitExistenceInSystem(repository.getWorkingDirectory());
    }

    @Override
    protected void doFetch(@NotNull final Transport transport, @NotNull final File sourceDirectory, @NotNull final GitRepository.GitRepositoryAccessData accessData, final RefSpec refSpec, final boolean useShallow) throws RepositoryException
    {
        gitCommandProcessor.runFetchCommand(sourceDirectory, accessData, refSpec, useShallow);
    }

    @Override
    protected String doCheckout(@NotNull FileRepository localRepository, @NotNull final File sourceDirectory, @NotNull final String targetRevision, @Nullable final String previousRevision) throws RepositoryException
    {
        gitCommandProcessor.runCheckoutCommand(sourceDirectory, targetRevision);
        return targetRevision;
    }
}
