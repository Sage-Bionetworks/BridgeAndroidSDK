/*
 *    Copyright 2018 Sage Bionetworks
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package org.sagebionetworks.bridge.android.manager;

import android.app.Application;
import android.content.Context;
import android.support.annotation.NonNull;

import org.sagebionetworks.bridge.android.BridgeApplication;
import org.sagebionetworks.bridge.android.BridgeConfig;
import org.sagebionetworks.bridge.android.di.BridgeManagerProviderModule;
import org.sagebionetworks.bridge.android.di.BridgeServiceModule;
import org.sagebionetworks.bridge.android.di.S3Module;
import org.sagebionetworks.bridge.android.manager.dao.AccountDAO;
import org.sagebionetworks.bridge.android.manager.dao.ConsentDAO;
import org.sagebionetworks.bridge.data.AndroidStudyUploadEncryptor;
import org.sagebionetworks.bridge.rest.ApiClientProvider;

import javax.inject.Singleton;

import dagger.BindsInstance;
import dagger.Component;

/**
 * Created by liujoshua on 2/22/2018.
 */

@Component(modules = {BridgeManagerProviderModule.class, BridgeServiceModule.class, S3Module.class})
public interface BridgeManagerProvider {
    @Component.Builder
    interface Builder {
        @BindsInstance
        Builder application(Application application);

        BridgeManagerProvider build();
    }

    static BridgeManagerProvider getInstance() {
        return BridgeApplication.getBridgeManagerProvider();
    }

    @NonNull
    AccountDAO getAccountDao();

    @NonNull
    ActivityManager getActivityManager();

    @NonNull
    ApiClientProvider getApiClientProvider();

    @NonNull
    AppConfigManager getAppConfigManager();

    @NonNull
    Context getApplicationContext();

    @NonNull
    AuthenticationManager getAuthenticationManager();

    @NonNull
    BridgeConfig getBridgeConfig();

    @NonNull
    ConsentDAO getConsentDao();

    @NonNull
    ParticipantRecordManager getParticipantManager();

    @NonNull
    AndroidStudyUploadEncryptor getStudyUploadEncryptor();

    @NonNull
    SurveyManager getSurveyManager();

    @NonNull
    UploadManager getUploadManager();
}
