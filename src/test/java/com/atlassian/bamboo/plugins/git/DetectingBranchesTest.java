package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.build.branches.VcsBranch;
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
                new VcsBranch("Branch%with&stupid_chars"),
                new VcsBranch("a_branch"),
                new VcsBranch("master")
        ));
    }

}
