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

package org.sagebionetworks.bridge.android.manager

import android.support.annotation.AnyThread

import org.sagebionetworks.bridge.android.BridgeConfig
import org.sagebionetworks.bridge.android.di.BridgeStudyScope
import org.sagebionetworks.bridge.android.manager.dao.AppConfigDAO
import org.sagebionetworks.bridge.android.manager.models.ProfileDataManager
import org.sagebionetworks.bridge.android.manager.models.ProfileDataSource
import org.sagebionetworks.bridge.android.util.retrofit.RxUtils
import org.sagebionetworks.bridge.rest.api.PublicApi
import org.sagebionetworks.bridge.rest.model.AppConfig

import javax.inject.Inject

import rx.Single
import rx.functions.Action1

/** Handles calling Bridge server to get study app config and caching the result.  */
@AnyThread
@BridgeStudyScope
class AppConfigManager @Inject
constructor(private val appConfigDAO: AppConfigDAO,
            private val publicApi: PublicApi, private val config: BridgeConfig) {

    /** Get app config from the cache, or fall back to server if there is no value in the cache.  */
    val appConfig: Single<AppConfig>
        get() {
//            val cachedAppConfig = cachedAppConfig
//            return if (cachedAppConfig != null) {
//                Single.just(cachedAppConfig)
//            } else {
                return remoteAppConfig
//            }
        }

    /** Get the app config from the cache, null if there is no value in the cache.  */
    val cachedAppConfig: AppConfig?
        get() = appConfigDAO.appConfig

    /** Gets the app config from the server and caches the result.  */
    val remoteAppConfig: Single<AppConfig>
        get() = RxUtils.toBodySingle(publicApi
                .getAppConfigForStudy(config.studyId))
                .doOnSuccess(Action1<AppConfig> { appConfigDAO.cacheAppConfig(it) })


//    val profileDataSources: Single<Map<String, ProfileDataSource>>
//        get() = appConfig.map { extractProfileDataSources(it) }
//
//    val profileDataManagers: Single<Map<String, ProfileDataManager>>
//        get() = appConfig.map { extractProfileDataManagers(it) }
//
//    private fun extractProfileDataSources(appConfig: AppConfig) : Map<String, ProfileDataSource> {
//        return appConfig.configElements.filter {(it.value as Map<String, Any>).get("catType") == "profileDataSource"}.mapValues { ProfileDataSource(it.value as Map<String, Any>) }
//    }
//
//    private fun extractProfileDataManagers(appConfig: AppConfig) : Map<String, ProfileDataManager> {
//        return appConfig.configElements.filter {(it.value as Map<String, Any>).get("catType") == "profileManager"}.mapValues { ProfileDataManager(it.value as Map<String, Any>) }
//    }


}
