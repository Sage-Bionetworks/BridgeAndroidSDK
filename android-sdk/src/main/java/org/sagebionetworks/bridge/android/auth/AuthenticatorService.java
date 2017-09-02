package org.sagebionetworks.bridge.android.auth;


import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * A bound Service that instantiates the osb_authenticator
 * when started.
 */
public class AuthenticatorService extends Service {
    // Instance field that stores the osb_authenticator object
    private Authenticator mAuthenticator;

    @Override
    public void onCreate() {
        // Create a new osb_authenticator object
        mAuthenticator = new Authenticator(this);
    }

    /*
     * When the system binds to this Service to make the RPC call
     * return the osb_authenticator's IBinder.
     */
    @Override
    public IBinder onBind(Intent intent) {
        return mAuthenticator.getIBinder();
    }
}
