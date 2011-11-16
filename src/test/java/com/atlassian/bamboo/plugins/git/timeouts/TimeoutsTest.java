package com.atlassian.bamboo.plugins.git.timeouts;


import com.atlassian.bamboo.build.logger.NullBuildLogger;
import com.atlassian.bamboo.plugins.git.GitAbstractTest;
import com.atlassian.bamboo.plugins.git.GitOperationHelper;
import com.atlassian.bamboo.plugins.git.JGitOperationHelper;
import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.ssh.SshProxyService;
import com.opensymphony.xwork.TextProvider;
import org.mockito.Mockito;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import static org.mockito.Mockito.mock;

/**
 * This test class is not intended to be run with other test classes - run it manually when solving timeout-related issues.
 */
@Test(enabled = false, groups = "manual")
public class TimeoutsTest extends GitAbstractTest
{
    private Thread servingThread;
    private ServerSocket serverSocket;
    private Collection<Socket> connectedSockets = Collections.synchronizedCollection(new ArrayList<Socket>());

    @BeforeClass
    public void setUp() throws Exception
    {
        Field timeout = GitOperationHelper.class.getDeclaredField("DEFAULT_TRANSFER_TIMEOUT");
        timeout.setAccessible(true);
        timeout.setInt(null, 1);

        serverSocket = new ServerSocket(0);

        servingThread = new Thread()
        {
            @Override
            public void run()
            {
                try
                {
                    for (;;)
                    {
                        connectedSockets.add(serverSocket.accept());
                    }
                }
                catch (IOException e)
                {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
            }
        };
        servingThread.start();
    }

    @AfterClass
    public void tearDown() throws Exception
    {
        servingThread.stop();
        serverSocket.close();
        for (Socket connectedSocket : connectedSockets)
        {
            connectedSocket.close();
        }
    }

    public GitOperationHelper createGitOperationHelper()
    {
        TextProvider textProvider = mock(TextProvider.class);
        SshProxyService sshProxyService = null; //new SshProxyServiceImpl(textProvider);

        return new JGitOperationHelper(
            new NullBuildLogger()
            {
                @Override
                public String addBuildLogEntry(String logString)
                {
                    System.out.println(logString);
                    return null;
                }
            },
            sshProxyService,
            textProvider);
    }

    @Test
    public void testTimeoutIsSufficientToCheckOutBigRepo() throws Exception
    {
        GitOperationHelper helper = createGitOperationHelper();
        String s = helper.obtainLatestRevision(createAccessData("git://git.jetbrains.org/idea/community.git"));
        File directory = createTempDirectory();
        System.out.println(directory);
        helper.fetch(directory, createAccessData("git://git.jetbrains.org/idea/community.git"), false);
        helper.checkout(null, directory, s, null);
    }

    @DataProvider
    Object[][] urlsToHang()
    {
        return new String[][] {
                {"ssh://localhost:" + serverSocket.getLocalPort() + "/path/to/repo"},
                {"http://localhost:" + serverSocket.getLocalPort() + "/path/to/repo"},
                {"git://localhost:" + serverSocket.getLocalPort() + "/path/to/repo"},
        };
    }

    @Test(dataProvider = "urlsToHang", expectedExceptions = RepositoryException.class, timeOut = 5000)
    public void testTimeoutOnObtainingLatestRevision(String url) throws Exception
    {
        String rev = createGitOperationHelper().obtainLatestRevision(createAccessData(url));
    }

    @Test(dataProvider = "urlsToHang", expectedExceptions = RepositoryException.class, timeOut = 5000)
    public void testTimeoutOnFetch(String url) throws Exception
    {
        File directory = createTempDirectory();
        String targetRevision = createGitOperationHelper().obtainLatestRevision(createAccessData(url));
        createGitOperationHelper().fetch(directory, createAccessData(url), false);
        createGitOperationHelper().checkout(null, directory, targetRevision, null);
    }

}
