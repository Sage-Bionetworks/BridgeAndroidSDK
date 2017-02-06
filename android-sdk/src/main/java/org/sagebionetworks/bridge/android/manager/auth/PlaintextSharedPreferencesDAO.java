package org.sagebionetworks.bridge.android.manager.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.Nullable;

import org.sagebionetworks.bridge.rest.RestUtils;
import org.sagebionetworks.bridge.rest.model.UserSessionInfo;

/**
 * Created by jyliu on 2/2/2017.
 */

class PlaintextSharedPreferencesDAO implements AuthenticationManager.DAO {
    public static final String PREFERENCES_FILE = "authentication";

    private static final String KEY_SESSION_INFO = "session";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_PASSWORD = "password";

    private final SharedPreferences sharedPreferences;

    public PlaintextSharedPreferencesDAO(Context applicationContext) {
        sharedPreferences = applicationContext
                .getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE);
    }

    @Nullable
    @Override
    public UserSessionInfo getUserSessionInfo() {
        String sessionJson = sharedPreferences.getString(KEY_SESSION_INFO, null);
        return RestUtils.GSON.fromJson(sessionJson, UserSessionInfo.class);
    }

    @Override
    public void setUserSessionInfo(@Nullable UserSessionInfo userSessionInfo) {
        String sessionJson = RestUtils.GSON.toJson(userSessionInfo);
        sharedPreferences.edit().putString(KEY_SESSION_INFO, sessionJson).apply();
    }

    @Nullable
    @Override
    public String getEmail() {
        return sharedPreferences.getString(KEY_EMAIL, null);
    }

    @Override
    public void setEmail(@Nullable String email) {
        sharedPreferences.edit().putString(KEY_EMAIL, email).apply();
    }

    @Nullable
    @Override
    public String getPassword() {
        return sharedPreferences.getString(KEY_PASSWORD, null);
    }

    @Override
    public void setPassword(@Nullable String password) {
        // FIXME: password should not be in plaintext
        sharedPreferences.edit().putString(KEY_PASSWORD, password).apply();
    }
}
