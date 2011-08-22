package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.plan.Plan;
import com.atlassian.bamboo.repository.RepositoryDefinition;
import com.atlassian.bamboo.v2.build.repository.RepositoryV2;
import com.atlassian.bamboo.ww2.actions.PlanActionSupport;
import com.atlassian.bamboo.ww2.aware.permissions.PlanEditSecurityAware;
import com.atlassian.util.concurrent.Supplier;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class DeleteGitCacheDirectoryAction extends PlanActionSupport implements PlanEditSecurityAware
{
    private static final Logger log = Logger.getLogger(DeleteGitCacheDirectoryAction.class);

    @Override
    public String doExecute() throws Exception
    {
        final String buildKey = getBuildKey();
        Plan plan = planManager.getPlanByKey(buildKey);
        if (plan == null)
        {
            String message = getText("repository.git.messages.cache.cleanFailedNoPlan", Arrays.asList(buildKey));
            log.error(message);
            addActionError(message);
            return ERROR;
        }

        boolean success = true;
        for (RepositoryDefinition repositoryDefinition : plan.getRepositoryDefinitions())
        {
            RepositoryV2 repository = repositoryDefinition.getRepository();
            if (!(repository instanceof GitRepository))
            {
                String message = getText("repository.git.messages.cache.notGit", Arrays.asList(buildKey));
                log.error(message);
                addActionError(message);
                return ERROR;
            }

            final GitRepository gitRepository = (GitRepository) repository;
            final File cacheDirectoryFile = gitRepository.getCacheDirectory();
            success = success && SUCCESS.equals(GitCacheDirectory.getCacheLock(cacheDirectoryFile).withLock(new Supplier<String>()
            {
                public String get()
                {
                    if (cacheDirectoryFile.exists())
                    {
                        log.info(getText("repository.git.messages.cache.cleaning", Arrays.asList(buildKey, cacheDirectoryFile.getAbsolutePath())));
                        try
                        {
                            FileUtils.forceDelete(cacheDirectoryFile);
                        }
                        catch (IOException e)
                        {
                            String message = getText("repository.git.messages.cache.cleanFailed", Arrays.asList(buildKey));
                            log.error(message, e);
                            addActionError(message);
                            return ERROR;
                        }
                    }
                    else
                    {
                        String message = getText("repository.git.messages.cache.notExist", Arrays.asList(buildKey, cacheDirectoryFile.getAbsolutePath()));
                        log.info(message);
                    }
                    return SUCCESS;
                }
            }));
        }
        return (success ? SUCCESS : ERROR);
    }
}
