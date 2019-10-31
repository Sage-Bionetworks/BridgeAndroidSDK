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

/**
 * A generalized repository for storing and retrieving objects as JSON blobs.
 */
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