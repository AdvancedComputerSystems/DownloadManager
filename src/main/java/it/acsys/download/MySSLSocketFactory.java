/**
 *    Copyright 1995-2014, Advanced Computer Systems ,Inc.
 *    Via Della Bufalotta, 378 - 00139 Roma - Italy
 *    http://www.acsys.it
 *    All Rights Reserved.
 *
 *    This is UNPUBLISHED PROPRIETARY SOURCE CODE of Advanced Computer Systems;
 *    the contents of this file may not be disclosed to third parties, copied or
 *    duplicated in any form, in whole or in part, without the prior written
 *    permission of Advanced Computer Systems, Inc.
 *
 *    $Prod:  $
 *    $Author: anngal $
 *    $Id: MySSLSocketFactory.java,v 1.2 2014/08/06 14:08:58 anngal Exp $
 *    $Revision: 1.2 $
 */
package it.acsys.download;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.conn.ssl.SSLSocketFactory;

/**
 * This class is used to perform downloads from auto-certified servers.
 * 
 *
 */

public class MySSLSocketFactory extends SSLSocketFactory {
    private SSLContext sslContext = SSLContext.getInstance("TLS");

    /**
     * @param truststore
     * @throws NoSuchAlgorithmException
     * @throws KeyManagementException
     * @throws KeyStoreException
     * @throws UnrecoverableKeyException
     */
    public MySSLSocketFactory(KeyStore truststore) throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException {
        super(truststore);

        TrustManager tm = new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            }

            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            }

            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }
        };

        sslContext.init(null, new TrustManager[] { tm }, null);
    }

    /* (non-Javadoc)
     * @see org.apache.http.conn.ssl.SSLSocketFactory#createSocket(java.net.Socket, java.lang.String, int, boolean)
     */
    @Override
    public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException, UnknownHostException {
        return sslContext.getSocketFactory().createSocket(socket, host, port, autoClose);
    }

    /* (non-Javadoc)
     * @see org.apache.http.conn.ssl.SSLSocketFactory#createSocket()
     */
    @Override
    public Socket createSocket() throws IOException {
        return sslContext.getSocketFactory().createSocket();
    }
}
