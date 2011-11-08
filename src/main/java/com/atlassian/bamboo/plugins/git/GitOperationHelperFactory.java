package com.atlassian.bamboo.plugins.git;


import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.ssh.SshProxyService;
import com.opensymphony.xwork.TextProvider;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GitOperationHelperFactory
{
    public static GitOperationHelper createGitOperationHelper(final @NotNull BuildLogger buildLogger,
                                                              final @NotNull SshProxyService sshProxyService,
                                                              final @NotNull TextProvider textProvider,
                                                              final @Nullable String gitCapability)
    {
        if (StringUtils.isNotBlank(gitCapability))
        {
            return new NativeGitOperationHelper(buildLogger, sshProxyService, textProvider, gitCapability);
        }
        else
        {
            return new JGitOperationHelper(buildLogger, sshProxyService, textProvider);
        }
    }
}
