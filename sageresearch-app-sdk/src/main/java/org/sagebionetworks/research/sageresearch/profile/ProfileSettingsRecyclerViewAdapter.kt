package org.sagebionetworks.research.sageresearch.profile

import android.content.Context
import android.content.Intent
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import kotlinx.android.synthetic.main.fragment_profilesettings_title_details_row.view.*
import org.sagebionetworks.bridge.android.manager.models.*
import org.sagebionetworks.bridge.researchstack.BridgeDataProvider
import org.sagebionetworks.bridge.rest.model.SurveyReference
import org.sagebionetworks.research.sageresearch.profile.ProfileSettingsRecyclerViewAdapter.Companion.VIEW_TYPE_ICON_TITLE
import org.sagebionetworks.research.sageresearch.profile.ProfileSettingsRecyclerViewAdapter.Companion.VIEW_TYPE_SECTION
import org.sagebionetworks.research.sageresearch.profile.ProfileSettingsRecyclerViewAdapter.Companion.VIEW_TYPE_TITLE_DETAILS
import org.sagebionetworks.research.sageresearch_app_sdk.R
import org.sagebionetworks.researchstack.backbone.ResourceManager
import org.sagebionetworks.researchstack.backbone.ResourcePathManager

class ProfileSettingsRecyclerViewAdapter(
        private val dataPair: Pair<ProfileDataSource?, ProfileDataLoader?>,
        private val mListener: OnListInteractionListener)
    : RecyclerView.Adapter<ProfileSettingsRecyclerViewAdapter.ViewHolder>() {

    companion object {
        const val VIEW_TYPE_SECTION = 0
        const val VIEW_TYPE_TITLE_DETAILS = 1
        const val VIEW_TYPE_ICON_TITLE = 2

    }

    private val dataGroups = dataPair.second?.participantData?.dataGroups?: listOf()
    private val mValues = dataPair.first?.filteredProfileItemList(dataGroups)?.map { ProfileRow.createProfileRow(it, dataPair.second!!) } ?: listOf()

    private val mOnClickListener: View.OnClickListener

    init {
        mOnClickListener = View.OnClickListener { v ->
            val item = v.tag as ProfileRow
            item.onClick(mListener)
        }
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
        holder.mTitle.visibility = if (item.title == null) View.GONE else View.VISIBLE
        holder.mDetails.visibility = if (item.detail == null) View.GONE else View.VISIBLE

        with(holder.mView) {
            tag = item
            setOnClickListener(mOnClickListener)
        }
    }

    override fun getItemCount(): Int = mValues.size

    override fun getItemViewType(position: Int): Int {
        return mValues[position].viewType
    }

    inner class ViewHolder(val mView: View) : RecyclerView.ViewHolder(mView) {
        val mTitle: TextView = mView.item_text
        val mDetails: TextView = mView.item_detail
    }
}

interface OnListInteractionListener {
    fun launchSurvey(surveyReference: SurveyReference)
    fun startActivity(intent: Intent)
    fun getContext(): Context?
}

abstract class ProfileRow {
    enum class TYPE {
        SECTION, PROFILE_ITEM, PROFILE_VIEW
    }
    abstract val type: TYPE
    abstract val viewType: Int
    abstract val title: String
    open val detail: String? = null
    abstract fun onClick(listener: OnListInteractionListener)

    companion object {

        fun createProfileRow(profileItem: ProfileTableItem, profileDataLoader: ProfileDataLoader): ProfileRow {
            when(profileItem) {
                is ProfileSection ->  return SectionRow(profileItem)
                is ProfileItemProfileTableItem -> {
                    val dataDef = profileDataLoader.getDataDef(profileItem.profileItemKey)
                    when (dataDef) {
                        is ReportProfileDataItem -> return ReportProfileItemRow(dataDef,profileItem, profileDataLoader)
                        is ParticipantProfileDataItem -> return ParticipantProfileItemRow(profileItem, profileDataLoader)
                        else -> return DisplayOnlyRow(profileItem)
                    }
                }
                is ProfileViewProfileTableView -> return ProfileViewRow(profileItem)
                is HtmlProfileTableItem -> return HtmlRow(profileItem)
                is StudyParticipationProfileTableItem -> return StudyParticipationProfileItemRow(profileItem)
                else -> return DisplayOnlyRow(profileItem)
            }
        }
    }
}

class DisplayOnlyRow(val profileItem: ProfileTableItem): ProfileRow() {
    override val type = TYPE.PROFILE_ITEM
    override val viewType = VIEW_TYPE_TITLE_DETAILS
    override val title = profileItem.title
    override fun onClick(listener: OnListInteractionListener) {}
}

class SectionRow(val profileItem: ProfileSection): ProfileRow() {
    override val type = TYPE.SECTION
    override val viewType = VIEW_TYPE_SECTION
    override val title = profileItem.title
    override fun onClick(listener: OnListInteractionListener) {}
}

class StudyParticipationProfileItemRow(val profileItem: StudyParticipationProfileTableItem): ProfileRow() {
    override val type = TYPE.PROFILE_ITEM
    override val viewType = VIEW_TYPE_TITLE_DETAILS
    override val title = profileItem.title
    override val detail: String?
        get() = if (BridgeDataProvider.getInstance().isConsented) "Enrolled in mPower study" else "Rejoin mPower study"
    override fun onClick(listener: OnListInteractionListener) {}
}

abstract class ProfileItemRow(val profileItem: ProfileItemProfileTableItem, val profileDataLoader: ProfileDataLoader): ProfileRow() {
    override val type = TYPE.PROFILE_ITEM
    override val viewType = VIEW_TYPE_TITLE_DETAILS
    override val title = profileItem.title

    override val detail: String?
        get() {
            val value = profileDataLoader.getValue(profileItem.profileItemKey)
            return value?.toString()?: ""
        }

}

class ReportProfileItemRow(val reportProfileDataItem: ReportProfileDataItem,
                           profileItem: ProfileItemProfileTableItem,
                           profileDataLoader: ProfileDataLoader) : ProfileItemRow(profileItem, profileDataLoader) {

    override fun onClick(listener: OnListInteractionListener) {
        val surveyRef = reportProfileDataItem.surveyReference
        if (surveyRef != null) {
            listener.launchSurvey(surveyRef)
        }
    }
}

class ParticipantProfileItemRow(profileItem: ProfileItemProfileTableItem, profileDataLoader: ProfileDataLoader) : ProfileItemRow(profileItem, profileDataLoader) {

    override fun onClick(listener: OnListInteractionListener) {}
}

class HtmlRow(val profileItem: HtmlProfileTableItem): ProfileRow() {
    override val type = TYPE.PROFILE_ITEM
    override val viewType = VIEW_TYPE_TITLE_DETAILS
    override val title = profileItem.title
    override val detail = profileItem.detail
    val htmlResource: ResourcePathManager.Resource?
        get() {
            when(profileItem.htmlResource) {
                "Licenses" -> return ResourceManager.getInstance().getLicense()
                "consent" -> return ResourceManager.getInstance().consentHtml
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

class ProfileViewRow(val profileItem: ProfileViewProfileTableView): ProfileRow() {
    override val type = TYPE.PROFILE_VIEW
    override val viewType = VIEW_TYPE_ICON_TITLE
    override val title = profileItem.title
    override fun onClick(listener: OnListInteractionListener) {}
}
