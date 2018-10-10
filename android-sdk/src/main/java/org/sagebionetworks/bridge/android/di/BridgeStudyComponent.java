package org.sagebionetworks.bridge.android.di;

import android.content.Context;
import android.support.annotation.NonNull;

import org.sagebionetworks.bridge.android.BridgeConfig;
import org.sagebionetworks.bridge.android.manager.AppConfigManager;
import org.sagebionetworks.bridge.android.manager.dao.AccountDAO;
import org.sagebionetworks.bridge.android.manager.dao.ConsentDAO;
import org.sagebionetworks.bridge.android.manager.dao.UploadDAO;
import org.sagebionetworks.bridge.data.AndroidStudyUploadEncryptor;
import org.sagebionetworks.bridge.rest.ApiClientProvider;

import javax.inject.Named;

import dagger.BindsInstance;
import dagger.Component;
import okhttp3.OkHttpClient;

@Component(modules = {BridgeServiceModule.class, S3Module.class})
@BridgeStudyScope
public interface BridgeStudyComponent {
    @Component.Builder
    interface Builder {
        @BindsInstance
        Builder applicationContext(Context context);

        BridgeStudyComponent build();
    }

    @NonNull
    AccountDAO getAccountDao();

    @NonNull
    ApiClientProvider getApiClientProvider();

    @NonNull
    AppConfigManager getAppConfigManager();

    @NonNull
    BridgeConfig getBridgeConfig();

    @NonNull
    ConsentDAO getConsentDao();

    @NonNull
    UploadDAO getUploadDAO();

    @NonNull
    AndroidStudyUploadEncryptor getStudyUploadEncryptor();

    @Named("s3OkHttp3Client")
    OkHttpClient getS3OkHttp3Client();
}
