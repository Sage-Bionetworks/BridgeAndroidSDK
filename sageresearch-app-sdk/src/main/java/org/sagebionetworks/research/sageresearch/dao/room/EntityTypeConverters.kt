package org.sagebionetworks.research.sageresearch.dao.room

import android.arch.persistence.room.TypeConverter
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSyntaxException
import com.google.gson.TypeAdapter

import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import org.joda.time.DateTime
import org.joda.time.LocalDate
import org.joda.time.format.ISODateTimeFormat
import org.researchstack.backbone.utils.LogExt
import org.researchstack.backbone.utils.ResUtils
import org.sagebionetworks.bridge.rest.RestUtils

import org.sagebionetworks.bridge.rest.gson.ByteArrayToBase64TypeAdapter
import org.sagebionetworks.bridge.rest.gson.DateTimeTypeAdapter
import org.sagebionetworks.bridge.rest.gson.LocalDateTypeAdapter
import org.sagebionetworks.bridge.rest.model.Activity
import org.sagebionetworks.bridge.rest.model.ActivityType
import org.sagebionetworks.bridge.rest.model.ScheduleStatus
import org.sagebionetworks.bridge.rest.model.ScheduledActivity
import org.sagebionetworks.bridge.rest.model.ScheduledActivityListV4
import org.sagebionetworks.bridge.rest.model.TaskReference
import org.sagebionetworks.research.domain.result.interfaces.TaskResult
import org.slf4j.LoggerFactory
import org.threeten.bp.Instant

import org.threeten.bp.LocalDateTime
import org.threeten.bp.format.DateTimeFormatter
import java.io.IOException
import java.lang.reflect.Type
import java.util.UUID

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

    private val logger = LoggerFactory.getLogger(EntityTypeConverters::class.java)

    companion object {
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

    val bridgeGson = GsonBuilder()
            .registerTypeAdapter(ByteArray::class.java, ByteArrayToBase64TypeAdapter())
            .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
            .registerTypeAdapter(DateTime::class.java, DateTimeTypeAdapter())
            .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeAdapter())
            .registerTypeAdapter(Instant::class.java, InstantAdapter())
            .create()

    private val schemaRefListType = object : TypeToken<List<RoomSchemaReference>>() {}.type
    private val surveyRefListType = object : TypeToken<List<RoomSurveyReference>>() {}.type

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
        var activities = ArrayList<ScheduledActivityEntity>()
        for (scheduledActivity in valueChecked.items) {
            var roomActivity = bridgeGson.fromJson(
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
    val immutableFieldMap = HashMap<String, String>()

    scheduledOn?.let {
        // TODO: mdephillips 10/14/18 better way to convert from 3tenbp LocalDateTime to jodatime DateTime?
        val dateTime = DateTime.now()
                .withYear(it.year)
                .withDayOfYear(it.dayOfYear)
                .withHourOfDay(it.hour)
                .withMinuteOfHour(it.minute)
                .withSecondOfMinute(it.second)
        val dateTimeStr = RestUtils.GSON.toJson(dateTime)
        immutableFieldMap.put("scheduledOn", dateTimeStr)
    }

    schedulePlanGuid?.let {
        immutableFieldMap.put("schedulePlanGuid", it)
    }

    if (immutableFieldMap.isNotEmpty()) {
        // TODO: mdephillips 10/14/18 sync with josh or dwayne to make scheduledOn and schedulePlanGuid mutable
        // TODO: mdephillips 10/14/18 so I don't have to do this json non-sense
        var scheduleJson = "{"
        immutableFieldMap.keys.forEachIndexed { i, s ->
            scheduleJson = "$scheduleJson \"$s\" : \"${immutableFieldMap[s]}\""
            if (i < (immutableFieldMap.keys.size - 1)) {
                scheduleJson = "$scheduleJson,"
            }
        }
        scheduleJson = "$scheduleJson}"
        try {
            schedule = RestUtils.GSON.fromJson(scheduleJson, ScheduledActivity::class.java)
        } catch (e: JsonSyntaxException) {
            LogExt.e(ScheduledActivityEntity::class.java, e)
        }
    }

    schedule.guid = guid
    schedule.startedOn = startedOn?.let { org.joda.time.DateTime(it.toEpochMilli()) }
    schedule.finishedOn = finishedOn?.let { org.joda.time.DateTime(it.toEpochMilli()) }

    activity?.task?.let {
        val bridgeActivity = Activity()
        bridgeActivity.label = activity?.label
        bridgeActivity.task = TaskReference()
        bridgeActivity.task?.identifier = activityIdentifier()
    }

    return schedule
}

/**
 * 'LocalDateTimeAdapter' is needed when going from
 * ScheduledActivity.scheduledOn: DateTime to ScheduledActivityEntity.scheduledOn: LocalDateTime
 */
class LocalDateTimeAdapter: TypeAdapter<LocalDateTime>() {
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
class InstantAdapter: TypeAdapter<Instant>() {
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