package org.sagebionetworks.bridge.android.di;

import android.app.Application;
import android.content.Context;

import dagger.Module;
import dagger.Provides;

@Module(includes = {BridgeServiceModule.class, S3Module.class})
public class BridgeApplicationModule {
    @Provides
    static Context getApplicationContext(Application application) {
        return application.getApplicationContext();
    }
}
