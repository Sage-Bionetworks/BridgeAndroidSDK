package org.sagebionetworks.bridge.android;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import org.sagebionetworks.bridge.android.manager.BridgeManagerProvider;
import org.sagebionetworks.bridge.rest.exceptions.ConsentRequiredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Single;

/**
 * Created by liujoshua on 12/17/2017.
 */

public class AuthManagementActivity extends Activity {
    private static final Logger logger =
            LoggerFactory.getLogger(AuthManagementActivity.class);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String action = intent.getAction();
        Uri data = intent.getData();
        String study = data.getQueryParameter("study");
        String email = data.getQueryParameter("email");
        String token = data.getQueryParameter("token");


        BridgeManagerProvider.getInstance()
                .getAuthenticationManager()
                .signInViaEmailLink(email, token)
                .subscribe(session -> {
//                    setResult(RESULT_OK);
                    PackageManager pm = getPackageManager();
                    Intent launchIntentForPackage = pm.getLaunchIntentForPackage(getPackageName());
                    startActivity(launchIntentForPackage);
                }, t -> {
                    logger.warn("Failed to authenticated: ", t);
                    setResult(RESULT_CANCELED);
                });
    }
}
