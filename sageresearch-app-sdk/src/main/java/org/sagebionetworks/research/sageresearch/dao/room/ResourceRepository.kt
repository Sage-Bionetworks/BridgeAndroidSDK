package org.sagebionetworks.research.sageresearch.dao.room

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.Observer
import android.arch.lifecycle.Transformations
import com.google.gson.Gson
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.Single
import org.joda.time.DateTime
import org.sagebionetworks.bridge.android.manager.AppConfigManager
import org.sagebionetworks.bridge.android.manager.SurveyManager
import org.sagebionetworks.bridge.android.manager.models.ProfileDataManager
import org.sagebionetworks.bridge.android.manager.models.ProfileDataSource
import org.sagebionetworks.bridge.android.manager.models.getSurveyReference
import org.sagebionetworks.bridge.rest.RestUtils
import org.sagebionetworks.bridge.rest.model.AppConfig
import org.sagebionetworks.bridge.rest.model.Survey
import org.sagebionetworks.bridge.rest.model.SurveyReference

abstract class ResourceRepository<T: Any> constructor(
        protected val resourceDao: ResourceEntityDao) : BaseRepository() {


    protected fun replaceResourceInRoom(resourceEntity: ResourceEntity): Completable {
        return Completable.fromAction {
            resourceDao.upsert(resourceEntity)
        }
        .doOnError {
            logger.warn(it.localizedMessage)
        }
    }

    protected fun storeResourceInRoom(resource: T, identifier: String, resourceType: ResourceEntity.ResourceType): Completable {
        val json = RestUtils.GSON.toJson(resource, resource::class.java)
        val resourceEntity = ResourceEntity(identifier, resourceType, json, System.currentTimeMillis())
        return replaceResourceInRoom(resourceEntity)
    }

    companion object {
        const val defaultUpdateFrequency = 60000 * 60
    }


}

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

class SurveyRepository(resourceDao: ResourceEntityDao, val surveyManager: SurveyManager): ResourceRepository<Survey>(resourceDao) {


    fun getSurvey(surveyReference: SurveyReference): Single<Survey> {
        return getSurvey(surveyReference.guid, surveyReference.createdOn).firstOrError()
    }

    private fun getSurvey(guid: String, createdOn: DateTime): Flowable<Survey> {
            return resourceDao.getResource(guid).filter {
                if (it.isEmpty() || it.get(0).lastUpdateTime + defaultUpdateFrequency < System.currentTimeMillis()) {
                    subscribeCompletable(getRemoteSurvey(guid, createdOn), "Get survey succeeded", "Get survey failed")
                }
                !it.isEmpty()
            }.map { it.get(0).loadResource(Survey::class.java)}
    }



    private fun getRemoteSurvey(guid: String, createdOn: DateTime): Completable {
        val surveySingle = toV2SingleAsync(surveyManager.getSurvey(guid, createdOn))
        return surveySingle.observeOn(asyncScheduler)
                .flatMapCompletable {
                    storeSurveyInRoom(it)
                }
                .doOnError {
                    logger.warn("Failed to fetch reports from bridge")
                }
    }

    private fun storeSurveyInRoom(survey: Survey): Completable {
        return storeResourceInRoom(survey, survey.guid, ResourceEntity.ResourceType.SURVEY)
    }


}