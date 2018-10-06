package org.sagebionetworks.bridge.android.manager;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

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
