package org.sagebionetworks.research.sageresearch.dao.room

import androidx.room.TypeConverter
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import com.google.gson.TypeAdapter

import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.sagebionetworks.bridge.rest.RestUtils

import org.sagebionetworks.bridge.rest.gson.ByteArrayToBase64TypeAdapter
import org.sagebionetworks.bridge.rest.gson.DateTimeTypeAdapter
import org.sagebionetworks.bridge.rest.gson.LocalDateTypeAdapter
import org.sagebionetworks.bridge.rest.model.Activity
import org.sagebionetworks.bridge.rest.model.ActivityType
import org.sagebionetworks.bridge.rest.model.ReportData
import org.sagebionetworks.bridge.rest.model.ScheduleStatus
import org.sagebionetworks.bridge.rest.model.ScheduledActivity
import org.sagebionetworks.bridge.rest.model.ScheduledActivityListV4
import org.sagebionetworks.bridge.rest.model.TaskReference
import org.sagebionetworks.research.sageresearch.dao.room.EntityTypeConverters.Companion.logger
import org.sagebionetworks.research.sageresearch.extensions.toJodaDateTime
import org.sagebionetworks.research.sageresearch.extensions.toJodaLocalDate
import org.sagebionetworks.research.sageresearch.extensions.toThreeTenInstant
import org.sagebionetworks.research.sageresearch.extensions.toThreeTenLocalDate
import org.slf4j.LoggerFactory
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate

import org.threeten.bp.LocalDateTime
import org.threeten.bp.LocalTime
import org.threeten.bp.ZonedDateTime
import org.threeten.bp.format.DateTimeFormatter
import java.io.IOException
import java.io.Serializable

//
//  Copyright © 2018 Sage Bionetworks. All rights reserved.
//
// Redistribution and use in source and binary forms, with or without modification,
// are permitted provided that the following conditions are met:
//
// 1.  Redistributions of source code must retain the above copyright notice, this
// list of conditions and the following disclaimer.
//
// 2.  Redistributions in binary form must reproduce the above copyright notice,
// this list of conditions and the following disclaimer in the documentation and/or
// other materials provided with the distribution.
//
// 3.  Neither the name of the copyright holder(s) nor the names of any contributors
// may be used to endorse or promote products derived from this software without
// specific prior written permission. No license is granted to the trademarks of
// the copyright holders even if such marks are included in this software.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
// FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
// DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
// SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
// CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
// OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
//

/**
 * This class controls how objects are converted to and from data types supported by SqlLite
 * Room recognizes which class types can be converted by
 * the @TypeConverter annotation, and inferred by the method structure
 */
class EntityTypeConverters {

    companion object {
        val logger = LoggerFactory.getLogger(EntityTypeConverters::class.java)
        /**
         * Short-cuts to extension functionality because Java doesn't support extensions
         */
        @JvmStatic
        fun bridgeMetaDataSchedule(fromEntity: ScheduledActivityEntity): ScheduledActivity {
            return fromEntity.bridgeMetadataCopy()
        }

        /**
         * Short-cuts to extension functionality because Java doesn't support extensions
         */
        @JvmStatic
        fun clientWritableSchedule(fromEntity: ScheduledActivityEntity): ScheduledActivity {
            return fromEntity.clientWritableCopy()
        }
    }

    val bridgeGsonBuilder = GsonBuilder()
            .registerTypeAdapter(ByteArray::class.java, ByteArrayToBase64TypeAdapter())
            .registerTypeAdapter(org.joda.time.LocalDate::class.java, LocalDateTypeAdapter())
            .registerTypeAdapter(DateTime::class.java, DateTimeTypeAdapter())
            .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeAdapter())
            .registerTypeAdapter(ZonedDateTime::class.java, ZonedDateTimeAdapter())
            .registerTypeAdapter(LocalDate::class.java, LocalDateAdapter())
            .registerTypeAdapter(Instant::class.java, InstantAdapter())

    val bridgeGson = bridgeGsonBuilder.create()

    private val schemaRefListType = object : TypeToken<List<RoomSchemaReference>>() {}.type
    private val surveyRefListType = object : TypeToken<List<RoomSurveyReference>>() {}.type

    @TypeConverter
    fun fromResourceTypeString(value: String): ResourceEntity.ResourceType {
        return ResourceEntity.ResourceType.valueOf(value)
    }

    @TypeConverter
    fun fromResourceType(value: ResourceEntity.ResourceType): String {
        return value.name
    }

    @TypeConverter
    fun fromLocalDateString(value: String?): LocalDate? {
        val valueChecked = value ?: return null
        return LocalDate.parse(valueChecked)
    }

    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? {
        val valueChecked = value ?: return null
        return valueChecked.toString()
    }

    @TypeConverter
    fun fromLocalDateTimeString(value: String?): LocalDateTime? {
        val valueChecked = value ?: return null
        return LocalDateTime.parse(valueChecked)
    }

    @TypeConverter
    fun fromLocalDateTime(value: LocalDateTime?): String? {
        val valueChecked = value ?: return null
        return valueChecked.toString()
    }

    @TypeConverter
    fun fromInstant(value: Long?): Instant? {
        val valueChecked = value ?: return null
        return Instant.ofEpochMilli(valueChecked)
    }

    @TypeConverter
    fun fromInstantTimestamp(value: Instant?): Long? {
        val valueChecked = value ?: return null
        return valueChecked.toEpochMilli()
    }

    @TypeConverter
    fun fromLocalTime(value: Long?): LocalTime? {
        val valueChecked = value ?: return null
        return LocalTime.ofNanoOfDay(valueChecked)
    }

    @TypeConverter
    fun fromLocalTimeStamp(value: LocalTime?): Long? {
        val valueChecked = value ?: return null
        return valueChecked.toNanoOfDay()
    }

    @TypeConverter
    fun toClientData(value: String?): ClientData? {
        val valueChecked = value ?: return null
        return bridgeGson.fromJson(valueChecked, ClientData::class.java)
    }

    @TypeConverter
    fun fromClientData(value: ClientData?): String? {
        val valueChecked = value ?: return null
        return bridgeGson.toJson(valueChecked)
    }

    @TypeConverter
    fun toScheduleStatus(value: String?): ScheduleStatus? {
        val valueChecked = value ?: return null
        return ScheduleStatus.fromValue(valueChecked)
    }

    @TypeConverter
    fun fromActivityType(value: ActivityType?): String? {
        val valueCheck = value ?: return null
        return valueCheck.toString()
    }

    @TypeConverter
    fun toActivityType(value: String?): ActivityType? {
        val valueChecked = value ?: return null
        return ActivityType.fromValue(valueChecked)
    }

    @TypeConverter
    fun fromScheduleStatus(value: ScheduleStatus?): String? {
        val valueCheck = value ?: return null
        return valueCheck.toString()
    }

    @TypeConverter
    fun toSchemaReferenceList(value: String?): List<RoomSchemaReference>? {
        val valueChecked = value ?: return null
        return bridgeGson.fromJson<List<RoomSchemaReference>>(valueChecked, schemaRefListType)
    }

    @TypeConverter
    fun fromSchemaReferenceList(value: List<RoomSchemaReference>?): String? {
        val valueCheck = value ?: return null
        return bridgeGson.toJson(valueCheck, schemaRefListType)
    }

    @TypeConverter
    fun toSurveyReferenceList(value: String?): List<RoomSurveyReference>? {
        val valueChecked = value ?: return null
        return bridgeGson.fromJson<List<RoomSurveyReference>>(valueChecked, surveyRefListType)
    }

    @TypeConverter
    fun fromRoomSurveyReferenceList(refList: List<RoomSurveyReference>?): String? {
        return bridgeGson.toJson(refList, surveyRefListType)
    }

    fun fromScheduledActivityListV4(value: ScheduledActivityListV4?): List<ScheduledActivityEntity>? {
        val valueChecked = value ?: return null
        val activities = ArrayList<ScheduledActivityEntity>()
        for (scheduledActivity in valueChecked.items) {
            val roomActivity = bridgeGson.fromJson(
                    bridgeGson.toJson(scheduledActivity), ScheduledActivityEntity::class.java)
            scheduledActivity.clientData.let {
                roomActivity.clientData = ClientData(it)
            }
            activities.add(roomActivity)
        }
        return activities
    }
}

/**
 * A client writable copy is considered a new object with only the properties set on the object
 * that are writable on bridge.  All other fields sent to bridge will be ignored anyways.
 * @return a ScheduledActivity that can be sent to bridge
 */
fun ScheduledActivityEntity.clientWritableCopy(): ScheduledActivity {
    val schedule = ScheduledActivity()
    schedule.guid = guid
    schedule.startedOn = startedOn?.let { org.joda.time.DateTime(it.toEpochMilli()) }
    schedule.finishedOn = finishedOn?.let { org.joda.time.DateTime(it.toEpochMilli()) }
    schedule.clientData = clientData?.data
    return schedule
}

/**
 * A bridge metadata copy is considered a new object with only the properties set on the object
 * that the ArchiveUtil.createMetaDataFile() function is concerned with.
 * @return a ScheduledActivity that can be pass to the BridgeDataProvider and have the metadata JSON included.
 */
fun ScheduledActivityEntity.bridgeMetadataCopy(): ScheduledActivity {
    var schedule = ScheduledActivity()
    val immutableFieldMap = HashMap<String, Any>()

    scheduledOn?.let {
        val dateTime = it.toJodaDateTime()
        immutableFieldMap.put("scheduledOn", dateTime)
    }

    schedulePlanGuid?.let {
        immutableFieldMap.put("schedulePlanGuid", it)
    }

    activity?.task?.let {
        val bridgeActivity = Activity()
        bridgeActivity.label = activity?.label
        bridgeActivity.task = TaskReference()
        bridgeActivity.task?.identifier = activityIdentifier()
        immutableFieldMap.put("activity", bridgeActivity)
    }

    if (immutableFieldMap.isNotEmpty()) {
        // TODO: mdephillips 10/14/18 sync with josh or dwayne to make scheduledOn and schedulePlanGuid mutable
        // TODO: mdephillips 10/14/18 so I don't have to do this json non-sense
        val scheduleJsonTree = RestUtils.GSON.toJsonTree(ScheduledActivity()) as JsonObject
        immutableFieldMap.forEach { e ->
            if (e.value is String) {
                scheduleJsonTree.addProperty(e.key, e.value as String)
            } else {
                scheduleJsonTree.add(e.key, RestUtils.GSON.toJsonTree(e.value))
            }
        }

        try {
            schedule = RestUtils.toType(scheduleJsonTree, ScheduledActivity::class.java)
        } catch (e: JsonSyntaxException) {
            logger.error("Error deserializing ScheduledActivity: $scheduleJsonTree", e)
        }
    }

    schedule.guid = guid
    schedule.startedOn = startedOn?.let { org.joda.time.DateTime(it.toEpochMilli()) }
    schedule.finishedOn = finishedOn?.let { org.joda.time.DateTime(it.toEpochMilli()) }

    return schedule
}

/**
 * @return ReportData that can be consumed by bridge
 */
fun ReportData.entityCopy(reportIdentifier: String): ReportEntity {
    return ReportEntity(
            identifier = reportIdentifier,
            data = if (this.data != null) ClientData(this.data) else null,
            dateTime = this.dateTime?.toThreeTenInstant(),
            localDate = this.localDate?.toThreeTenLocalDate())
}

/**
 * @return ReportData that can be consumed by bridge
 */
fun ReportEntity.bridgeCopy(): ReportData {
    val report = ReportData()
    this.dateTime?.let {
        report.dateTime = DateTime(it.toEpochMilli(), DateTimeZone.UTC)
    }
    this.localDate?.let {
        report.localDate = it.toJodaLocalDate()
    }
    report.data = this.data?.data
    return report
}

/**
 * 'LocalDateTimeAdapter' is needed when going from
 * ScheduledActivity.scheduledOn: DateTime to ScheduledActivityEntity.scheduledOn: LocalDateTime
 */
class LocalDateTimeAdapter : TypeAdapter<LocalDateTime>() {

    @Throws(IOException::class)
    override fun read(reader: JsonReader): LocalDateTime {
        val src = reader.nextString()
        return LocalDateTime.parse(src, DateTimeFormatter.ISO_DATE_TIME)
    }

    @Throws(IOException::class)
    override fun write(writer: JsonWriter, date: LocalDateTime?) {
        if (date != null) {
            writer.value(DateTimeFormatter.ISO_DATE_TIME.format(date))
        } else {
            writer.nullValue()
        }
    }
}

/**
 * 'InstantAdapter' is needed when going from
 * ScheduledActivity.finishedOn: DateTime to ScheduledActivityEntity.finishedOn: Instant
 */
class InstantAdapter : TypeAdapter<Instant>() {

    @Throws(IOException::class)
    override fun read(reader: JsonReader): Instant {
        val src = reader.nextString()
        return Instant.ofEpochMilli(DateTime.parse(src).toDate().time)
    }

    @Throws(IOException::class)
    override fun write(writer: JsonWriter, instant: Instant?) {
        if (instant != null) {
            writer.value(DateTime(instant.toEpochMilli()).toString())
        } else {
            writer.nullValue()
        }
    }
}

/**
 * 'LocalDateTimeAdapter' is needed when going from
 * ScheduledActivity.scheduledOn: DateTime to ScheduledActivityEntity.scheduledOn: LocalDateTime
 */
class LocalDateAdapter : TypeAdapter<LocalDate>() {

    @Throws(IOException::class)
    override fun read(reader: JsonReader): LocalDate {
        val src = reader.nextString()
        return LocalDate.parse(src)
    }

    @Throws(IOException::class)
    override fun write(writer: JsonWriter, date: LocalDate?) {
        date?.let {
            writer.value(it.toString())
        } ?: run {
            writer.nullValue()
        }
    }
}

/**
 * 'ZonedDateTimeAdapter' is needed when going from client data zoned date time to a iso date format
 */
class ZonedDateTimeAdapter : TypeAdapter<ZonedDateTime>() {

    @Throws(IOException::class)
    override fun read(reader: JsonReader): ZonedDateTime {
        val src = reader.nextString()
        return ZonedDateTime.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(src))
    }

    @Throws(IOException::class)
    override fun write(writer: JsonWriter, date: ZonedDateTime?) {
        date?.let {
            writer.value(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(it))
        } ?: run {
            writer.nullValue()
        }
    }
}

/**
 * Client data wrapper class to allow for custom read/write for Room Any type objects
 */
data class ClientData(var data: Any? = null) : Serializable

/**
 * @param key for retrieving the value if client data is a map
 * @return the value for the map key if client data is a map, null otherwise
 */
fun ClientData.mapValue(key: String): Any? {
    (this.data as? Map<*, *>)?.forEach { it ->
        if (it.key == key) {
            return it.value
        }
    }
    return null
}

@Suppress("UNCHECKED_CAST")
fun <T> ClientData.mapValue(key: String, type: Class<T>): T? {
    (this.data as? Map<*, *>)?.forEach { entry ->
        if (entry.key == key) {
            (entry.value as? T)?.let {
                return it
            }
        }
    }
    return null
}