package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.build.BuildDefinition;
import com.atlassian.bamboo.plan.Plan;
import com.atlassian.bamboo.plan.PlanManager;
import com.atlassian.bamboo.variable.CustomVariableContextImpl;
import com.atlassian.bamboo.variable.CustomVariableContextThreadLocal;
import org.apache.commons.io.FileUtils;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.answers.Returns;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;

public class DeleteGitCacheDirectoryActionTest extends GitAbstractTest
{
    @Test
    public void testDeletingNonexistentDirectory() throws Exception
    {
        GitRepository repository = createGitRepository();
        setRepositoryProperties(repository, "repository.url", "");

        DeleteGitCacheDirectoryAction action = createDeleteAction(repository);

        Assert.assertEquals(action.doExecute(), "success");
    }

    @Test
    public void testDeletingExistingDirectory() throws Exception
    {
        GitRepository repository = createGitRepository();
        setRepositoryProperties(repository, "repository.url", "");
        File cache = repository.getCacheDirectory();
        cache.mkdirs();
        File someFile = new File(cache, "file.txt");
        FileUtils.writeStringToFile(someFile, "file content");

        Assert.assertTrue(cache.isDirectory(), "Precondition");
        Assert.assertTrue(someFile.exists(), "Precondition");

        DeleteGitCacheDirectoryAction action = createDeleteAction(repository);

        Assert.assertEquals(action.doExecute(), "success");
        Assert.assertFalse(cache.exists());
    }


    //"A test case that checks if cache for a plan is properly deleted if the repository url contains a variable."
    @Test
    public void testIfCacheIsProperlyDeletedWhenRepositoryUrlContainsVariable() throws Exception
    {
        CustomVariableContextImpl variableContext = new CustomVariableContextImpl();
        variableContext.addCustomData("variable", "value");
        CustomVariableContextThreadLocal.set(variableContext);

        GitRepository repository = createGitRepository();
        setRepositoryProperties(repository, "${bamboo.variable}");

        File cache = repository.getCacheDirectory();
        cache.mkdirs();
        File someFile = new File(cache, "file.txt");
        FileUtils.writeStringToFile(someFile,  "file content");

        Assert.assertTrue(cache.isDirectory(), "Precondition");
        Assert.assertTrue(someFile.exists(), "Precondition");

        DeleteGitCacheDirectoryAction action = createDeleteAction(repository);

        Assert.assertEquals(action.doExecute(), "success");
        Assert.assertFalse(cache.exists());
    }

    private static DeleteGitCacheDirectoryAction createDeleteAction(GitRepository repository)
    {
        DeleteGitCacheDirectoryAction action = new DeleteGitCacheDirectoryAction();
        action.setBuildKey("BUILD-KEY-1");
        BuildDefinition buildDefinition = Mockito.mock(BuildDefinition.class, new Returns(repository));

        Plan plan = Mockito.mock(Plan.class);
        Mockito.when(plan.getBuildDefinition()).thenReturn(buildDefinition);
        PlanManager planManager = Mockito.mock(PlanManager.class);
        Mockito.when(planManager.getPlanByKey(action.getBuildKey())).thenReturn(plan);
        action.setPlanManager(planManager);
        return action;
    }
}
