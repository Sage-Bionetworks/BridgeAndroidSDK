package org.sagebionetworks.research.sageresearch.profile

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MediatorLiveData
import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import com.google.common.base.Preconditions
import org.sagebionetworks.bridge.android.manager.models.ProfileDataManager
import org.sagebionetworks.bridge.android.manager.models.ProfileDataSource
import org.sagebionetworks.bridge.rest.model.StudyParticipant
import org.sagebionetworks.research.sageresearch.dao.room.ReportEntity
import org.sagebionetworks.research.sageresearch.dao.room.ReportRepository
import javax.inject.Inject

open class ProfileViewModel(reportRepo: ReportRepository):
        ViewModel(), ProfileViewModelInterface by ProfileViewModelBaseImplementation(reportRepo) {

    class Factory @Inject constructor(private val reportRepo: ReportRepository): ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            Preconditions.checkArgument(modelClass.isAssignableFrom(ProfileViewModel::class.java))
            return ProfileViewModel(reportRepo) as T
        }
    }

}

interface ProfileViewModelInterface {
    val reportRepo: ReportRepository

    fun profileDataLoader(): LiveData<ProfileDataLoader>
    fun defaultProfileData(): LiveData<Pair<ProfileDataSource?, ProfileDataLoader?>>
    fun profileData(profileKey: String): LiveData<Pair<ProfileDataSource?, ProfileDataLoader?>>

}

class ProfileViewModelBaseImplementation(override val reportRepo: ReportRepository): ProfileViewModelInterface {

    val profileManager = ProfileManager(reportRepo)


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

}