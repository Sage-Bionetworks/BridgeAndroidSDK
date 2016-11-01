package org.sagebionetworks.bridge.android;

import android.app.Application;

import net.danlew.android.joda.JodaTimeAndroid;

import ch.qos.logback.classic.android.BasicLogcatConfigurator;

/**
 * Base class for a Bridge Application.
 */
public abstract class BridgeApplication extends Application {
  @Override
  public void onCreate() {
    super.onCreate();
    JodaTimeAndroid.init(this);
    //BasicLogcatConfigurator.configureDefaultContext();
  }
}