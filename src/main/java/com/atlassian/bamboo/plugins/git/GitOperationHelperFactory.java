package com.atlassian.bamboo.plugins.git;


import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.ssh.SshProxyService;
import com.atlassian.config.HomeLocator;
import com.opensymphony.xwork.TextProvider;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.net.URISyntaxException;

public class GitOperationHelperFactory
{
    public static GitOperationHelper createGitOperationHelper(final @NotNull GitRepository repository,
                                                              final @NotNull GitRepository.GitRepositoryAccessData accessData,
                                                              final @NotNull SshProxyService sshProxyService,
                                                              final @NotNull BuildLogger buildLogger,
                                                              final @NotNull TextProvider textProvider,
                                                              final @NotNull HomeLocator homeLocator) throws RepositoryException, URISyntaxException
    {
        if (StringUtils.isNotBlank(repository.getGitCapability()))
        {
            return new NativeGitOperationHelper(repository, accessData, sshProxyService, buildLogger, textProvider, homeLocator, repository.getGitCapability());
        }
        else
        {
            return new JGitOperationHelper(buildLogger, sshProxyService, textProvider);
        }
    }
}
