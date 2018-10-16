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

import java.util.concurrent.TimeUnit;

import javax.inject.Named;
import javax.net.SocketFactory;

import dagger.Module;
import dagger.Provides;
import okhttp3.OkHttpClient;

/**
 * Created by liujoshua on 2/22/2018.
 */
@Module
public class S3Module {
    @Provides
    @Named("s3OkHttp3Client")
    @BridgeStudyScope
    OkHttpClient getS3OkHttp3Client(SocketFactory socketFactory) {
        return new OkHttpClient.Builder()
                .socketFactory(socketFactory)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true).build();
    }
}
