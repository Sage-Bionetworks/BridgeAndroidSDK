package org.sagebionetworks.research.sageresearch.dao.room

import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import org.joda.time.DateTime
import org.sagebionetworks.bridge.android.manager.SurveyManager
import org.sagebionetworks.bridge.rest.model.Survey
import org.sagebionetworks.bridge.rest.model.SurveyReference

class SurveyRepository(resourceDao: ResourceEntityDao, val surveyManager: SurveyManager): ResourceRepository<Survey>(resourceDao) {


    fun getSurvey(surveyReference: SurveyReference): Single<Survey> {
        return getSurvey(surveyReference.guid, surveyReference.createdOn).firstOrError()
    }

    private fun getSurvey(guid: String, createdOn: DateTime): Flowable<Survey> {
        return resourceDao.getResource(guid, ResourceEntity.ResourceType.SURVEY).filter {
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