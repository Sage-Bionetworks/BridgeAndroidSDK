package org.sagebionetworks.bridge.android.di;

import android.app.Application;
import android.content.Context;

import org.sagebionetworks.bridge.rest.ApiClientProvider;
import org.sagebionetworks.bridge.rest.api.PublicApi;

import dagger.Module;
import dagger.Provides;

@Module
public interface BridgeManagerProviderModule {
    @Provides
    static Context getApplicationContext(Application application) {
        return application.getApplicationContext();
    }

    @Provides
    static PublicApi providePublicApi(ApiClientProvider apiClientProvider) {
        return apiClientProvider.getClient(PublicApi.class);
    }
}
