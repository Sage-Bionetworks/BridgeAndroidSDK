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

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.sagebionetworks.bridge.android.BridgeConfig;
import org.sagebionetworks.bridge.android.manager.dao.AppConfigDAO;
import org.sagebionetworks.bridge.android.util.retrofit.RxUtils;
import org.sagebionetworks.bridge.rest.api.PublicApi;
import org.sagebionetworks.bridge.rest.model.AppConfig;

import javax.inject.Inject;

import rx.Single;

/** Handles calling Bridge server to get study app config and caching the result. */
@AnyThread
public class AppConfigManager {
    private @NonNull final AppConfigDAO appConfigDAO;
    private @NonNull final PublicApi publicApi;
    private @NonNull final BridgeConfig config;

    @Inject
    public AppConfigManager(@NonNull AppConfigDAO appConfigDAO,
            @NonNull PublicApi publicApi, @NonNull BridgeConfig config) {
        this.appConfigDAO = appConfigDAO;
        this.publicApi = publicApi;
        this.config = config;
    }

    /** Get app config from the cache, or fall back to server if there is no value in the cache. */
    public @NonNull Single<AppConfig> getAppConfig() {
        AppConfig cachedAppConfig = getCachedAppConfig();
        if (cachedAppConfig != null) {
            return Single.just(cachedAppConfig);
        } else {
            return getRemoteAppConfig();
        }
    }

    /** Get the app config from the cache, null if there is no value in the cache. */
    public @Nullable AppConfig getCachedAppConfig() {
        return appConfigDAO.getAppConfig();
    }

    /** Gets the app config from the server and caches the result. */
    public @NonNull Single<AppConfig> getRemoteAppConfig() {
        return RxUtils.toBodySingle(publicApi
                .getAppConfig(config.getStudyId()))
                .doOnSuccess(appConfigDAO::cacheAppConfig);
    }
}
