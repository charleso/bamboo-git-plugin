package com.atlassian.bamboo.plugins.git;


import com.atlassian.bamboo.plan.Plan;
import com.atlassian.bamboo.v2.build.repository.RepositoryV2;
import com.atlassian.bamboo.ww2.actions.PlanActionSupport;
import com.atlassian.bamboo.ww2.aware.permissions.PlanEditSecurityAware;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Arrays;

public class DeleteGitCacheDirectory extends PlanActionSupport implements PlanEditSecurityAware
{
    private static final Logger log = Logger.getLogger(DeleteGitCacheDirectory.class);

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
        RepositoryV2 repository = plan.getBuildDefinition().getRepositoryV2();
        if (!(repository instanceof GitRepository))
        {
            String message = getText("repository.git.messages.cache.notGit", Arrays.asList(buildKey));
            log.error(message);
            addActionError(message);
            return ERROR;
        }

        final GitRepository gitRepository = (GitRepository) repository;
        try
        {
            return GitCacheDirectory.callOnCacheWithLock(gitRepository.getCacheDirectory(), new AbstractGitCacheDirectoryOperation<String>()
            {
                @Override
                public String call(@NotNull File cacheDirectoryFile) throws Exception
                {
                    if (cacheDirectoryFile.exists())
                    {
                        String message = getText("repository.git.messages.cache.cleaning", Arrays.asList(buildKey, cacheDirectoryFile.getAbsolutePath()));
                        log.info(message);
                        FileUtils.forceDelete(cacheDirectoryFile); // will throw on error
                    }
                    else
                    {
                        String message = getText("repository.git.messages.cache.notExist", Arrays.asList(buildKey, cacheDirectoryFile.getAbsolutePath()));
                        log.info(message);
                    }
                    return SUCCESS;
                }
            });
        }
        catch (Exception e)
        {
            String message = getText("repository.git.messages.cache.cleanFailed", Arrays.asList(buildKey));
            log.error(message, e);
            addActionError(message);
            return ERROR;
        }
    }
}
