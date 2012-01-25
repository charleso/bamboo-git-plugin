package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.plan.branch.VcsBranchImpl;
import com.atlassian.testtools.ZipResourceDirectory;
import com.google.common.collect.Sets;
import org.testng.annotations.Test;

import java.io.File;

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

        assertEquals(gitRepository.getOpenBranches(), Sets.newHashSet(
                new VcsBranchImpl("Branch%with&stupid_chars"),
                new VcsBranchImpl("a_branch"),
                new VcsBranchImpl("master")
        ));
    }

}
