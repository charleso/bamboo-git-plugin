package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.build.fileserver.BuildDirectoryManager;
import com.atlassian.bamboo.plan.Plan;
import com.atlassian.bamboo.plan.PlanManager;
import com.atlassian.bamboo.repository.CacheDescription;
import com.atlassian.bamboo.repository.Repository;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.opensymphony.xwork.TextProvider;
import com.opensymphony.xwork.ValidationAware;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * @see com.atlassian.bamboo.repository.CacheHandler
 */
@SuppressWarnings({"JavaDoc"})
public class GitCacheHandler
{
    private PlanManager planManager;
    private BuildDirectoryManager buildDirectoryManager;
    private TextProvider textProvider;

    /**
     * @see com.atlassian.bamboo.repository.CacheHandler#getCacheDescriptions()
     */
    @NotNull
    public Collection<CacheDescription> getCacheDescriptions()
    {
        Multimap<File, Plan> planMap = mapCachesToPlans();

        Collection<CacheDescription> cacheDescriptions = Lists.newArrayList();


        for (Map.Entry<File, Collection<Plan>> cacheMapping : planMap.asMap().entrySet())
        {
            File cacheDir = cacheMapping.getKey();
            Collection<Plan> usingPlans = cacheMapping.getValue();

            Plan firstOne = usingPlans.iterator().next();
            GitRepository repository = (GitRepository) firstOne.getBuildDefinition().getRepository();
            // mapCachesToPlans ensures that we get only non-null GitRepositories here
            //noinspection ConstantConditions
            cacheDescriptions.add(createCacheDescription(repository, cacheDir, usingPlans));
        }

        Set<File> unusedDirs = findUnusedCaches(planMap.keySet());
        for (File unusedDir : unusedDirs)
        {
            String description = "Descriptions for unused caches is unsupported";
            CacheDescription cacheDescription = new CacheDescription.FileBased(unusedDir, description, Collections.<Plan>emptyList());
            cacheDescriptions.add(cacheDescription);
        }
        return cacheDescriptions;
    }

    @NotNull
    public CacheDescription getCacheDescription(@NotNull GitRepository gitRepository)
    {
        File cacheDirectory = gitRepository.getCacheDirectory();
        Multimap<File, Plan> planMap = mapCachesToPlans();
        Collection<Plan> usingPlans = planMap.get(cacheDirectory);

        return createCacheDescription(gitRepository, cacheDirectory, usingPlans);
    }

    @NotNull
    private static CacheDescription createCacheDescription(@NotNull GitRepository repository, @NotNull File cacheDir, @NotNull Collection<Plan> usingPlans)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("URL: '").append(repository.accessData.repositoryUrl).append('\'');
        if (StringUtils.isNotBlank(repository.accessData.username))
        {
            sb.append(", Username: '").append(repository.accessData.username).append('\'');
        }

        final String description = sb.toString();

        return new CacheDescription.FileBased(cacheDir, description, usingPlans);
    }

    /**
     * @see com.atlassian.bamboo.repository.CacheHandler#deleteCaches(java.util.Collection, com.opensymphony.xwork.ValidationAware)
     */
    public void deleteCaches(@NotNull Collection<String> keys, @NotNull ValidationAware feedback)
    {
        if (keys.isEmpty())
        {
            feedback.addActionMessage(textProvider.getText("manageCaches.delete.git.nothingToDelete"));
            return;
        }

        File cacheRootDir = new File(buildDirectoryManager.getBaseBuildWorkingDirectory(), GitCacheDirectory.GIT_REPOSITORY_CACHE_DIRECTORY);
        for (String key : keys)
        {
            File cacheCandidate = new File(cacheRootDir, key);

            if (cacheCandidate.exists())
            {
                try
                {
                    FileUtils.forceDelete(cacheCandidate);
                    feedback.addActionMessage(textProvider.getText("manageCaches.delete.git.success", Arrays.asList(key)));
                }
                catch (IOException e)
                {
                    feedback.addActionError(textProvider.getText("manageCaches.delete.git.failed", Arrays.asList(key, e.getLocalizedMessage())));
                }
            }
            else
            {
                feedback.addActionMessage(textProvider.getText("manageCaches.delete.git.skipped", Arrays.asList(key)));
            }
        }
    }

    /**
     * @see com.atlassian.bamboo.repository.CacheHandler#deleteUnusedCaches(com.opensymphony.xwork.ValidationAware)
     */
    public void deleteUnusedCaches(@NotNull final ValidationAware feedback)
    {
        Set<File> usedCaches = mapCachesToPlans().keySet();
        Set<File> unusedCacheDirs = findUnusedCaches(usedCaches);
        for (File unusedCacheDir : unusedCacheDirs)
        {
            String sha = unusedCacheDir.getName();
            try
            {
                FileUtils.deleteDirectory(unusedCacheDir);
                feedback.addActionMessage(textProvider.getText("manageCaches.delete.git.unused.success", Arrays.asList(sha)));
            }
            catch (IOException e)
            {
                feedback.addActionError(textProvider.getText("manageCaches.delete.git.unused.failed", Arrays.asList(sha, e.getLocalizedMessage())));
            }
        }
    }

    @NotNull
    Multimap<File, Plan> mapCachesToPlans()
    {
        Multimap<File, Plan> map = HashMultimap.create();

        for (Plan plan : planManager.getAllPlans(Plan.class))
        {
            Repository repository = plan.getBuildDefinition().getRepository();
            if (!plan.getBuildDefinition().isInheritRepository() && repository instanceof GitRepository)
            {
                GitRepository gitRepository = (GitRepository) repository;
                map.put(gitRepository.getCacheDirectory(), plan);
            }
        }

        return map;
    }

    @NotNull
    private Set<File> findUnusedCaches(@NotNull Set<File> usedCaches)
    {
        File cacheRootDir = new File(buildDirectoryManager.getBaseBuildWorkingDirectory(), GitCacheDirectory.GIT_REPOSITORY_CACHE_DIRECTORY);
        File[] cacheDirs = cacheRootDir.listFiles((FileFilter) DirectoryFileFilter.DIRECTORY); // will be null if cacheRootDir does not exist
        return ArrayUtils.isEmpty(cacheDirs) ? Collections.<File>emptySet() : Sets.difference(ImmutableSet.of(cacheDirs), usedCaches);
    }

    public void setPlanManager(PlanManager planManager)
    {
        this.planManager = planManager;
    }

    public void setBuildDirectoryManager(BuildDirectoryManager buildDirectoryManager)
    {
        this.buildDirectoryManager = buildDirectoryManager;
    }

    public void setTextProvider(TextProvider textProvider)
    {
        this.textProvider = textProvider;
    }
}
