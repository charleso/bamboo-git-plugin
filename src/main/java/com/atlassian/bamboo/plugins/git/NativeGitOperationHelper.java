package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.ssh.ProxyConnectionData;
import com.atlassian.bamboo.ssh.ProxyConnectionDataBuilder;
import com.atlassian.bamboo.ssh.ProxyException;
import com.atlassian.bamboo.ssh.SshProxyService;
import com.google.common.collect.ImmutableMap;
import com.opensymphony.xwork.TextProvider;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.eclipse.jgit.lib.Constants;
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
import java.util.List;

public class NativeGitOperationHelper extends GitOperationHelper
{
    @SuppressWarnings("UnusedDeclaration")
    private static final Logger log = Logger.getLogger(GitRepository.class);
    private static final String SSH_SCHEME = "ssh";
    private static final String GIT_SCHEME = "git";
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
        if (!containsSomethingToCommit(sourceDirectory))
        {
            log.debug("Nothing to commit");
            return getCurrentRevision(sourceDirectory);
        }

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
        final boolean needsProxy =
                accessData.authenticationType == GitAuthenticationType.SSH_KEYPAIR ||
                (isSsh(accessData.repositoryUrl) && accessData.authenticationType == GitAuthenticationType.PASSWORD);
        if (needsProxy)
        {
            GitRepository.GitRepositoryAccessData proxyAccessData = accessData.cloneAccessData();

            if (!StringUtils.contains(proxyAccessData.repositoryUrl, "://"))
            {
                proxyAccessData.repositoryUrl = SSH_SCHEME + "://" + proxyAccessData.repositoryUrl.replaceFirst(":", "/");
            }

            URI repositoryUri = URI.create(proxyAccessData.repositoryUrl);
            if (GIT_SCHEME.equals(repositoryUri.getScheme()) || SSH_SCHEME.equals(repositoryUri.getScheme()))
            {
                try
                {
                    String username = extractUsername(proxyAccessData.repositoryUrl);
                    if (username != null)
                    {
                        proxyAccessData.username = username;
                    }

                    final ProxyConnectionDataBuilder proxyConnectionDataBuilder = sshProxyService.createProxyConnectionDataBuilder()
                            .withRemoteAddress(repositoryUri.getHost(), repositoryUri.getPort() == -1 ? 22 : repositoryUri.getPort())
                            .withRemoteUserName(StringUtils.defaultIfEmpty(proxyAccessData.username, repositoryUri.getUserInfo()))
                            .withErrorReceiver(gitCommandProcessor);

                    switch (accessData.authenticationType)
                    {
                        case SSH_KEYPAIR:
                            proxyConnectionDataBuilder.withKeyFromString(proxyAccessData.sshKey, proxyAccessData.sshPassphrase);
                            break;
                        case PASSWORD:
                            proxyConnectionDataBuilder.withRemotePassword(StringUtils.defaultString(proxyAccessData.password));
                            break;
                        default:
                            throw new IllegalArgumentException("Proxy does not know how to handle " + accessData.authenticationType);
                    }

                    ProxyConnectionData connectionData = proxyConnectionDataBuilder.build();

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

    private boolean isSsh(final String repositoryUrl)
    {
        return repositoryUrl.startsWith(SSH_SCHEME + "://");
    }

    /**
     * @return true if modified files exist in the directory or current revision in the directory has changed
     */
    @Override
    public boolean merge(@NotNull final File workspaceDir, @NotNull final String targetRevision,
                         @NotNull String committerName, @NotNull String committerEmail) throws RepositoryException
    {
        GitCommandBuilder commandBuilder =
                gitCommandProcessor
                        .createCommandBuilder("merge", "--no-commit", targetRevision)
                        .env(identificationVariables(committerName, committerEmail));

        String headRevisionBeforeMerge = getCurrentRevision(workspaceDir);
        gitCommandProcessor.runMergeCommand(commandBuilder, workspaceDir);

        if (containsSomethingToCommit(workspaceDir))
        {
            return true;
        }
        //fast forward merge check
        String headRevisionAfterMerge = getCurrentRevision(workspaceDir);
        log.debug("Revision before merge: " + headRevisionBeforeMerge + ", after merge: " + headRevisionAfterMerge);
        return !headRevisionAfterMerge.equals(headRevisionBeforeMerge);
    }

    private boolean containsSomethingToCommit(@NotNull File workspaceDir) throws RepositoryException
    {
        //check for merge with no changes to files, but with changes to index
        final String mergeHead = getRevisionIfExists(workspaceDir, Constants.MERGE_HEAD);
        if (mergeHead!=null)
        {
            log.debug("Has modified index");
            return true;
        }

        final List<String> strings = gitCommandProcessor.runStatusCommand(workspaceDir);
        final boolean hasModifiedFiles = !strings.isEmpty();
        if (hasModifiedFiles)
        {
            log.debug("Has modified files");
        }
        return hasModifiedFiles;
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
            URI remoteUri = new URI(repositoryAccessData.repositoryUrl);
            return new URI(remoteUri.getScheme(),
                           getAuthority(repositoryAccessData),
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

    @Nullable
    private String getAuthority(final GitRepository.GitRepositoryAccessData repositoryAccessData) {
        final String username = repositoryAccessData.username;
        if (StringUtils.isEmpty(username))
        {
            return null;
        }

        String repositoryUrl = repositoryAccessData.repositoryUrl;
        
        final boolean passwordAuthentication = repositoryAccessData.authenticationType == GitAuthenticationType.PASSWORD;

        if (!passwordAuthentication || isSsh(repositoryUrl))
        {
            return username;
        }

        String password = repositoryAccessData.password;

        final boolean isHttpBased = repositoryUrl.startsWith("http://") || repositoryUrl.startsWith("https://");
        if (isHttpBased && StringUtils.isBlank(password))
        {
            password = "none"; //otherwise we'll get a password prompt
        }

        return StringUtils.isNotBlank(password) ? (username + ":" + password) : username;
    }

    protected void closeProxy(@NotNull final GitRepository.GitRepositoryAccessData accessData)
    {
        sshProxyService.unregister(accessData.proxyRegistrationInfo);
    }
}
