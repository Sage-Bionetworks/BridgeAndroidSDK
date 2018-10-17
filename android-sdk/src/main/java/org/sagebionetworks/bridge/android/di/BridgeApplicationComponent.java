package org.sagebionetworks.bridge.android.di;

import android.app.Application;

import org.sagebionetworks.bridge.android.BridgeApplication;
import org.sagebionetworks.bridge.android.manager.BridgeManagerProvider;

import dagger.BindsInstance;
import dagger.Component;
import dagger.android.AndroidInjector;
import dagger.android.support.AndroidSupportInjectionModule;

@Component(modules = {AndroidSupportInjectionModule.class}, dependencies = BridgeManagerProvider.class)
@BridgeApplicationScope
public interface BridgeApplicationComponent extends AndroidInjector<BridgeApplication> {
    @Component.Builder
    interface Builder {
        @BindsInstance
        Builder application(Application application);

        Builder bridgeManagerProvider(BridgeManagerProvider bridgeManagerProvider);

        BridgeApplicationComponent build();
    }
}
