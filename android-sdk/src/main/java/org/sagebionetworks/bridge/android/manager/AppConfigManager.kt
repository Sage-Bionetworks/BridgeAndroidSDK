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
import org.sagebionetworks.bridge.android.util.retrofit.RxUtils
import org.sagebionetworks.bridge.rest.api.PublicApi
import org.sagebionetworks.bridge.rest.model.AppConfig
import rx.Single
import javax.inject.Inject

/** Handles calling Bridge server to get study app config. AppConfigRepository should be used
 * for client side access to AppConfig data */
@AnyThread
@BridgeStudyScope
class AppConfigManager @Inject
constructor(private val publicApi: PublicApi, private val config: BridgeConfig) {

    /** Gets the app config from the server.  */
    val appConfig: Single<AppConfig>
        get() = RxUtils.toBodySingle(publicApi
                .getAppConfigForStudy(config.studyId))



}
