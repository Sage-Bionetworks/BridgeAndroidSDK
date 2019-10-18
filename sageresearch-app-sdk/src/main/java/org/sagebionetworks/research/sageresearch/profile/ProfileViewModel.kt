package org.sagebionetworks.research.sageresearch.profile

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MediatorLiveData
import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import com.google.common.base.Preconditions
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import org.sagebionetworks.bridge.android.manager.models.ProfileDataManager
import org.sagebionetworks.bridge.android.manager.models.ProfileDataSource
import org.sagebionetworks.bridge.rest.model.StudyParticipant
import org.sagebionetworks.bridge.rest.model.Survey
import org.sagebionetworks.bridge.rest.model.SurveyReference
import org.sagebionetworks.research.sageresearch.dao.room.*
import org.sagebionetworks.research.sageresearch.repos.BridgeRepositoryManager
import org.sagebionetworks.researchstack.backbone.task.SmartSurveyTask
import javax.inject.Inject


open class ProfileViewModel(val bridgeRepoManager: BridgeRepositoryManager, val reportRepo: ReportRepository, val appConfigRepo: AppConfigRepository, val surveyRepo: SurveyRepository): ViewModel() {

    val profileManager = ProfileManager(reportRepo, appConfigRepo)

    val compositeDisposable = io.reactivex.disposables.CompositeDisposable()

    var currentScheduledActivity: ScheduledActivityEntity? = null

    fun profileDataLoader(): LiveData<ProfileDataLoader> {
        return profileManager.profileDataLoader()
    }

    fun profileDataSource(): LiveData<Map<String, ProfileDataSource>> {
        return profileManager.loadProfileDataSources()
    }

    fun defaultProfileData(): LiveData<Pair<ProfileDataSource?, ProfileDataLoader?>> {
        return profileData("ProfileDataSource")
    }

    fun profileData(key: String): LiveData<Pair<ProfileDataSource?, ProfileDataLoader?>> {

        val mediator = MediatorLiveData<Pair<ProfileDataSource?, ProfileDataLoader?>>().apply {
            var profileDataLoader: ProfileDataLoader? = null
            var profileDataSource: ProfileDataSource? = null

            fun update() {
                if (profileDataLoader != null && profileDataSource != null) {
                    this.value = Pair(profileDataSource, profileDataLoader)
                }
            }

            addSource(profileDataSource()) {
                if (it != null) {
                    profileDataSource = it.get(key)
                }
                //TODO: Handle error case where profileDataSource is null
                update()
            }

            addSource(profileDataLoader()) {
                profileDataLoader = it
                update()
            }
        }
        return mediator

    }

    fun loadSurvey(surveyReference: SurveyReference): Single<Survey> {
        return surveyRepo.getSurvey(surveyReference).firstOrError().doOnSuccess { loadScheduledActivity(it.identifier) }
    }

    private fun loadScheduledActivity(surveyId: String) {
        compositeDisposable.add(bridgeRepoManager.scheduleRepo.scheduleDao.activityGroupFlowable(setOf(surveyId))
                .firstOrError()
                .subscribe({currentScheduledActivity = it.firstOrNull()},{"fail"})
        )
    }

    override fun onCleared() {
        super.onCleared()
        compositeDisposable.dispose()
    }


}