package org.sagebionetworks.research.sageresearch.dao.room

import io.reactivex.Completable
import io.reactivex.Single
import org.sagebionetworks.bridge.android.manager.AppConfigManager
import org.sagebionetworks.bridge.android.manager.models.ProfileDataManager
import org.sagebionetworks.bridge.android.manager.models.ProfileDataSource
import org.sagebionetworks.bridge.android.manager.models.getSurveyReference
import org.sagebionetworks.bridge.rest.model.AppConfig
import org.sagebionetworks.bridge.rest.model.SurveyReference

class AppConfigRepository(resourceDao: ResourceEntityDao, val appConfigManager: AppConfigManager): ResourceRepository<AppConfig>(resourceDao) {

    companion object {
        const val APP_CONFIG_ID = "AppConfigId"
    }

    var cachedAppConfig: AppConfig? = null

    val appConfig : Single<AppConfig>
        get() {
            return resourceDao.getResource(APP_CONFIG_ID).filter {
                if (it.isEmpty() || it.get(0).lastUpdateTime + defaultUpdateFrequency < System.currentTimeMillis()) {
                    subscribeCompletable(getRemoteAppConfig(), "Get app config succeeded", "Get app config failed")
                }
                !it.isEmpty()
            }.map {
                cachedAppConfig = it.get(0).loadResource(AppConfig::class.java)
                cachedAppConfig!!
            }.firstOrError()
        }

    val profileDataSources: Single<Map<String, ProfileDataSource>>
        get() = appConfig.map { extractProfileDataSources(it) }

    val profileDataManagers: Single<Map<String, ProfileDataManager>>
        get() = appConfig.map { extractProfileDataManagers(it) }

    fun getSurveyReference(surveyId: String): Single<SurveyReference> {
        return appConfig.map { it.getSurveyReference(surveyId) }
    }

    private fun getRemoteAppConfig(): Completable {
        val surveySingle = toV2SingleAsync(appConfigManager.appConfig)
        return surveySingle.observeOn(asyncScheduler)
                .flatMapCompletable {
                    storeAppConfigInRoom(it)
                }
                .doOnError {
                    logger.warn("Failed to fetch reports from bridge")
                }
    }

    private fun storeAppConfigInRoom(appConfig: AppConfig): Completable {
        return storeResourceInRoom(appConfig, APP_CONFIG_ID, ResourceEntity.ResourceType.APP_CONFIG)
    }

    private fun extractProfileDataSources(appConfig: AppConfig) : Map<String, ProfileDataSource> {
        return appConfig.configElements.filter {(it.value as Map<String, Any>).get("catType") == "profileDataSource"}.mapValues { ProfileDataSource(it.value as Map<String, Any>) }
    }

    private fun extractProfileDataManagers(appConfig: AppConfig) : Map<String, ProfileDataManager> {
        return appConfig.configElements.filter {(it.value as Map<String, Any>).get("catType") == "profileManager"}.mapValues { ProfileDataManager(it.value as Map<String, Any>, appConfig) }
    }

}