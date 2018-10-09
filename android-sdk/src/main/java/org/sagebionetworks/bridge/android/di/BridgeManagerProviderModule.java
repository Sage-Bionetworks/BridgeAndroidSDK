package org.sagebionetworks.bridge.android.di;

import android.app.Application;
import android.content.Context;

import org.sagebionetworks.bridge.android.BridgeApplication;
import org.sagebionetworks.bridge.android.manager.BridgeManagerProvider;
import org.sagebionetworks.bridge.rest.ApiClientProvider;
import org.sagebionetworks.bridge.rest.api.PublicApi;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module(includes = {BridgeServiceModule.class, S3Module.class})
public interface BridgeManagerProviderModule {
    @Provides
    @Singleton
    static Context getApplicationContext(Application application) {
        return application.getApplicationContext();
    }

    @Provides
    static BridgeManagerProvider getInstance() {
        return BridgeApplication.getBridgeManagerProvider();
    }
    
    @Provides
    @Singleton
    static PublicApi providePublicApi(ApiClientProvider apiClientProvider) {
        return apiClientProvider.getClient(PublicApi.class);
    }
}
