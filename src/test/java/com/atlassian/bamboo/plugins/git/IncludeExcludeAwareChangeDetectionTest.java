package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.build.BuildLoggerManager;
import com.atlassian.bamboo.build.logger.NullBuildLogger;
import com.atlassian.bamboo.plan.Plan;
import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.plan.vcsRevision.PlanVcsRevisionHistoryService;
import com.atlassian.bamboo.repository.IncludeExcludeAwareRepository;
import com.atlassian.bamboo.repository.RepositoryDefinition;
import com.atlassian.bamboo.v2.build.BuildRepositoryChanges;
import com.atlassian.bamboo.v2.trigger.DefaultChangeDetectionManager;
import com.atlassian.bamboo.variable.CustomVariableContextImpl;
import com.atlassian.bamboo.variable.VariableDefinitionContext;
import com.atlassian.bamboo.variable.VariableDefinitionManager;
import com.google.common.collect.Maps;
import com.opensymphony.xwork.DefaultTextProvider;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.NoMessageException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepository;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.answers.Returns;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class IncludeExcludeAwareChangeDetectionTest extends GitAbstractTest
{

    private static final Object[][] TEST_EXCLUDE_SEQUENCE = new Object[][]{
            {Arrays.asList("file1.txt"), true},
            {Arrays.asList("file_excluded.txt"), false},
            {Arrays.asList("file1.txt", "file_excluded.txt"), true},
    };

    private static final Object[][] TEST_INCLUDE_SEQUENCE = new Object[][]{
            {Arrays.asList("file1.txt"), false},
            {Arrays.asList("file_included.txt"), true},
            {Arrays.asList("file1.txt", "file_included.txt"), true},
            {Arrays.asList("file1.txt"), false},
    };

    @DataProvider
    Object[][] includeExcludeData()
    {
        return new Object[][] {
                {IncludeExcludeAwareRepository.FILTER_PATTERN_EXCLUDE, ".*excluded.*", TEST_EXCLUDE_SEQUENCE},
                {IncludeExcludeAwareRepository.FILTER_PATTERN_INCLUDE, ".*included.*", TEST_INCLUDE_SEQUENCE},
        };
    }
    
    @Test(dataProvider = "includeExcludeData")
    public void testIncludeExcludeSequence(String option, String pattern, Object[][] sequence) throws Exception
    {
        File localRepository = createTempDirectory();
        FileRepository repository = new FileRepository(new File(localRepository, Constants.DOT_GIT));
        repository.create(false);
        repository.close();
        String previousVcsRevisionKey = touchFiles(localRepository, Arrays.asList("file1.txt"));

        BuildLoggerManager mockBuildLoggerManager = Mockito.mock(BuildLoggerManager.class, new Returns(new NullBuildLogger()));

        CustomVariableContextImpl customVariableContext = new CustomVariableContextImpl();
        customVariableContext.setBuildLoggerManager(mockBuildLoggerManager);
        customVariableContext.setVariables(Maps.<String, VariableDefinitionContext>newHashMap());
        DefaultChangeDetectionManager changeDetectionManager = new DefaultChangeDetectionManager(
                mockBuildLoggerManager,
                DefaultTextProvider.INSTANCE,
                Mockito.mock(VariableDefinitionManager.class),
                customVariableContext,
                Mockito.mock(PlanVcsRevisionHistoryService.class)
        );
        GitRepository gitRepository = createGitRepository();
        setRepositoryProperties(gitRepository, localRepository);
        gitRepository.setFilterFilePatternOption(option);
        gitRepository.setFilterFilePatternRegex(pattern);
        gitRepository.setCustomVariableContext(customVariableContext);

        RepositoryDefinition repositoryDefinition = Mockito.mock(RepositoryDefinition.class);
        Mockito.when(repositoryDefinition.getRepository()).thenReturn(gitRepository);
        Mockito.when(repositoryDefinition.getId()).thenReturn((long) 1);

        for (Object[] objects : sequence)
        {
            final List<String> filesToTouch = (List<String>)objects[0];
            final boolean shouldTrigger = (Boolean)objects[1];

            String newRevision = touchFiles(localRepository, filesToTouch);

            Plan plan = Mockito.mock(Plan.class);
            Mockito.when(plan.getKey()).thenReturn(PLAN_KEY.getKey());
            Mockito.when(plan.getPlanKey()).thenReturn(PlanKeys.getPlanKey(PLAN_KEY.getKey()));
            BuildRepositoryChanges changes = changeDetectionManager.collectChangesSinceLastBuild(plan, repositoryDefinition, previousVcsRevisionKey, null);
            Assert.assertEquals(!changes.getChanges().isEmpty(), shouldTrigger, "Build should " + (shouldTrigger ? "" : "not ") + "trigger for " + option + " pattern '" + pattern + "' and files " + filesToTouch);
            previousVcsRevisionKey = newRevision;
        }
    }


    private String touchFiles(File localRepository, List<String> filesToTouch)
            throws IOException, NoFilepatternException, NoHeadException, NoMessageException, ConcurrentRefUpdateException, WrongRepositoryStateException
    {
        Git git = new Git(new FileRepository(new File(localRepository, Constants.DOT_GIT)));
        for (String file : filesToTouch)
        {
            new FileWriter(new File(localRepository, file), true).append("line\n").close();
        }
        git.add().addFilepattern(".").call();
        RevCommit revCommit = git.commit().setMessage("Committing " + filesToTouch).setCommitter("testUser", "testUser@testDomain").call();
        git.getRepository().close();
        return revCommit.name();
    }
}
