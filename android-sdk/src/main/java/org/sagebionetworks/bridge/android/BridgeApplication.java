package org.sagebionetworks.bridge.android;

import android.app.Application;
import android.content.res.Configuration;

import net.danlew.android.joda.JodaTimeAndroid;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;

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