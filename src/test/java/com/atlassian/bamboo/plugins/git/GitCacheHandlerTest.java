package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.build.AbstractBuildable;
import com.atlassian.bamboo.build.BuildDefinition;
import com.atlassian.bamboo.build.fileserver.BuildDirectoryManager;
import com.atlassian.bamboo.plan.Plan;
import com.atlassian.bamboo.plan.PlanManager;
import com.atlassian.bamboo.repository.CacheDescription;
import com.atlassian.bamboo.repository.Repository;
import com.atlassian.bamboo.v2.build.agent.messages.RemoteBambooMessage;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.opensymphony.xwork.TextProvider;
import com.opensymphony.xwork.ValidationAware;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.answers.Returns;
import org.mockito.internal.stubbing.defaultanswers.ReturnsMocks;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Test(singleThreaded = true)
public class GitCacheHandlerTest extends GitAbstractTest
{
    private static final String URL1 = "git://some.url";
    private static final String URL2 = "git://some.other.url";
    private static final String URL3 = "git://some.other.url2";
    private static final String URL4 = "git://some.unused.url";
    private File tmpDir;
    private File cache1;
    private File cache2;
    private File cache3;
    private File cache4;

    @BeforeMethod
    protected void setUp() throws Exception
    {
        tmpDir = createTempDirectory();

        cache1 = GitCacheDirectory.getCacheDirectory(tmpDir, createAccessData(URL1));
        cache2 = GitCacheDirectory.getCacheDirectory(tmpDir, createAccessData(URL2));
        cache3 = GitCacheDirectory.getCacheDirectory(tmpDir, createAccessData(URL3));
        cache4 = GitCacheDirectory.getCacheDirectory(tmpDir, createAccessData(URL4));
    }

    @Test
    public void testNonExistentRootDir() throws Exception
    {
        GitCacheHandler provider = new GitCacheHandler();

        BuildDirectoryManager directoryManager = Mockito.mock(BuildDirectoryManager.class, new Returns(new File(tmpDir, "non-existent-subdir")));
        provider.setBuildDirectoryManager(directoryManager);
        provider.setPlanManager(Mockito.mock(PlanManager.class, new ReturnsMocks()));

        Collection<CacheDescription> cacheDescriptions = provider.getCacheDescriptions();
        Assert.assertEquals(cacheDescriptions, Collections.<Object>emptyList());
    }

    @Test
    public void testMappingDirsToRepos() throws Exception
    {

        GitCacheHandler provider = createInitializedProvider();

        Multimap<File,Plan> planMap = provider.mapCachesToPlans();

        FileUtils.deleteQuietly(tmpDir);

        Assert.assertEquals(planMap.keySet(), ImmutableSet.of(cache1, cache2, cache3));

        verifyPlanList(URL1, 2, planMap.get(cache1));
        verifyPlanList(URL2, 1, planMap.get(cache2));
        verifyPlanList(URL3, 1, planMap.get(cache3));
    }

    @Test
    public void testGettingDescriptions() throws Exception
    {
        GitCacheHandler provider = createInitializedProvider();

        FileUtils.forceMkdir(cache1);
        FileUtils.forceMkdir(cache4);

        Collection<CacheDescription> cacheDescriptions = provider.getCacheDescriptions();
        Assert.assertEquals(cacheDescriptions.size(), 4);

        Map<String, CacheDescription> indexed = Maps.uniqueIndex(cacheDescriptions, new Function<CacheDescription, String>()
        {
            public String apply(CacheDescription from)
            {
                return from.getKey();
            }
        });

        verifyDescription(cache1, indexed.get(cache1.getName()), true, URL1, 2);

        verifyDescription(cache2, indexed.get(cache2.getName()), false, URL2, 1);
        verifyDescription(cache3, indexed.get(cache3.getName()), false, URL3, 1);

        verifyDescription(cache4, indexed.get(cache4.getName()), true, null, 0);
    }

    private void verifyDescription(File c1, CacheDescription cacheDescription, boolean exists, String url, int expectedCount)
    {
        CacheDescription d1 = cacheDescription;
        if (url != null)
        {
            Assert.assertEquals(d1.getDescription(), "URL: '" + url + "'");
        }
        Assert.assertEquals(d1.getLocation(), c1.getAbsolutePath());
        Assert.assertEquals(d1.isExists(), exists);
        Collection<Plan> p1 = d1.getUsingPlans();
        verifyPlanList(url, expectedCount, p1);
    }

    private void verifyPlanList(String url, int expectedCount, Collection<Plan> plans)
    {
        for (Plan plan : plans)
        {
            Assert.assertEquals(((GitRepository) plan.getBuildDefinition().getRepository()).getRepositoryUrl(), url);
        }
        Assert.assertEquals(plans.size(), expectedCount);
    }

    @DataProvider
    Object[][] descriptionForRepositoryData()
    {
        return new Object[][] {
                {URL1, 2},
                {URL2, 1},
                {URL3, 1},
        };
    }
    @Test(dataProvider = "descriptionForRepositoryData")
    public void testGettingDescriptionForRepository(String url, int expectedCount) throws Exception
    {
        GitCacheHandler provider = createInitializedProvider();
        GitRepository repository = createGitRepository(url);
        CacheDescription cacheDescription = provider.getCacheDescription(repository);

        verifyDescription(repository.getCacheDirectory(), cacheDescription, false, url, expectedCount);
    }

    @DataProvider
    Object[][] otherPlansData()
    {
        return new Object[][] {
                {"UNRELATED", URL1, 2},
                {"UNRELATED", URL2, 1},
                {"UNRELATED", URL3, 1},
                {"UNRELATED", "http://different.url", 0},

                {"AUTO1", URL1, 1},
                {"AUTO2", URL1, 1},
                {"AUTO3", URL1, 2},

                {"AUTO4", URL2, 0},
                {"AUTO5", URL2, 1},

                {"AUTO6", URL3, 0},

        };
    }
    @Test(dataProvider = "otherPlansData")
    public void testGettingOtherPlansForRepository(String currentKey, String url, int expectedCount) throws Exception
    {
        GitCacheHandler provider = createInitializedProvider();
        
        GitRepository repository = createGitRepository(url);
        Plan current = createPlan(currentKey, repository, false);
        repository.setGitCacheHandler(provider);

        List<Plan> plans = repository.getOtherPlansSharingCache(current);
        verifyPlanList(url, expectedCount, plans);
    }

    @Test
    public void testDeletingUnusedDirs() throws Exception
    {
        GitCacheHandler provider = createInitializedProvider();
        File cache5 = GitCacheDirectory.getCacheDirectory(tmpDir, createAccessData("http://yet.another.url"));
        FileUtils.forceMkdir(cache1);
        FileUtils.forceMkdir(cache2);
        FileUtils.forceMkdir(cache4);
        FileUtils.forceMkdir(cache5);

        provider.deleteUnusedCaches(Mockito.mock(ValidationAware.class));

        Assert.assertTrue(cache1.exists());
        Assert.assertTrue(cache2.exists());
        Assert.assertFalse(cache4.exists());
        Assert.assertFalse(cache5.exists());
    }

    @Test
    public void testDeletingSpecificDirs() throws Exception
    {
        GitCacheHandler provider = createInitializedProvider();
        File cache5 = GitCacheDirectory.getCacheDirectory(tmpDir, createAccessData("http://yet.another.url"));
        FileUtils.forceMkdir(cache1);
        FileUtils.forceMkdir(cache2);
        FileUtils.forceMkdir(cache4);
        FileUtils.forceMkdir(cache5);

        Set<String> toDelete = ImmutableSet.of(cache1.getName(), cache3.getName(), cache4.getName());
        provider.deleteCaches(toDelete, Mockito.mock(ValidationAware.class));

        Assert.assertFalse(cache1.exists());
        Assert.assertTrue(cache2.exists());
        Assert.assertFalse(cache3.exists());
        Assert.assertFalse(cache4.exists());
        Assert.assertTrue(cache5.exists());
    }

    private GitCacheHandler createInitializedProvider() throws Exception
    {
        int cnt = 0;
        List<Plan> allPlans = Lists.newArrayList();
        allPlans.add(createPlan("AUTO" + ++cnt, URL1, false));
        allPlans.add(createPlan("AUTO" + ++cnt, URL1, false));
        allPlans.add(createPlan("AUTO" + ++cnt, URL1, true));
        allPlans.add(createPlan("AUTO" + ++cnt, URL2, false));
        allPlans.add(createPlan("AUTO" + ++cnt, URL2, true));
        allPlans.add(createPlan("AUTO" + ++cnt, URL3, false));
        allPlans.add(createPlan("AUTO" + ++cnt, "http://some.inherited.url", true));

        allPlans.add(createPlan("AUTO" + ++cnt, Mockito.mock(Repository.class), false));

        GitCacheHandler provider = new GitCacheHandler();

        BuildDirectoryManager directoryManager = Mockito.mock(BuildDirectoryManager.class, new Returns(tmpDir));
        provider.setBuildDirectoryManager(directoryManager);
        PlanManager planManager = Mockito.mock(PlanManager.class);
        Mockito.when(planManager.getAllPlans(Plan.class)).thenReturn(allPlans);
        provider.setPlanManager(planManager);
        provider.setTextProvider(Mockito.mock(TextProvider.class));
        return provider;
    }

    Plan createPlan(String key, String url, boolean inherited) throws Exception
    {
        GitRepository repository = createGitRepository(url);

        return createPlan(key, repository, inherited);
    }

    private GitRepository createGitRepository(String url) throws Exception
    {
        GitRepository repository = createGitRepository(tmpDir);
        setRepositoryProperties(repository, url);
        return repository;
    }

    Plan createPlan(final String key, Repository repository, boolean inherited)
    {
        final BuildDefinition buildDefinition = Mockito.mock(BuildDefinition.class);
        Mockito.when(buildDefinition.getRepository()).thenReturn(repository);
        Mockito.when(buildDefinition.isInheritRepository()).thenReturn(inherited);

        return new AbstractBuildable()
        {
            @NotNull
            public String getType()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getKey()
            {
                return key;
            }

            @NotNull
            @Override
            public BuildDefinition getBuildDefinition()
            {
                return buildDefinition;
            }
        };
    }
    
}
