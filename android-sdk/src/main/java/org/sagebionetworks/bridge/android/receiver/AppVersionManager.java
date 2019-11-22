package org.sagebionetworks.bridge.android.receiver;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import org.sagebionetworks.bridge.android.BridgeConfig;

import javax.inject.Inject;

/**
 * Manages version of the app to notify when an upgrade is required.
 */
public class AppVersionManager {
    private static final String SHARED_PREFS_FILE = "org.sagebionetworks.bridge.AppVersion";

    private static final String PREFS_KEY_UPGRADE_REQUIRED = "APP_UPGRADE_REQUIRED_FROM_VERSION";

    private final SharedPreferences appVersionSharedPreferences;

    private final BridgeConfig bridgeConfig;

    @Inject
    public AppVersionManager(Context context, BridgeConfig bridgeConfig) {
        appVersionSharedPreferences = context.getSharedPreferences(SHARED_PREFS_FILE, Context.MODE_PRIVATE);
        this.bridgeConfig = bridgeConfig;
    }

    /**
     * @return live data which will contain true when an app upgrade is required
     */
    @NonNull
    public boolean isUpgradeRequired() {
        int upgradeRequiredVersion = appVersionSharedPreferences
                .getInt(PREFS_KEY_UPGRADE_REQUIRED, 0);

        return bridgeConfig.getAppVersion() <= upgradeRequiredVersion;
    }

    /**
     * Set that upgrading from current version is required, after which {@link #isUpgradeRequired() will contain true
     * until a higher version is installed.}
     */
    public void setUpgradeRequired() {
        appVersionSharedPreferences.edit()
                .putInt(PREFS_KEY_UPGRADE_REQUIRED, bridgeConfig.getAppVersion())
                .apply();
    }

    @VisibleForTesting
    SharedPreferences getAppVersionSharedPreferences() {
        return appVersionSharedPreferences;
    }
}
