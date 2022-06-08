package org.foo;

import java.security.GeneralSecurityException;

import javax.net.SocketFactory;

import com.sun.mail.util.MailSSLSocketFactory;

public class MyMailSSLSocketFactory extends MailSSLSocketFactory {

    public MyMailSSLSocketFactory() throws GeneralSecurityException {
        super();
    }

    public static synchronized SocketFactory getDefault() {
        try {
            MyMailSSLSocketFactory res = new MyMailSSLSocketFactory();
            res.setTrustAllHosts(true);
            return res;
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }
}
