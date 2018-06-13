package org.sagebionetworks.bridge.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import org.sagebionetworks.bridge.android.manager.BridgeManagerProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.android.schedulers.AndroidSchedulers;
import rx.subscriptions.CompositeSubscription;

import java.net.UnknownHostException;

/**
 * Created by liujoshua on 12/17/2017.
 */

public class AuthManagementActivity extends Activity {
    private static final Logger logger =
            LoggerFactory.getLogger(AuthManagementActivity.class);

    private final CompositeSubscription compositeSubscription = new CompositeSubscription();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().getDecorView().setBackgroundColor(Color.TRANSPARENT);

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        compositeSubscription.clear();
    }

    protected void verifyEmail(Uri data) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    protected void startSession(Uri data) {
        String email = data.getQueryParameter("email");
        if (email == null) {
            BridgeManagerProvider.getInstance().getAuthenticationManager().getEmail();
        }
        String token = data.getQueryParameter("token");

        compositeSubscription.add(
                BridgeManagerProvider.getInstance()
                        .getAuthenticationManager()
                        .signInViaEmailLink(email, token)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(session -> {
                            authSuccess();
                        }, this::authFailure)
        );
    }

    protected void authFailure(Throwable t) {
        logger.warn("Failed to authenticated: ", t);
        String errorMsg = t.getLocalizedMessage();
        if (t instanceof UnknownHostException) {
            errorMsg = "Please check your network connection and try again.";
        }
        new AlertDialog.Builder(AuthManagementActivity.this)
                .setMessage(errorMsg)
                .setCancelable(false)
                .setPositiveButton("Okay", (dialogInterface, i) -> finish())
                .create().show();
    }

    protected void authSuccess() {
        PackageManager pm = getPackageManager();
        Intent launchIntentForPackage = pm.getLaunchIntentForPackage(getPackageName());
        if (launchIntentForPackage != null) {
            launchIntentForPackage.addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launchIntentForPackage);
        }
        finish();
    }
}
