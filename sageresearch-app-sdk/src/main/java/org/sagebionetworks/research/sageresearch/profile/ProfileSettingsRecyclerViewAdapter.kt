package org.sagebionetworks.research.sageresearch.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import kotlinx.android.synthetic.main.fragment_profilesettings_title_details_row.view.*
import org.joda.time.DateTime
import org.joda.time.LocalDate
import org.sagebionetworks.bridge.android.manager.models.*
import org.sagebionetworks.bridge.researchstack.BridgeDataProvider
import org.sagebionetworks.bridge.rest.model.SurveyReference
import org.sagebionetworks.research.sageresearch.profile.ProfileSettingsRecyclerViewAdapter.Companion.VIEW_TYPE_SECTION
import org.sagebionetworks.research.sageresearch.profile.ProfileSettingsRecyclerViewAdapter.Companion.VIEW_TYPE_TITLE_DETAILS
import org.sagebionetworks.research.sageresearch_app_sdk.R
import org.sagebionetworks.researchstack.backbone.ResourceManager
import org.sagebionetworks.researchstack.backbone.ResourcePathManager


class ProfileSettingsRecyclerViewAdapter
    : androidx.recyclerview.widget.RecyclerView.Adapter<ProfileSettingsRecyclerViewAdapter.ViewHolder> {

    constructor(
            dataPair: Pair<ProfileDataSource?, ProfileDataLoader?>,
            listener: OnListInteractionListener) : super() {
        mDataPair = dataPair
        mOnClickListener = View.OnClickListener { v ->
            val item = v.tag as ProfileRow
            item.onClick(listener)
        }
    }

    companion object {
        const val VIEW_TYPE_SECTION = 0
        const val VIEW_TYPE_TITLE_DETAILS = 1
        const val VIEW_TYPE_ICON_TITLE = 2

    }

    private lateinit var mValues: List<ProfileRow>
    private val mOnClickListener: View.OnClickListener
    private var mDataPair: Pair<ProfileDataSource?, ProfileDataLoader?>
        set(value) {
            field = value
            val dataGroups = value.second?.participantData?.dataGroups ?: listOf()
            mValues = value.first?.filteredProfileItemList(dataGroups)?.map { value.second?.let{ second -> ProfileRow.createProfileRow(it, second)}?: return }
                    ?: listOf()

        }

    fun updateDataLoader(loader: Pair<ProfileDataSource?, ProfileDataLoader?>) {
        mDataPair = loader
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        when (viewType) {
            VIEW_TYPE_SECTION -> {
                return ViewHolder(inflater.inflate(R.layout.fragment_profilesettings_section_row, parent, false))
            }
            VIEW_TYPE_ICON_TITLE -> {
                return ViewHolder(inflater.inflate(R.layout.fragment_profilesettings_icon_row, parent, false))
            }
            else -> {
                return ViewHolder(inflater.inflate(R.layout.fragment_profilesettings_title_details_row, parent, false))
            }

        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = mValues[position]
        holder.mTitle.text = item.title
        holder.mDetails.text = item.detail
        holder.mTitle.visibility = if (holder.mTitle.text == null) View.GONE else View.VISIBLE
        holder.mDetails.visibility = if (holder.mDetails.text == null) View.GONE else View.VISIBLE
        holder.chevron.visibility = if (item.isClickable()) View.VISIBLE else View.GONE
        holder.mView.isClickable = item.isClickable()
        with(holder.mView) {
            tag = item
            setOnClickListener(mOnClickListener)
        }
    }

    override fun getItemCount(): Int = mValues.size

    override fun getItemViewType(position: Int): Int {
        return mValues[position].viewType
    }

    inner class ViewHolder(val mView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(mView) {
        val mTitle: TextView = mView.item_text
        val mDetails: TextView = mView.item_detail
        val chevron: View = mView.chevron
    }
}

interface OnListInteractionListener {
    fun launchSurvey(surveyReference: SurveyReference)
    fun startActivity(intent: Intent)
    fun getContext(): Context?
    fun launchStudyBurstReminderTime()
    fun launchEditProfileItemDialog(value: String, profileItemKey: String)
    fun launchWithdraw(firstName: String, joinedDate: DateTime)
    fun launchEditParticipantItem(profileItem: ProfileItemProfileTableItem, profileDataItem: ProfileDataItem)
    fun launchPassiveDataAllowed(profileItem: ProfileItemProfileTableItem, profileDataItem: ProfileDataItem,
                                 value: String?)
}

abstract class ProfileRow {
    enum class TYPE {
        SECTION, PROFILE_ITEM, PROFILE_VIEW
    }

    open val type = TYPE.PROFILE_ITEM
    open val viewType = VIEW_TYPE_TITLE_DETAILS
    abstract val title: String
    open val detail: String? = null
    open fun isClickable(): Boolean {
        return true
    }

    open fun onClick(listener: OnListInteractionListener) {}

    companion object {

        fun createProfileRow(profileItem: ProfileTableItem, profileDataLoader: ProfileDataLoader): ProfileRow {
            when (profileItem) {
                is ProfileSection -> return SectionRow(profileItem)
                is ProfileItemProfileTableItem -> {
                    val dataDef = profileDataLoader.getDataDef(profileItem.profileItemKey)
                    when (dataDef) {
                        is ReportProfileDataItem -> return ReportProfileItemRow(dataDef, profileItem, profileDataLoader)
                        is ParticipantProfileDataItem -> return ParticipantProfileItemRow(profileItem, profileDataLoader)
                        else -> return DisplayOnlyRow(profileItem)
                    }
                }
                is ProfileViewProfileTableView -> return ProfileViewRow(profileItem)
                is HtmlProfileTableItem -> return HtmlRow(profileItem)
                is StudyParticipationProfileTableItem -> return StudyParticipationProfileItemRow(profileItem, profileDataLoader)
                is SettingsProfileTableItem -> return SettingsRow(profileItem, profileDataLoader)
                is DownloadDataProfileTableItem -> return DownloadDataRow(profileItem, profileDataLoader)
                is EmailProfileTableItem -> return EmailRow(profileItem)
                else -> return DisplayOnlyRow(profileItem)
            }
        }
    }
}

class DisplayOnlyRow(val profileItem: ProfileTableItem) : ProfileRow() {
    override val title = profileItem.title
    override fun isClickable(): Boolean {
        return false
    }
}

class DownloadDataRow(val profileItem: ProfileTableItem, val profileDataLoader: ProfileDataLoader) : ProfileRow() {
    override val title = profileItem.title
    override fun onClick(listener: OnListInteractionListener) {
        BridgeDataProvider.getInstance().downloadData(profileDataLoader.participantData.createdOn.toLocalDate(), LocalDate.now())
    }
}

class SectionRow(val profileItem: ProfileSection) : ProfileRow() {
    override val type = TYPE.SECTION
    override val viewType = VIEW_TYPE_SECTION
    override val title = profileItem.title
    override fun isClickable(): Boolean {
        return false
    }
}

class StudyParticipationProfileItemRow(val profileItem: StudyParticipationProfileTableItem, val profileDataLoader: ProfileDataLoader) : ProfileRow() {
    override val title = profileItem.title
    override val detail: String?
        get() = if (BridgeDataProvider.getInstance().isConsented) "Enrolled in mPower study" else "Rejoin mPower study"

    override fun onClick(listener: OnListInteractionListener) {
        listener.launchWithdraw(profileDataLoader.participantData.firstName
                ?: "", profileDataLoader.participantData.createdOn)
    }
}

abstract class ProfileItemRow(val profileItem: ProfileItemProfileTableItem, val profileDataLoader: ProfileDataLoader) : ProfileRow() {
    override val title = profileItem.title

    override val detail: String?
        get() {
            var value = profileDataLoader.getValueString(profileItem.profileItemKey)
            if (value != null) {
                value = profileItem.valueMap?.get(value) ?: value
            }
            return value ?: ""
        }

}

class ReportProfileItemRow(val reportProfileDataItem: ReportProfileDataItem,
                           profileItem: ProfileItemProfileTableItem,
                           profileDataLoader: ProfileDataLoader) : ProfileItemRow(profileItem, profileDataLoader) {

    override val detail: String?
        get() {
            var value = profileDataLoader.getValueString(profileItem.profileItemKey)
            if (value != null) {
                value = when (profileItem.profileItemKey) {
                    "passiveDataAllowed" -> if (value.toBoolean()) "Enabled" else "Disabled"
                    else -> profileItem.valueMap?.get(value) ?: value
                }
            } else if (profileItem.profileItemKey == "passiveDataAllowed") {
                return "Unavailable"
            }
            return value ?: ""
        }

    override fun onClick(listener: OnListInteractionListener) {
        val surveyRef = reportProfileDataItem.surveyReference
        if (surveyRef != null) {
            listener.launchSurvey(surveyRef)
        } else {
            when (profileItem.profileItemKey) {
                "passiveDataAllowed" -> profileDataLoader.getDataDef(profileItem.profileItemKey)?.let {
                    listener.launchPassiveDataAllowed(profileItem,
                            it,
                            profileDataLoader.getValueString(profileItem.profileItemKey))
                }
            }
        }
    }
}

class ParticipantProfileItemRow(profileItem: ProfileItemProfileTableItem, profileDataLoader: ProfileDataLoader) : ProfileItemRow(profileItem, profileDataLoader) {

    override fun isClickable(): Boolean {
        when (profileItem.profileItemKey) {
            "externalId" -> return false
        }
        return true
    }

    override fun onClick(listener: OnListInteractionListener) {
        when (profileItem.profileItemKey) {
            "firstName" -> listener.launchEditProfileItemDialog(detail
                    ?: "", profileItem.profileItemKey)
            "sharingScope" -> profileDataLoader.getDataDef(profileItem.profileItemKey)?.let{
                listener.launchEditParticipantItem(profileItem, it)
            }
        }
    }
}

class SettingsRow(val profileItem: SettingsProfileTableItem, val profileDataLoader: ProfileDataLoader) : ProfileRow() {
    override val title = profileItem.title

    val setting = profileItem.setting

    override fun onClick(listener: OnListInteractionListener) {
        when (setting) {
            "studyBurstTime" -> return listener.launchStudyBurstReminderTime()
            else -> return

        }
    }

    override val detail: String?
        get() {
            val value = profileDataLoader.getValueString(profileItem.profileItemKey)
            return value ?: ""
        }

}


class HtmlRow(val profileItem: HtmlProfileTableItem) : ProfileRow() {
    override val title = profileItem.title
    override val detail = profileItem.detail
    val htmlResource: ResourcePathManager.Resource?
        get() {
            when (profileItem.htmlResource) {
                "Licenses" -> return ResourceManager.getInstance().getLicense()
                "consent" -> return ResourceManager.getInstance().consentHtml
                "PrivacyPolicy" -> return ResourceManager.getInstance().privacyPolicy
                "StudyInformation" -> return ResourceManager.getInstance().studyOverview
                else -> return null
            }
        }

    override fun onClick(listener: OnListInteractionListener) {
        val path = htmlResource?.absolutePath
        val intent = org.sagebionetworks.researchstack.backbone.ui.ViewWebDocumentActivity.newIntentForPath(listener.getContext(),
                "", path, true)
        listener.startActivity(intent)
    }
}

class EmailRow(val profileItem: EmailProfileTableItem) : ProfileRow() {
    override val title = profileItem.title

    override fun onClick(listener: OnListInteractionListener) {
        val subject = title
        val message = ""
        val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts(
                "mailto", profileItem.email, null))
        intent.putExtra(Intent.EXTRA_SUBJECT, subject)
        intent.putExtra(Intent.EXTRA_TEXT, message)
        listener.startActivity(Intent.createChooser(intent, "Choose an Email client :"))
    }
}

class ProfileViewRow(val profileItem: ProfileViewProfileTableView) : ProfileRow() {
    override val title = profileItem.title
    override fun onClick(listener: OnListInteractionListener) {}
}
