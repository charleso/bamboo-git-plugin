package com.atlassian.bamboo.plugins.git;

import com.atlassian.testtools.ZipResourceDirectory;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;

import static org.testng.Assert.assertEquals;

public class GitOperationHelperTest extends GitAbstractTest
{

    @DataProvider(parallel = false)
    Object[][] testObtainLatestRevisionData()
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

    @Test(dataProvider = "testObtainLatestRevisionData")
    public void testObtainLatestRevision(String zipFile, String branch, String expectedRevision) throws Exception
    {
        File repository = createTempDirectory();
        ZipResourceDirectory.copyZipResourceToDirectory(zipFile, repository);

        String result = new GitOperationHelper().obtainLatestRevision(repository.getAbsolutePath(), branch, null, null);
        assertEquals(result, expectedRevision);
    }

    @DataProvider(parallel = false)
        Object[][] testCheckoutData()
        {
            return new Object[][]{
                    {"basic-repository.zip", new String[][]{{"55676cfa3db13bcf659b2a35e5d61eba478ed54d", "basic-repo-contents-55676cfa3db13bcf659b2a35e5d61eba478ed54d.zip"}}},
                    {"basic-repository.zip", new String[][]{{"2e20b0733759facbeb0dec6ee345d762dbc8eed8", "basic-repo-contents-2e20b0733759facbeb0dec6ee345d762dbc8eed8.zip"}}},
                    {"basic-repository.zip", new String[][]{{"a26ff19c3c63e19d6a57a396c764b140f48c530a", "basic-repo-contents-a26ff19c3c63e19d6a57a396c764b140f48c530a.zip"}}},
                    {"basic-repository.zip", new String[][]{
                                    {"55676cfa3db13bcf659b2a35e5d61eba478ed54d", "basic-repo-contents-55676cfa3db13bcf659b2a35e5d61eba478ed54d.zip"},
                                    {"2e20b0733759facbeb0dec6ee345d762dbc8eed8", "basic-repo-contents-2e20b0733759facbeb0dec6ee345d762dbc8eed8.zip"},
                                    {"a26ff19c3c63e19d6a57a396c764b140f48c530a", "basic-repo-contents-a26ff19c3c63e19d6a57a396c764b140f48c530a.zip"},
                                    {"a26ff19c3c63e19d6a57a396c764b140f48c530a", "basic-repo-contents-a26ff19c3c63e19d6a57a396c764b140f48c530a.zip"},
                                    {"2e20b0733759facbeb0dec6ee345d762dbc8eed8", "basic-repo-contents-2e20b0733759facbeb0dec6ee345d762dbc8eed8.zip"},
                                    {"55676cfa3db13bcf659b2a35e5d61eba478ed54d", "basic-repo-contents-55676cfa3db13bcf659b2a35e5d61eba478ed54d.zip"},
                            },
                    }
            };
        }


    @Test(dataProvider = "testCheckoutData")
    public void testCheckout(String repositoryZip, String[][] targetRevisions) throws Exception
    {
        File tmp = createTempDirectory();
        ZipResourceDirectory.copyZipResourceToDirectory(repositoryZip, tmp);

        String previousRevision = null;
        for(String[] testCase : targetRevisions)
        {
            String targetRevision = testCase[0];
            String expectedContentsInZip = testCase[1];
            String result = new GitOperationHelper().checkout(tmp, targetRevision, previousRevision);

            assertEquals(result, targetRevision);
            verifyContents(tmp, expectedContentsInZip);

            previousRevision = result;
        }
    }

}
