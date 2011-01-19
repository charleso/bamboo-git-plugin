package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.repository.RepositoryException;
import org.apache.log4j.Logger;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.TransportHttp;
import org.eclipse.jgit.transport.URIish;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

public class TransportAllTrustingHttps extends TransportHttp
{
    private static final Logger log = Logger.getLogger(TransportAllTrustingHttps.class);

    private final SSLSocketFactory sslSocketFactory;

    private static final TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager()
            {
                public X509Certificate[] getAcceptedIssuers()
                {
                    return null;
                }

                public void checkClientTrusted(X509Certificate[] certs, String authType)
                {
                }

                public void checkServerTrusted(X509Certificate[] certs, String authType)
                {
                }
            }
    };

    private static final HostnameVerifier dummyHostnameVerifier = new HostnameVerifier()
    {
        public boolean verify(String s, SSLSession sslSession)
        {
            return true;
        }
    };

    protected TransportAllTrustingHttps(Repository local, URIish uri)
            throws NotSupportedException, RepositoryException
    {
        super(local, uri);

        SSLContext context;
        try
        {
            context = SSLContext.getInstance("SSL");
            context.init(null, trustAllCerts, new SecureRandom());
            sslSocketFactory = context.getSocketFactory();
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new RepositoryException("Cannot get SSL Context", e);
        }
        catch (KeyManagementException e)
        {
            throw new RepositoryException("Cannot initialize SSL Context", e);
        }

    }

    public static boolean canHandle(final URIish uri) {
        return uri.isRemote() && "https".equals(uri.getScheme());
    }

    @Override
    protected HttpURLConnection httpOpen(String method, URL u) throws IOException
    {
        HttpURLConnection conn = super.httpOpen(method, u);
        if (!(conn instanceof HttpsURLConnection))
        {
            log.error("Expecting HttpsURLConnection for https:// protocol, got " + conn.getClass());
            return conn;
        }
        HttpsURLConnection httpsConnection = (HttpsURLConnection) conn;

        httpsConnection.setSSLSocketFactory(sslSocketFactory);
        httpsConnection.setHostnameVerifier(dummyHostnameVerifier);

        return httpsConnection;
    }
}
