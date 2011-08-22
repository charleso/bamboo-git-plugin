package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.plugins.git.testutils.ExtractComments;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.BuildRepositoryChanges;
import com.atlassian.testtools.ZipResourceDirectory;
import com.google.common.collect.Lists;
import org.apache.commons.io.FileUtils;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static java.util.Arrays.asList;

public class HandlingSwitchingRepositoriesToUnrelatedOnesTest extends GitAbstractTest
{
    // multiple branches repo
    private static final String CHG_M_4 = "2c389a83e43c140eae2fddb9d794f79af6124214";

    // detached head repo
    private static final String CHG_1 = "2dc6e849cae7ff78e93179cfe04e62671807109d";
    private static final String CHG_5 = "f22d51f401b7d6ff22f6b1c611b95ebe757e47bf";

    private File sourceRepositoriesBaseHeads;
    private File sourceRepositoriesBaseDetached;

    @BeforeClass
    public void setUpTmpDirWithRepository() throws Exception
    {
        sourceRepositoriesBaseHeads = createTempDirectory();
        ZipResourceDirectory.copyZipResourceToDirectory("multiple-branches-with-conflicts.zip", sourceRepositoriesBaseHeads);
        sourceRepositoriesBaseDetached = createTempDirectory();
        ZipResourceDirectory.copyZipResourceToDirectory("detached-git-repos.zip", sourceRepositoriesBaseDetached);
    }

    @DataProvider
    Object[][] unrelatedPreviousRevisionsData()
    {
        return new String[][] {
                {"123456"},
                {CHG_5},
        };
    }

    @Test(dataProvider = "unrelatedPreviousRevisionsData")
    public void testCollectChangesWithUnrelatedPreviousRevision(String previousChangeset) throws Exception
    {
        File source = new File(sourceRepositoriesBaseHeads, "5");
        GitRepository gitRepository = createGitRepository();
        setRepositoryProperties(gitRepository, source);

        BuildRepositoryChanges changes = gitRepository.collectChangesSinceLastBuild(PLAN_KEY.getKey(), previousChangeset);
        String vcsRevisionKey = changes.getVcsRevisionKey();

        Assert.assertEquals(vcsRevisionKey, CHG_M_4);
        List<String> comments = Lists.transform(changes.getChanges(), new ExtractComments());
        Assert.assertEquals(comments, Arrays.asList(getTextProvider().getText("repository.git.messages.unknownChanges", asList(previousChangeset, CHG_M_4))));
    }

    @Test
    public void testCollectChangesWithUnrelatedPreviousRevisionUsingCache() throws Exception
    {
        // This scenario is not very likely to happen - different repo would need to appear at the same URL
        File commonSource = createTempDirectory();
        GitRepository gitRepository = createGitRepository();
        setRepositoryProperties(gitRepository, commonSource);

        FileUtils.copyDirectory(new File(sourceRepositoriesBaseDetached, "5"), commonSource);
        BuildRepositoryChanges changes = gitRepository.collectChangesSinceLastBuild(PLAN_KEY.getKey(), CHG_1); // feed cache
        String vcsRevisionKey = changes.getVcsRevisionKey();
        Assert.assertEquals(vcsRevisionKey, CHG_5, "Precondition");

        FileUtils.cleanDirectory(commonSource);
        FileUtils.copyDirectory(new File(sourceRepositoriesBaseHeads, "5"), commonSource);

        BuildRepositoryChanges newChanges = gitRepository.collectChangesSinceLastBuild(PLAN_KEY.getKey(), CHG_5); // now unrelated
        String newVcsRevisionKey = newChanges.getVcsRevisionKey();
        Assert.assertEquals(newVcsRevisionKey, CHG_M_4);

        // we don't know that the previous changeset is really unrelated, we treat it as being on a very very unrelated branch
//        List<String> comments = Lists.transform(changes.getChanges(), new ExtractComments());
//        Assert.assertEquals(comments, Arrays.asList(getTextProvider().getText("repository.git.messages.unknownChanges", asList(CHG_5, CHG_M_4))));
    }

    @Test
    public void testSourceCheckoutOverUnrelatedSources() throws Exception
    {
        GitRepository gitRepository = createGitRepository();
        setRepositoryProperties(gitRepository, new File(sourceRepositoriesBaseDetached, "5"));

        BuildContext buildContext = Mockito.mock(BuildContext.class);
        Mockito.when(buildContext.getPlanKey()).thenReturn(PLAN_KEY.getKey());

        gitRepository.retrieveSourceCode(buildContext, CHG_5);
        verifyContents(gitRepository.getSourceCodeDirectory(PLAN_KEY), "detached-git-repos-contents/5.zip"); // precondition

        setRepositoryProperties(gitRepository, new File(sourceRepositoriesBaseHeads, "5"));
        String retrieved = gitRepository.retrieveSourceCode(buildContext, CHG_M_4);

        Assert.assertEquals(retrieved, CHG_M_4);
        verifyContents(gitRepository.getSourceCodeDirectory(PLAN_KEY), "multiple-branches-with-conflicts-contents/master-5.zip");
    }
}
