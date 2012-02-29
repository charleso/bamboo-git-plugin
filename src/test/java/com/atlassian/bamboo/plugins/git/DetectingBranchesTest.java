package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.plan.branch.VcsBranch;
import com.atlassian.bamboo.plan.branch.VcsBranchImpl;
import com.atlassian.testtools.ZipResourceDirectory;
import com.google.common.collect.Lists;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.util.List;

import static org.testng.Assert.assertEquals;

public class DetectingBranchesTest extends GitAbstractTest
{
    @Test
    public void testDetectingBranches() throws Exception
    {
        File testRepository = createTempDirectory();
        ZipResourceDirectory.copyZipResourceToDirectory("detect-branches/detect-branches-repo.zip", testRepository);

        GitRepository gitRepository = createGitRepository();
        setRepositoryProperties(gitRepository, testRepository);

        List<VcsBranch> expectedBranches = Lists.<VcsBranch>newArrayList(
                new VcsBranchImpl("Branch%with&stupid_chars"),
                new VcsBranchImpl("a_branch"),
                new VcsBranchImpl("master")
        );

        List<VcsBranch> openBranches = gitRepository.getOpenBranches();

        Assert.assertEquals(openBranches.size(), expectedBranches.size());
        for (VcsBranch expected : expectedBranches)
        {
            Assert.assertTrue(openBranches.contains(expected));
        }
    }

}
