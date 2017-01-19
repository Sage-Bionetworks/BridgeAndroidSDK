package org.sagebionetworks.bridge.android;

import android.app.Application;

import net.danlew.android.joda.JodaTimeAndroid;

/**
 * Base class for a Bridge Application.
 */
public abstract class BridgeApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        JodaTimeAndroid.init(this);
    }
}