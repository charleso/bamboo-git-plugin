package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.repository.RepositoryException;
import com.opensymphony.xwork.TextProvider;
import org.apache.log4j.Logger;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.TagOpt;
import org.eclipse.jgit.transport.Transport;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class JGitOperationStrategy implements GitOperationStrategy
{
    private static final Logger log = Logger.getLogger(JGitOperationStrategy.class);

    private final BuildLogger buildLogger;
    private final TextProvider textProvider;

    public JGitOperationStrategy(final BuildLogger buildLogger, final TextProvider textProvider)
    {
        this.buildLogger = buildLogger;
        this.textProvider = textProvider;
    }

    @Override
    public void fetch(@NotNull final File sourceDirectory, @NotNull final GitRepository.GitRepositoryAccessData accessData, RefSpec refSpec, final boolean useShallow) throws RepositoryException
    {
        // temporarily make it fail on jgit use
        Transport transport = null;
        String branchDescription = "(unresolved) " + accessData.branch;
        try
        {
            transport.setTagOpt(TagOpt.AUTO_FOLLOW);

            FetchResult fetchResult = transport.fetch(new BuildLoggerProgressMonitor(buildLogger), Arrays.asList(refSpec), useShallow ? 1 : 0);
            buildLogger.addBuildLogEntry("Git: " + fetchResult.getMessages());
        }
        catch (IOException e)
        {
            String message = textProvider.getText("repository.git.messages.fetchingFailed", Arrays.asList(accessData.repositoryUrl, branchDescription, sourceDirectory));
            throw new RepositoryException(buildLogger.addErrorLogEntry(message + " " + e.getMessage()), e);
        }
        finally
        {
            if (transport != null)
            {
                transport.close();
            }
        }
    }
}
