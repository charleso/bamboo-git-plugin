package com.atlassian.bamboo.plugins.git.timeouts;


import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.build.logger.NullBuildLogger;
import com.atlassian.bamboo.plugins.git.GitAbstractTest;
import com.atlassian.bamboo.plugins.git.GitOperationHelper;
import org.testng.annotations.*;

import java.io.File;
import java.lang.reflect.Field;

/**
 * This test class is not intended to be run with other test classes - run it manually when solving timeout-related issues.
 */
@Test(enabled = false)
public class TimeoutsTest extends GitAbstractTest
{
    @BeforeClass
    public void setUp() throws Exception
    {
        Field timeout = GitOperationHelper.class.getDeclaredField("DEFAULT_TRANSFER_TIMEOUT");
        timeout.setAccessible(true);
        timeout.setInt(null, 1);
    }

    @Test
    public void testTimeoutIsSufficientToCheckOutBigRepo() throws Exception
    {
        BuildLogger bl = new NullBuildLogger()
        {
            @Override
            public String addBuildLogEntry(String logString)
            {
                System.out.println(logString);
                return null;
            }
        };
        GitOperationHelper helper = new GitOperationHelper(bl);
        String s = helper.obtainLatestRevision("git://git.jetbrains.org/idea/community.git", null, null, null);
        File directory = createTempDirectory();
        System.out.println(directory);
        helper.fetchAndCheckout(directory, "git://git.jetbrains.org/idea/community.git", null, s, null, null);
    }
}
