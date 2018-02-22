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

package org.sagebionetworks.bridge.android.di;

import android.content.Context;

import com.facebook.stetho.okhttp3.StethoInterceptor;
import com.google.common.collect.Lists;

import org.sagebionetworks.bridge.android.BridgeConfig;
import org.sagebionetworks.bridge.android.R;
import org.sagebionetworks.bridge.data.AndroidStudyUploadEncryptor;
import org.sagebionetworks.bridge.rest.ApiClientProvider;

import java.util.Collections;
import java.util.List;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import okhttp3.Interceptor;

/**
 * Created by liujoshua on 2/22/2018.
 */
@Module
public class BridgeServiceModule {
    public BridgeServiceModule() {
    }
    
    @Provides
    @Singleton
    ApiClientProvider getApiClientProvider(Context applicationContext,
                                           BridgeConfig bridgeConfig) {
    
        List<Interceptor> appInterceptors = Collections.emptyList();
        List<Interceptor> networkInterceptors = Lists.newArrayList();
        if (applicationContext.getResources().getBoolean(R.bool.osb_stetho_debug_bridge)) {
            networkInterceptors.add(new StethoInterceptor());
        }
    
        return new ApiClientProvider(
                bridgeConfig.getBaseUrl(),
                bridgeConfig.getUserAgent(),
                bridgeConfig.getAcceptLanguage(),
                bridgeConfig.getStudyId(),
                networkInterceptors,
                appInterceptors);
    }
    
    @Provides
    @Singleton
    AndroidStudyUploadEncryptor getAndroidStudyUploadEncryptor(BridgeConfig bridgeConfig) {
        try {
            return new AndroidStudyUploadEncryptor(bridgeConfig.getPublicKey());
        } catch (Exception e) {
            throw new RuntimeException("Could not create StudyUploadEncryptor", e);
        }
    }
}
