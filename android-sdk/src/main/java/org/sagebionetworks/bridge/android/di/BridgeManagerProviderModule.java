package org.sagebionetworks.bridge.android.di;

import android.app.Application;
import android.content.Context;

import dagger.Module;
import dagger.Provides;

@Module
public interface BridgeManagerProviderModule {
    @Provides
    static Context getApplicationContext(Application application) {
        return application.getApplicationContext();
    }
}
