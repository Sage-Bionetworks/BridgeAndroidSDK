package org.sagebionetworks.research.sageresearch.profile

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.LiveDataReactiveStreams
import android.arch.lifecycle.MediatorLiveData
import org.sagebionetworks.bridge.android.BridgeApplication
import org.sagebionetworks.bridge.android.manager.models.*
import org.sagebionetworks.bridge.rest.model.StudyParticipant
import org.sagebionetworks.research.sageresearch.dao.room.ReportEntity
import org.sagebionetworks.research.sageresearch.dao.room.ReportRepository
import org.sagebionetworks.research.sageresearch.dao.room.mapValue
import rx.RxReactiveStreams
import rx.Single
import javax.inject.Inject

class ProfileManager(val reportRepo: ReportRepository) {


    fun loadProfileDataSources() : LiveData<Map<String, ProfileDataSource>> {

        val profileDataSourceLiveData = LiveDataReactiveStreams.fromPublisher(RxReactiveStreams.toPublisher(BridgeApplication.getBridgeManagerProvider().appConfigManager.profileDataSources))
        return profileDataSourceLiveData
    }

    private fun loadDefaultProfileDataManager() : Single<ProfileDataManager> {
        return BridgeApplication.getBridgeManagerProvider().appConfigManager.profileDataManagers.map { it.get("ProfileManager")  }
    }

    private fun loadDefaultProfileManagerLiveData() : LiveData<ProfileDataManager> {
        return LiveDataReactiveStreams.fromPublisher(RxReactiveStreams.toPublisher(loadDefaultProfileDataManager()))
    }

    private fun loadParticipantRecord() : Single<StudyParticipant> {
        return BridgeApplication.getBridgeManagerProvider().participantManager.participantRecord
    }

    private fun loadParticipantRecordLiveData(): LiveData<StudyParticipant> {
        return LiveDataReactiveStreams.fromPublisher(RxReactiveStreams.toPublisher(loadParticipantRecord()))
    }

    private fun loadReports(reportKeys: Set<String>): LiveData<Map<String, ReportEntity?>> {
        val mediator = MediatorLiveData<Map<String, ReportEntity?>>().apply {
            val numSources = reportKeys.size
            val reportMap: MutableMap<String, ReportEntity?> = mutableMapOf()

            fun update() {
                if (numSources == reportMap.size) {
                    this.value = reportMap
                }
            }

            for (key in reportKeys) {
                val reportLiveData = reportRepo.fetchMostRecentReport(key)
                addSource(reportLiveData) {
                    val report = it?.firstOrNull()
                    reportMap.put(key, report)
                    update()
                }

            }

        }
        return mediator
    }

    fun profileDataLoader(): LiveData<ProfileDataLoader> {
        val mediator = MediatorLiveData<ProfileDataLoader>().apply {
            var profileDataManager: ProfileDataManager? = null
            var participantRecord: StudyParticipant? = null
            var reportMap: Map<String, ReportEntity?>? = null

            fun update() {
                if (profileDataManager != null && participantRecord != null && reportMap != null) {
                    this.value = ProfileDataLoader(profileDataManager!!, participantRecord!!, reportMap!!)
                }
            }

            addSource(loadDefaultProfileManagerLiveData()) {
                profileDataManager = it
                addSource(loadReports(it!!.reportIds)) {
                    reportMap = it
                    update()
                }
                update()
            }

            addSource(loadParticipantRecordLiveData()) {
                participantRecord = it
                update()
            }
        }
        return mediator
    }


}

class ProfileDataLoader(val profileDataDef: ProfileDataManager, val participantData: StudyParticipant, val reports: Map<String, ReportEntity?>) {

    fun getValue(profileKey: String): Any? {
        val profileDataItem: ProfileDataItem? = profileDataDef.profileDataMap.get(profileKey)

        when (profileDataItem) {
            is ReportProfileDataItem -> {
                val report = reports.get(profileDataItem.demographicSchema)
                return report?.data?.mapValue(profileDataItem.sourceKey)
            }
            is ParticipantClientDataProfileDataItem -> {
                participantData.clientData
                when(profileDataItem.fallbackKeyPath) {
                    "firstName" -> return participantData.firstName
                }

            }
        }


        return null
    }

}


