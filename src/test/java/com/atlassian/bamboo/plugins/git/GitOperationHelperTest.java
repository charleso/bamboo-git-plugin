package com.atlassian.bamboo.plugins.git;

import com.atlassian.testtools.ZipResourceDirectory;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.mockito.Mockito;

import java.io.File;

import static org.testng.Assert.assertEquals;

public class GitOperationHelperTest extends GitAbstractTest
{

    @DataProvider(parallel = false)
    Object[][] obtainLatestRevisionData()
    {
        return new Object[][]{
                {"obtainLatestRevision/1.zip", "HEAD",         "b91edd07dfd908cada0f4279aafe66c3beafc114"},
                {"obtainLatestRevision/2.zip", "HEAD",         "41455a21cda3002b40abe8f4d8940e6d304d4dee"},
                {"obtainLatestRevision/3.zip", "HEAD",         "3f90c11e650a33cb4ea31b958941082d1f4b8a69"},
                {"basic-repository.zip",       null,           "a26ff19c3c63e19d6a57a396c764b140f48c530a"},
                {"basic-repository.zip",       "HEAD",         "a26ff19c3c63e19d6a57a396c764b140f48c530a"},
                {"basic-repository.zip",       "master",       "a26ff19c3c63e19d6a57a396c764b140f48c530a"},
                {"basic-repository.zip",       "myBranch",     "4367e71d438f091a5e85304618a8f78f9db6738e"},
                {"basic-repository.zip",       "secondBranch", "f15f4c4a5881a2fdfa8b153dc377a081685e1d24"},
        };
    }

    @Test(dataProvider = "obtainLatestRevisionData")
    public void testObtainLatestRevision(String zipFile, String branch, String expectedRevision) throws Exception
    {
        File repository = createTempDirectory();
        ZipResourceDirectory.copyZipResourceToDirectory(zipFile, repository);

        String result = new GitOperationHelper().obtainLatestRevision(repository.getAbsolutePath(), branch, null, null);
        assertEquals(result, expectedRevision);
    }
}
