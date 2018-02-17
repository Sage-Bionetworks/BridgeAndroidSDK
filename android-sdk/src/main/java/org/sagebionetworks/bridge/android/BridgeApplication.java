package org.sagebionetworks.bridge.android;

import android.app.Application;

import com.facebook.stetho.Stetho;

import net.danlew.android.joda.JodaTimeAndroid;

import org.sagebionetworks.bridge.android.manager.BridgeManagerProvider;

/**
 * Base class for a Bridge Application.
 */
public class BridgeApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        JodaTimeAndroid.init(this);
        BridgeManagerProvider.init(this);
        if (this.getResources().getBoolean(R.bool.osb_stetho_debug_bridge)){
            initStetho();
        }
    }

    protected void initStetho() {
        Stetho.initialize(Stetho.newInitializerBuilder(this)
                // Enable Chrome DevTools
                .enableWebKitInspector(Stetho.defaultInspectorModulesProvider(this))
                // Enable command line interface
                .enableDumpapp(Stetho.defaultDumperPluginsProvider(this))
                .build());
    }
}