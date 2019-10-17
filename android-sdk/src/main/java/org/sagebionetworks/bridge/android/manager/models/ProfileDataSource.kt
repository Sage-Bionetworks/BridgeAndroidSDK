package org.sagebionetworks.bridge.android.manager.models

data class ProfileDataSource(val map: Map<String, Any?>) {
    val catType:    String by map
    val type:       String by map
    val identifier: String by map
    private val sections:   List<Map<String, Any?>> by map

    val profileSections: List<ProfileSection> = sections.map { ProfileSection(it.withDefault { null }) }

    private var _profileItemList: List<ProfileTableItem>? = null
    val profileItemsList: List<ProfileTableItem>
        get() {
            if (_profileItemList == null) {
                val list = mutableListOf<ProfileTableItem>()
                for (section in profileSections) {
                    list.add(section)
                    list.addAll(section.profileTableItems)
                }
                _profileItemList = list
            }
            return _profileItemList as List<ProfileTableItem>
        }

    fun filteredProfileItemList(userDataGroups: List<String>): List<ProfileTableItem> {
        return profileItemsList.filter { it.shouldShow(userDataGroups) }

    }

}

data class ProfileSection(val map: Map<String, Any?>) : ProfileTableItem {
    override val type: String by map
    override val title: String by map
    override val hideOnAndroid: Boolean by map
    override val notInCohorts: List<String> by map
    override val inCohorts: List<String> by map
    private val items: List<Map<String, Any?>> by map

    val profileTableItems: List<ProfileTableItem> = items.mapNotNull { decodeProfileItem(it) }

    private fun decodeProfileItem(itemMap: Map<String, Any?>): ProfileTableItem? {
        val type = itemMap.get("type")
        val defaultMap = itemMap.withDefault { key ->  if (key == "hideOnAndroid") false else null }
        //val map = itemMap.withDefault { if ("readonly" == it) true else null }
        var profileItem: ProfileTableItem? = null
        when (type) {
            "profileItem" -> {
                profileItem = ProfileItemProfileTableItem(defaultMap)
            }
            "profileView" -> {
                profileItem =  ProfileViewProfileTableView(defaultMap)
            }
            "settings" -> {
                profileItem =  SettingsProfileTableItem(defaultMap)
            }
            "permissions" -> {
                profileItem =  PermissionsProfileTableItem(defaultMap)
            }
            "html" -> {
                profileItem =  HtmlProfileTableItem(defaultMap)
            }
            "studyParticipation" -> {
                profileItem =  StudyParticipationProfileTableItem(defaultMap)
            }

            else -> {
                //Error unknown type
                profileItem =  null
            }

        }
        if (profileItem?.hideOnAndroid == true) return null
        return profileItem
    }

}

interface ProfileTableItem {
    val type: String
    val title: String
    val hideOnAndroid: Boolean
    val notInCohorts: List<String>?
    val inCohorts: List<String>?
    fun shouldShow(userDataGroups: List<String>) : Boolean {
        if (notInCohorts == null && inCohorts == null) {
            return true
        }
        val mustBeIn = inCohorts?.toSet()?: setOf<String>()
        val mustNotBeIn = notInCohorts?.toSet()?: setOf<String>()
        return (userDataGroups.containsAll(mustBeIn) &&
                !userDataGroups.containsAll(mustNotBeIn))

    }
}

data class ProfileItemProfileTableItem(val map: Map<String, Any?>) : ProfileTableItem {
    override val type: String by map
    override val title: String by map
    override val hideOnAndroid: Boolean by map
    val profileItemKey: String by map
    override val notInCohorts: List<String> by map
    override val inCohorts: List<String> by map
}

data class HtmlProfileTableItem(val map: Map<String, Any?>) : ProfileTableItem {
    override val type: String by map
    override val title: String by map
    override val hideOnAndroid: Boolean by map
    val detail: String? by map
    val htmlResource: String by map
    override val notInCohorts: List<String> by map
    override val inCohorts: List<String> by map
}

data class ProfileViewProfileTableView(val map: Map<String, Any?>) : ProfileTableItem {
    override val type: String by map
    override val title: String by map
    override val hideOnAndroid: Boolean by map
    val icon: String by map
    val profileDataSource: String by map
    override val notInCohorts: List<String> by map
    override val inCohorts: List<String> by map
}

data class SettingsProfileTableItem(val map: Map<String, Any?>) : ProfileTableItem {
    override val type: String by map
    override val title: String by map
    override val hideOnAndroid: Boolean by map
    val setting: String by map
    override val notInCohorts: List<String> by map
    override val inCohorts: List<String> by map
}

data class PermissionsProfileTableItem(val map: Map<String, Any?>) : ProfileTableItem {
    override val type: String by map
    override val title: String by map
    override val hideOnAndroid: Boolean by map
    val permissionType: String by map
    override val notInCohorts: List<String> by map
    override val inCohorts: List<String> by map
}

data class StudyParticipationProfileTableItem(val map: Map<String, Any?>) : ProfileTableItem {
    override val type: String by map
    override val title: String by map
    override val hideOnAndroid: Boolean by map
    override val notInCohorts: List<String> by map
    override val inCohorts: List<String> by map
}
