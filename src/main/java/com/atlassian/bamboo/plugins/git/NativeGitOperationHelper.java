package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.ssh.ProxyConnectionData;
import com.atlassian.bamboo.ssh.ProxyException;
import com.atlassian.bamboo.ssh.SshProxyService;
import com.google.common.collect.ImmutableMap;
import com.opensymphony.xwork.TextProvider;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class NativeGitOperationHelper extends GitOperationHelper
{
    @SuppressWarnings("UnusedDeclaration")
    private static final Logger log = Logger.getLogger(NativeGitOperationHelper.class);
    // ------------------------------------------------------------------------------------------------------- Constants
    // ------------------------------------------------------------------------------------------------- Type Properties
    protected SshProxyService sshProxyService;
    GitCommandProcessor gitCommandProcessor;
    // ---------------------------------------------------------------------------------------------------- Dependencies
    // ---------------------------------------------------------------------------------------------------- Constructors

    public NativeGitOperationHelper(final @NotNull GitRepository repository,
                                    final @NotNull GitRepository.GitRepositoryAccessData accessData,
                                    final @NotNull SshProxyService sshProxyService,
                                    final @NotNull BuildLogger buildLogger,
                                    final @NotNull TextProvider textProvider) throws RepositoryException
    {
        super(accessData, buildLogger, textProvider);
        this.sshProxyService = sshProxyService;
        this.gitCommandProcessor = new GitCommandProcessor(repository.getGitCapability(), buildLogger, accessData.commandTimeout, accessData.verboseLogs);
        this.gitCommandProcessor.checkGitExistenceInSystem(repository.getWorkingDirectory());
        this.gitCommandProcessor.setSshCommand(repository.getSshCapability());
    }

    // ----------------------------------------------------------------------------------------------- Interface Methods

    @Override
    public void pushRevision(@NotNull final File sourceDirectory, @NotNull String revision) throws RepositoryException
    {
        String possibleBranch = gitCommandProcessor.getPossibleBranchNameForCheckout(sourceDirectory, revision);
        if (StringUtils.isBlank(possibleBranch))
        {
            throw new RepositoryException("Can't guess branch name for revision " + revision + " when trying to perform push.");
        }
        final GitRepository.GitRepositoryAccessData proxiedAccessData = adjustRepositoryAccess(accessData);
        GitCommandBuilder commandBuilder = gitCommandProcessor.createCommandBuilder("push", proxiedAccessData.repositoryUrl, possibleBranch);
        if (proxiedAccessData.verboseLogs)
        {
            commandBuilder.verbose(true);
        }
        gitCommandProcessor.runCommand(commandBuilder, sourceDirectory);
    }

    @Override
    public String commit(@NotNull File sourceDirectory, @NotNull String message, @NotNull String comitterName, @NotNull String comitterEmail) throws RepositoryException
    {
        GitCommandBuilder commandBuilder = gitCommandProcessor
                .createCommandBuilder("commit", "-m", message, "--all")
                .env(identificationVariables(comitterName, comitterEmail));

        if (accessData.verboseLogs)
        {
            commandBuilder.verbose(true);
        }
        gitCommandProcessor.runCommand(commandBuilder, sourceDirectory);
        return getCurrentRevision(sourceDirectory);
    }

    public ImmutableMap<String, String> identificationVariables(@NotNull String name, @NotNull String email)
    {
        return ImmutableMap.of(
                "GIT_COMMITTER_NAME", name, //needed for merge
                "GIT_COMMITTER_EMAIL", email, //otherwise warning on commit
                "GIT_AUTHOR_NAME", name, //needed for commit
                "GIT_AUTHOR_EMAIL", email); //not required
    }

    @Override
    protected void doFetch(@NotNull final Transport transport, @NotNull final File sourceDirectory, final RefSpec refSpec, final boolean useShallow) throws RepositoryException
    {
        final GitRepository.GitRepositoryAccessData proxiedAccessData = adjustRepositoryAccess(accessData);
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
    protected String doCheckout(@NotNull FileRepository localRepository, @NotNull final File sourceDirectory, @NotNull final String targetRevision, @Nullable final String previousRevision, final boolean useSubmodules) throws RepositoryException
    {
        gitCommandProcessor.runCheckoutCommand(sourceDirectory, targetRevision);
        if (useSubmodules)
        {
            gitCommandProcessor.runSubmoduleUpdateCommand(sourceDirectory);
        }
        return targetRevision;
    }

    // -------------------------------------------------------------------------------------------------- Action Methods
    // -------------------------------------------------------------------------------------------------- Public Methods
    // -------------------------------------------------------------------------------------- Basic Accessors / Mutators

    protected GitRepository.GitRepositoryAccessData adjustRepositoryAccess(@NotNull final GitRepository.GitRepositoryAccessData accessData) throws RepositoryException
    {
        if (accessData.authenticationType == GitAuthenticationType.SSH_KEYPAIR)
        {
            GitRepository.GitRepositoryAccessData proxyAccessData = accessData.cloneAccessData();

            if (!StringUtils.contains(proxyAccessData.repositoryUrl, "://"))
            {
                proxyAccessData.repositoryUrl = "ssh://" + proxyAccessData.repositoryUrl.replaceFirst(":", "/");
            }

            URI repositoryUri = URI.create(proxyAccessData.repositoryUrl);
            if ("git".equals(repositoryUri.getScheme()) || "ssh".equals(repositoryUri.getScheme()))
            {
                try
                {
                    String username = extractUsername(proxyAccessData.repositoryUrl);
                    if (username != null)
                    {
                        proxyAccessData.username = username;
                    }

                    ProxyConnectionData connectionData = sshProxyService.createProxyConnectionDataBuilder()
                            .withRemoteAddress(repositoryUri.getHost(), repositoryUri.getPort() == -1 ? 22 : repositoryUri.getPort())
                            .withRemoteUserName(StringUtils.defaultIfEmpty(proxyAccessData.username, repositoryUri.getUserInfo()))
                            .withErrorReceiver(gitCommandProcessor)
                            .withKeyFromString(proxyAccessData.sshKey, proxyAccessData.sshPassphrase)
                            .build();

                    proxyAccessData.proxyRegistrationInfo = sshProxyService.register(connectionData);

                    URI cooked = new URI(repositoryUri.getScheme(),
                                         proxyAccessData.proxyRegistrationInfo.getProxyUserName(),
                                         proxyAccessData.proxyRegistrationInfo.getProxyHost(),
                                         proxyAccessData.proxyRegistrationInfo.getProxyPort(),
                                         repositoryUri.getRawPath(),
                                         repositoryUri.getRawQuery(),
                                         repositoryUri.getRawFragment());

                    proxyAccessData.repositoryUrl = cooked.toString();
                }
                catch (IOException e)
                {
                    if (e.getMessage().contains("exception using cipher - please check password and data."))
                    {
                        throw new RepositoryException(buildLogger.addErrorLogEntry("Encryption exception - please check ssh keyfile passphrase."), e);
                    }
                    else
                    {
                        throw new RepositoryException("Cannot decode connection params", e);
                    }
                }
                catch (ProxyException e)
                {
                    throw new RepositoryException("Cannot create SSH proxy", e);
                }
                catch (URISyntaxException e)
                {
                    throw new RepositoryException("Remote repository URL invalid", e);
                }

                return proxyAccessData;
            }
        }
        else
        {
            if (accessData.authenticationType == GitAuthenticationType.PASSWORD)
            {
                GitRepository.GitRepositoryAccessData credentialsAwareAccessData = accessData.cloneAccessData();
                URI repositoryUrl = wrapWithUsernameAndPassword(credentialsAwareAccessData);
                credentialsAwareAccessData.repositoryUrl = repositoryUrl.toString();

                return credentialsAwareAccessData;
            }
        }

        return accessData;
    }

    @Override
    public boolean merge(@NotNull final File workspaceDir, @NotNull final String targetRevision,
                         @NotNull String committerName, @NotNull String committerEmail) throws RepositoryException
    {
        GitCommandBuilder commandBuilder =
                gitCommandProcessor
                        .createCommandBuilder("merge", "--no-commit", targetRevision)
                        .env(identificationVariables(committerName, committerEmail));

        return gitCommandProcessor.runMergeCommand(commandBuilder, workspaceDir);
    }

    @Nullable
    private String extractUsername(final String repositoryUrl) throws URISyntaxException
    {
        URIish uri = new URIish(repositoryUrl);

        final String auth = uri.getUser();
        if (auth == null)
        {
            return null;
        }
        return auth;
    }


    @NotNull
    private URI wrapWithUsernameAndPassword(GitRepository.GitRepositoryAccessData repositoryAccessData)
    {
        try
        {
            final String username = repositoryAccessData.username;
            final String password = repositoryAccessData.password;
            final boolean usePassword = repositoryAccessData.authenticationType == GitAuthenticationType.PASSWORD && StringUtils.isNotBlank(password);
            final String authority = StringUtils.isEmpty(username) ? null :
                                     usePassword ? (username + ":" + password) : username;

            URI remoteUri = new URI(repositoryAccessData.repositoryUrl);
            return new URI(remoteUri.getScheme(),
                           authority,
                           remoteUri.getHost(),
                           remoteUri.getPort(),
                           remoteUri.getPath(),
                           remoteUri.getQuery(),
                           remoteUri.getFragment());
        }
        catch (URISyntaxException e)
        {
            // can't really happen
            final String message = "Cannot parse remote URI: " + repositoryAccessData.repositoryUrl;
            NativeGitOperationHelper.log.error(message, e);
            throw new RuntimeException(e);
        }
    }

    protected void closeProxy(@NotNull final GitRepository.GitRepositoryAccessData accessData)
    {
        sshProxyService.unregister(accessData.proxyRegistrationInfo);
    }
}
