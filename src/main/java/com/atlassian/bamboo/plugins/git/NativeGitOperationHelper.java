package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.ssh.SshProxyService;
import com.opensymphony.xwork.TextProvider;
import org.apache.log4j.Logger;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.Transport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class NativeGitOperationHelper extends GitOperationHelper
{
    @SuppressWarnings("UnusedDeclaration")
    private static final Logger log = Logger.getLogger(NativeGitOperationHelper.class);
    // ------------------------------------------------------------------------------------------------------- Constants
    // ------------------------------------------------------------------------------------------------- Type Properties
    private String gitCapability;
    // ---------------------------------------------------------------------------------------------------- Dependencies
    // ---------------------------------------------------------------------------------------------------- Constructors

    public NativeGitOperationHelper(final @NotNull BuildLogger buildLogger,
                                    final @NotNull SshProxyService sshProxyService,
                                    final @NotNull TextProvider textProvider,
                                    final @Nullable String gitCapability)
    {
        super(buildLogger, sshProxyService, textProvider);
        this.gitCapability = gitCapability;
    }

    // ----------------------------------------------------------------------------------------------- Interface Methods

    @Override
    protected void doFetch(@NotNull final Transport transport, @NotNull final File sourceDirectory, @NotNull final GitRepository.GitRepositoryAccessData accessData, final RefSpec refSpec, final boolean useShallow) throws RepositoryException
    {
        GitCommandProcessor gitCommandProcessor = new GitCommandProcessor(gitCapability, buildLogger, 1);
        gitCommandProcessor.runFetchCommand(sourceDirectory, accessData, refSpec, useShallow);
    }

    @Override
    protected String doCheckout(@NotNull FileRepository localRepository, @NotNull final File sourceDirectory, @NotNull final String targetRevision, @Nullable final String previousRevision) throws RepositoryException
    {
        GitCommandProcessor gitCommandProcessor = new GitCommandProcessor(gitCapability, buildLogger, 1);
        gitCommandProcessor.runCheckoutCommand(sourceDirectory, targetRevision);
        return targetRevision;
    }

    // -------------------------------------------------------------------------------------------------- Action Methods
    // -------------------------------------------------------------------------------------------------- Public Methods
    // -------------------------------------------------------------------------------------- Basic Accessors / Mutators

}
