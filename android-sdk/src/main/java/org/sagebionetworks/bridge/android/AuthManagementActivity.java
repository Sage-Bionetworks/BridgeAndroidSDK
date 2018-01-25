package org.sagebionetworks.bridge.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import org.sagebionetworks.bridge.android.manager.BridgeManagerProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.android.schedulers.AndroidSchedulers;

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
        Uri data = intent.getData();

        if (data != null) {
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
        } else {
            logger.error("No intent data from deep link in AuthManagementActivity");
            showFailedAlert("Something went wrong, please try again later.");
        }
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
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(session -> {
                    setResult(RESULT_OK);
                    PackageManager pm = getPackageManager();
                    Intent launchIntentForPackage = pm.getLaunchIntentForPackage(getPackageName());
                    startActivity(launchIntentForPackage);
                    finish();
                }, t -> showFailedAlert("Failed to authenticate: " + t.getLocalizedMessage()));
    }

    private void showFailedAlert(String message) {
        logger.error(message);
        setResult(RESULT_CANCELED);
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton("Okay", (dialogInterface, i) -> finish())
                .setCancelable(false)
                .create().show();
    }
}
