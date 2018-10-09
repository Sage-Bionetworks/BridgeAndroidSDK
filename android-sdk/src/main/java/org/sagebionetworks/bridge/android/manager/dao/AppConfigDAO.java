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

package org.sagebionetworks.bridge.android.manager.dao;

import javax.inject.Inject;
import javax.inject.Singleton;

import android.content.Context;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.sagebionetworks.bridge.rest.model.AppConfig;

/** Caches study app config in shared preferences. */
@AnyThread
@Singleton //TODO: consider scoping/namespacing to @BridgeStudyScope @liujoshua 2018/10/09
public class AppConfigDAO extends SharedPreferencesJsonDAO {
    private static final String APP_CONFIG_KEY = "APP_CONFIG";
    private static final String PREFERENCES_FILE = "appconfig";

    @Inject
    public AppConfigDAO(@NonNull Context applicationContext) {
        super(applicationContext, PREFERENCES_FILE);
    }

    /** Store the given app config in the cache, overwriting previous values if any. */
    public void cacheAppConfig(@NonNull AppConfig appConfig) {
        setValue(APP_CONFIG_KEY, appConfig, AppConfig.class);
    }

    /** Get cached app config, if present in the cache. */
    public @Nullable AppConfig getAppConfig() {
        return getValue(APP_CONFIG_KEY, AppConfig.class);
    }

    /** Removes the app config from the cache. */
    public void removeAppConfig() {
        removeValue(APP_CONFIG_KEY);
    }
}
