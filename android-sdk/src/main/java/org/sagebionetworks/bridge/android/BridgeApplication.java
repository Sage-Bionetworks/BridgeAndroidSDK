package org.sagebionetworks.bridge.android;

import android.content.Context;
import android.support.multidex.MultiDex;

import com.facebook.stetho.Stetho;

import net.danlew.android.joda.JodaTimeAndroid;

import org.sagebionetworks.bridge.android.di.BridgeStudyComponent;
import org.sagebionetworks.bridge.android.di.DaggerBridgeApplicationComponent;
import org.sagebionetworks.bridge.android.di.DaggerBridgeStudyComponent;
import org.sagebionetworks.bridge.android.manager.BridgeManagerProvider;
import org.sagebionetworks.bridge.android.manager.DaggerBridgeManagerProvider;

import dagger.android.AndroidInjector;
import dagger.android.support.DaggerApplication;

/**
 * Base class for a Bridge Application.
 */
public class BridgeApplication extends DaggerApplication {
    private static BridgeManagerProvider bridgeManagerProvider;

    @Override
    protected void attachBaseContext(Context base) {
        // This is needed for android versions < 5.0 or you can extend MultiDexApplication
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        getOrInitBridgeManagerProvider();
        JodaTimeAndroid.init(this);

        if (this.getResources().getBoolean(R.bool.osb_stetho_debug_bridge)) {
            initStetho();
        }
    }

    public final BridgeManagerProvider getOrInitBridgeManagerProvider() {
        if (BridgeApplication.bridgeManagerProvider != null) {
            return BridgeApplication.bridgeManagerProvider;
        }
        BridgeStudyComponent bridgeStudyComponent = DaggerBridgeStudyComponent.builder()
                .applicationContext(this.getApplicationContext())
                .build();
        BridgeManagerProvider bridgeManagerProvider = initBridgeManagerScopedComponent(bridgeStudyComponent);
        BridgeApplication.bridgeManagerProvider = bridgeManagerProvider;
        return bridgeManagerProvider;
    }

    protected BridgeManagerProvider initBridgeManagerScopedComponent(BridgeStudyComponent bridgeStudyComponent) {
        BridgeManagerProvider bridgeManagerProvider = DaggerBridgeManagerProvider.builder()
                .applicationContext(this.getApplicationContext())
                .bridgeStudyComponent(bridgeStudyComponent)
                .build();
        return bridgeManagerProvider;
    }

    @Override
    protected AndroidInjector<? extends DaggerApplication> applicationInjector() {
        return DaggerBridgeApplicationComponent
                .builder()
                .application(this)
                .bridgeManagerProvider(getOrInitBridgeManagerProvider())
                .build();
    }

    protected void initStetho() {
        Stetho.initialize(Stetho.newInitializerBuilder(this)
                // Enable Chrome DevTools
                .enableWebKitInspector(Stetho.defaultInspectorModulesProvider(this))
                // Enable command line interface
                .enableDumpapp(Stetho.defaultDumperPluginsProvider(this))
                .build());
    }

    public static BridgeManagerProvider getBridgeManagerProvider() {
        return bridgeManagerProvider;
    }
}