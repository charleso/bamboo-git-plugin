package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.ssh.SshProxyService;
import com.atlassian.config.HomeLocator;
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
    private GitCommandProcessor gitCommandProcessor;
    // ---------------------------------------------------------------------------------------------------- Dependencies
    // ---------------------------------------------------------------------------------------------------- Constructors

    public NativeGitOperationHelper(final @NotNull GitRepository repository,
                                    final @NotNull GitRepository.GitRepositoryAccessData accessData,
                                    final @NotNull SshProxyService sshProxyService,
                                    final @NotNull BuildLogger buildLogger,
                                    final @NotNull TextProvider textProvider,
                                    final @NotNull HomeLocator homeLocator,
                                    final @Nullable String gitCapability) throws RepositoryException
    {
        super(buildLogger, sshProxyService, textProvider);
        gitCommandProcessor = new GitCommandProcessor(gitCapability, buildLogger, accessData.commandTimeout, accessData.verboseLogs);
        gitCommandProcessor.checkGitExistenceInSystem(repository.getWorkingDirectory());
        gitCommandProcessor.setSshCommand(repository.getSshCapability());
    }

    // ----------------------------------------------------------------------------------------------- Interface Methods

    @Override
    protected void doFetch(@NotNull final Transport transport, @NotNull final File sourceDirectory, @NotNull final GitRepository.GitRepositoryAccessData accessData, final RefSpec refSpec, final boolean useShallow) throws RepositoryException
    {
        final GitRepository.GitRepositoryAccessData proxiedAccessData = openProxy(accessData);
        try
        {
            gitCommandProcessor.runFetchCommand(sourceDirectory, proxiedAccessData, refSpec, useShallow);
        }
        finally
        {
            closeProxy(proxiedAccessData);
        }
    }

    @Override
    protected String doCheckout(@NotNull FileRepository localRepository, @NotNull final File sourceDirectory, @NotNull final String targetRevision, @Nullable final String previousRevision) throws RepositoryException
    {
        gitCommandProcessor.runCheckoutCommand(sourceDirectory, targetRevision);
        gitCommandProcessor.runSubmoduleInitCommand(sourceDirectory);
        gitCommandProcessor.runSubmoduleUpdateCommand(sourceDirectory);
        return targetRevision;
    }

    // -------------------------------------------------------------------------------------------------- Action Methods
    // -------------------------------------------------------------------------------------------------- Public Methods
    // -------------------------------------------------------------------------------------- Basic Accessors / Mutators

}
