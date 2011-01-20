package com.atlassian.bamboo.plugins.git;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.ssl.SslSocketConnector;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.transport.Transport;
import org.testng.annotations.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class TransportAllTrustingHttpsTest extends GitAbstractTest
{

    private static final String RESPONSE_TEXT = "This is a test instance";
    private Server server;
    private int port;

    @BeforeClass
    public void setUp() throws Exception
    {
        SslSocketConnector connector = new SslSocketConnector();
		connector.setPort(0);
		try {
			final InetAddress me = InetAddress.getByName("localhost");
			connector.setHost(me.getHostAddress());
		} catch (UnknownHostException e) {
			throw new RuntimeException("Cannot find localhost", e);
		}
        String keystore = getClass().getResource("/certs/selfsignedtest.keystore").toString();
        connector.setTruststore(keystore);
        connector.setKeystore(keystore);
        connector.setKeyPassword("password");
        connector.setTrustPassword("password");

        server = new Server();
		server.setConnectors(new Connector[]{connector});
		server.setHandler(new AbstractHandler()
        {
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
            {
                response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, RESPONSE_TEXT);
            }
        });

        server.setStopAtShutdown(true);
        server.start();
        port = connector.getLocalPort();
    }

    @AfterClass
    public void tearDown() throws Exception
    {
        server.stop();
    }

    // Using manual exception matching because we want to see the exception in the test output if it is not what we expect
    @Test
    public void testSelfSigned() throws Exception
    {
        String url = "https://localhost:" + port + "/repository"; // path necessary or jGit will not parse properly

        GitOperationHelper goh = createGitOperationHelper();
        GitRepository.GitRepositoryAccessData accessData = createAccessData(url);
        FileRepository fileRepository = new FileRepository(createTempDirectory());
        Transport transport = goh.open(fileRepository, accessData);
        try
        {
            transport.openFetch().close();
        }
        catch (TransportException e)
        {
            if (!e.getMessage().endsWith(RESPONSE_TEXT))
            {
                throw e;
            }
        }
    }
}
