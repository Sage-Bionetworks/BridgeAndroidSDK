package org.sagebionetworks.bridge.android;

import android.app.Application;
import com.facebook.stetho.Stetho;
import net.danlew.android.joda.JodaTimeAndroid;
import org.sagebionetworks.bridge.android.manager.BridgeManagerProvider;
import org.sagebionetworks.bridge.android.manager.DaggerBridgeManagerProvider;

/**
 * Base class for a Bridge Application.
 */
public class BridgeApplication extends Application {
    private static BridgeManagerProvider bridgeManagerProvider;
    
    @Override
    public void onCreate() {
        super.onCreate();
    
        bridgeManagerProvider = initBridgeManagerProvider();
        
        JodaTimeAndroid.init(this);
        
        if (this.getResources().getBoolean(R.bool.osb_stetho_debug_bridge)){
            initStetho();
        }
    }
    
    protected BridgeManagerProvider initBridgeManagerProvider() {
        return DaggerBridgeManagerProvider.builder()
                .application(this)
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