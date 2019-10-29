package org.sagebionetworks.research.sageresearch.profile

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.LiveDataReactiveStreams
import android.arch.lifecycle.MediatorLiveData
import hu.akarnokd.rxjava.interop.RxJavaInterop.toV2Single
import io.reactivex.Single
import org.sagebionetworks.bridge.android.BridgeApplication
import org.sagebionetworks.bridge.android.manager.models.*
import org.sagebionetworks.bridge.researchstack.BridgeDataProvider
import org.sagebionetworks.bridge.rest.model.StudyParticipant
import org.sagebionetworks.research.sageresearch.dao.room.AppConfigRepository
import org.sagebionetworks.research.sageresearch.dao.room.ReportEntity
import org.sagebionetworks.research.sageresearch.dao.room.ReportRepository
import org.sagebionetworks.research.sageresearch.dao.room.mapValue
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

/**
 * The ProfileManager loads ProfileDataSources and a ProfileDataManager from AppConfig, useing the data
 * from these it loads the necessary Reports, StudyParticipant and returns a ProfileDataLoader.
 */
class ProfileManager(val reportRepo: ReportRepository, val appConfigRepo: AppConfigRepository) {


    fun loadProfileDataSources() : LiveData<Map<String, ProfileDataSource>> {
        val profileDataSourceLiveData = LiveDataReactiveStreams.fromPublisher(appConfigRepo.profileDataSources.toFlowable())
        return profileDataSourceLiveData
    }

    private fun loadDefaultProfileDataManager() : Single<ProfileDataManager> {
        return appConfigRepo.profileDataManagers.map { it.get("ProfileManager")  }
    }

    private fun loadDefaultProfileManagerLiveData() : LiveData<ProfileDataManager> {
        return LiveDataReactiveStreams.fromPublisher(loadDefaultProfileDataManager().toFlowable())
    }

    private fun loadParticipantRecord() : Single<StudyParticipant> {
        return toV2Single(BridgeApplication.getBridgeManagerProvider().participantManager.participantRecord)
    }

    private fun loadParticipantRecordLiveData(): LiveData<StudyParticipant> {
        return LiveDataReactiveStreams.fromPublisher(loadParticipantRecord().toFlowable())
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

    private val timeDisplayFormatter = SimpleDateFormat("h:mm aaa", Locale.getDefault())
    private val dateFormatter = SimpleDateFormat("HH:mm:00.000", Locale.getDefault())

    fun getValueString(profileKey: String): String? {
        val profileDataItem: ProfileDataItem? = profileDataDef.profileDataMap.get(profileKey)
        val value = getValue(profileDataItem)
        when(profileDataItem?.itemType) {
            "time" -> {
                if (value is String) {
                    return timeDisplayFormatter.format(dateFormatter.parse(value))
                }
            }
            "year" -> {
                if (value is Double) {
                    return value.roundToInt().toString()
                }
            }
            "multipleChoice.string" -> {
                if (value is List<*>) {
                    return value.joinToString(separator = ", ")
                }
            }
        }

        return value?.toString()
    }


    private fun getValue(profileDataItem: ProfileDataItem?): Any? {
        when (profileDataItem) {
            is ReportProfileDataItem -> {
                if (profileDataItem.dataGroups != null) {
                    return profileDataItem.dataGroups?.intersect(participantData.dataGroups)?.joinToString(separator = ",")
                } else {

                    val report = reports.get(profileDataItem.demographicSchema)
                    return report?.data?.mapValue(profileDataItem.profileKey)
                }
            }
            is ParticipantProfileDataItem -> {
                when(profileDataItem.profileKey) {
                    "firstName" -> return participantData.firstName
                    "externalId" -> return participantData.externalId
                    "sharingScope" -> return participantData.sharingScope.value
                }

            }
        }

        return null
    }


    fun getDataDef(profileKey: String): ProfileDataItem? {
        return profileDataDef.profileDataMap[profileKey]
    }

}


