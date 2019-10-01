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

}

data class ProfileSection(val map: Map<String, Any?>) : ProfileTableItem {
    override val type: String by map
    override val title: String by map
    private val items: List<Map<String, Any?>> by map

    val profileTableItems: List<ProfileTableItem> = items.mapNotNull { decodeProfileItem(it) }

    private fun decodeProfileItem(itemMap: Map<String, Any?>): ProfileTableItem? {
        val type = itemMap.get("type")
        //val map = itemMap.withDefault { if ("readonly" == it) true else null }
        when (type) {
            "profileItem" -> {
                return ProfileItemProfileTableItem(itemMap)
            }
            "profileView" -> {
                return ProfileViewProfileTableView(itemMap)
            }
            "settings" -> {
                return SettingsProfileTableItem(itemMap)
            }
            "permissions" -> {
                return PermissionsProfileTableItem(itemMap)
            }
            "html" -> {
                return HtmlProfileTableItem(itemMap)
            }
            "studyParticipation" -> {
                return StudyParticipationProfileTableItem(itemMap)
            }

            else -> {
                //Error unknown type
                return null
            }

        }
    }

}

interface ProfileTableItem {
    val type: String
    val title: String
}

data class ProfileItemProfileTableItem(val map: Map<String, Any?>) : ProfileTableItem {
    override val type: String by map
    override val title: String by map
    val profileItemKey: String by map
    val notInCohorts: List<String> by map
}

data class HtmlProfileTableItem(val map: Map<String, Any?>) : ProfileTableItem {
    override val type: String by map
    override val title: String by map
    val htmlResource: String by map
    val notInCohorts: List<String> by map
}

data class ProfileViewProfileTableView(val map: Map<String, Any?>) : ProfileTableItem {
    override val type: String by map
    override val title: String by map
    val icon: String by map
    val profileDataSource: String by map
}

data class SettingsProfileTableItem(val map: Map<String, Any?>) : ProfileTableItem {
    override val type: String by map
    override val title: String by map
    val setting: String by map
}

data class PermissionsProfileTableItem(val map: Map<String, Any?>) : ProfileTableItem {
    override val type: String by map
    override val title: String by map
    val permissionType: String by map
}

data class StudyParticipationProfileTableItem(val map: Map<String, Any?>) : ProfileTableItem {
    override val type: String by map
    override val title: String by map
    val notInCohorts: List<String> by map
}
