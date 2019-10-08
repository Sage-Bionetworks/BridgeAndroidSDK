package org.sagebionetworks.research.sageresearch.profile

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MediatorLiveData
import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import com.google.common.base.Preconditions
import io.reactivex.Flowable
import org.sagebionetworks.bridge.android.manager.models.ProfileDataManager
import org.sagebionetworks.bridge.android.manager.models.ProfileDataSource
import org.sagebionetworks.bridge.rest.model.StudyParticipant
import org.sagebionetworks.bridge.rest.model.Survey
import org.sagebionetworks.bridge.rest.model.SurveyReference
import org.sagebionetworks.research.sageresearch.dao.room.AppConfigRepository
import org.sagebionetworks.research.sageresearch.dao.room.ReportEntity
import org.sagebionetworks.research.sageresearch.dao.room.ReportRepository
import org.sagebionetworks.research.sageresearch.dao.room.SurveyRepository
import org.sagebionetworks.researchstack.backbone.task.SmartSurveyTask
import javax.inject.Inject

open class ProfileViewModel(reportRepo: ReportRepository, appConfigRepo: AppConfigRepository, surveyRepo: SurveyRepository):
        ViewModel(), ProfileViewModelInterface by ProfileViewModelBaseImplementation(reportRepo, appConfigRepo, surveyRepo) {

    class Factory @Inject constructor(private val reportRepo: ReportRepository, private val appConfigRepo: AppConfigRepository, private val surveyRepo: SurveyRepository): ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            Preconditions.checkArgument(modelClass.isAssignableFrom(ProfileViewModel::class.java))
            return ProfileViewModel(reportRepo, appConfigRepo, surveyRepo) as T
        }
    }

}

interface ProfileViewModelInterface {
    val reportRepo: ReportRepository

    fun profileDataLoader(): LiveData<ProfileDataLoader>
    fun defaultProfileData(): LiveData<Pair<ProfileDataSource?, ProfileDataLoader?>>
    fun profileData(profileKey: String): LiveData<Pair<ProfileDataSource?, ProfileDataLoader?>>
    fun loadSurvey(surveyReference: SurveyReference): Flowable<Survey>

}

class ProfileViewModelBaseImplementation(override val reportRepo: ReportRepository, val appConfigRepo: AppConfigRepository, val surveyRepo: SurveyRepository): ProfileViewModelInterface {

    val profileManager = ProfileManager(reportRepo, appConfigRepo)


    override fun profileDataLoader(): LiveData<ProfileDataLoader> {
        return profileManager.profileDataLoader()
    }

    fun profileDataSource(): LiveData<Map<String, ProfileDataSource>> {
        return profileManager.loadProfileDataSources()
    }

    override fun defaultProfileData(): LiveData<Pair<ProfileDataSource?, ProfileDataLoader?>> {
        return profileData("ProfileDataSource")
    }

    override fun profileData(key: String): LiveData<Pair<ProfileDataSource?, ProfileDataLoader?>> {

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

    override fun loadSurvey(surveyReference: SurveyReference): Flowable<Survey> {
        return surveyRepo.getSurvey(surveyReference)
    }



}