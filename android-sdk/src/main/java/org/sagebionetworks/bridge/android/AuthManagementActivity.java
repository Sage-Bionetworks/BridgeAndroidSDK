package org.sagebionetworks.bridge.android;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import org.sagebionetworks.bridge.android.manager.BridgeManagerProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by liujoshua on 12/17/2017.
 */

public class AuthManagementActivity extends Activity {
    private static final Logger logger =
            LoggerFactory.getLogger(AuthManagementActivity.class);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Uri data = getIntent().getData();
        if (data == null) {
            logger.warn("Intent did not contain any data, aborting.");
            return;
        }

        switch (data.getLastPathSegment()) {
            case "startSession.html":
                startSession(data);
                break;
            case "verifyEmail.html":
                startSession(data);
                break;
            default:
                startSession(data);
        }
    }

    void verifyEmail(Uri data) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    void startSession(Uri data) {
        String email = data.getQueryParameter("email");
        if (email == null) {
            BridgeManagerProvider.getInstance().getAuthenticationManager().getEmail();
        }
        String token = data.getQueryParameter("token");
        BridgeManagerProvider.getInstance()
                .getAuthenticationManager()
                .signInViaEmailLink(email, token)
                .subscribe(session -> {
                    PackageManager pm = getPackageManager();
                    Intent launchIntentForPackage = pm.getLaunchIntentForPackage(getPackageName());
                    launchIntentForPackage.setFlags(
                            Intent.FLAG_ACTIVITY_CLEAR_TOP |
                            Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(launchIntentForPackage);
                    finish();
                }, t -> {
                    logger.warn("Failed to authenticated: ", t);
                    setResult(RESULT_CANCELED);
                    finish();
                });
    }
}
