package org.sagebionetworks.bridge.android.manager;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import org.sagebionetworks.bridge.android.AuthManagementActivity;

public class EmailSignInRedirectUriActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent launchIntent = new Intent(this, AuthManagementActivity.class)
                .setData(getIntent().getData())
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        startActivity(launchIntent);
        finish();
    }
}
