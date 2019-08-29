/*
 * BSD 3-Clause License
 *
 * Copyright 2018  Sage Bionetworks. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1.  Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2.  Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3.  Neither the name of the copyright holder(s) nor the names of any contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission. No license is granted to the trademarks of
 * the copyright holders even if such marks are included in this software.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.sagebionetworks.research.sageresearch.viewmodel

import android.os.Build
import com.google.common.collect.ImmutableList
import org.joda.time.DateTime
import org.sagebionetworks.researchstack.backbone.result.Result
import org.sagebionetworks.bridge.android.BridgeConfig
import org.sagebionetworks.bridge.android.manager.upload.ArchiveUtil
import org.sagebionetworks.bridge.data.Archive
import org.sagebionetworks.bridge.data.JsonArchiveFile
import org.sagebionetworks.bridge.researchstack.TaskHelper
import org.sagebionetworks.bridge.researchstack.factory.ArchiveFileFactory
import org.sagebionetworks.bridge.rest.RestUtils
import org.sagebionetworks.research.sageresearch.dao.room.ScheduledActivityEntity
import org.sagebionetworks.research.sageresearch.dao.room.bridgeMetadataCopy
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * The ResearchStackUploadArchiveFactory controls upload archive creation and allows for
 * easy sub-classing to change the functionality of the factory.
 */
open class ResearchStackUploadArchiveFactory: ArchiveFileFactory() {

    private val logger = LoggerFactory.getLogger(ResearchStackUploadArchiveFactory::class.java)

    /**
     * @param schedule of the task completed, if null, no metadata json file will be included
     * @param bridgeConfig to be used for accessing schema information for the schedule
     * @param userDataGroups the current data groups of the user
     * @param taskResult result of running the research stack task
     * @return a pair where first is the archive filename, and the second is the archive builder
     */
    open fun buildResearchStackArchive(
        schedule: ScheduledActivityEntity?,
        bridgeConfig: BridgeConfig,
        userDataGroups: ImmutableList<String>,
        userExternalId: String?,
        taskResult: org.sagebionetworks.researchstack.backbone.result.TaskResult): Pair<String, Archive.Builder>? {

        // ResearchStack tasks don't use a UUID, so just assign a random one for naming conventions
        val rsTaskUuid = UUID.randomUUID()

        var taskId = taskResult.identifier
        var archiveFilename = taskId + rsTaskUuid
        // If the schedule is a survey, we should create the survey archive builder
        val builder: Archive.Builder = schedule?.activity?.survey?.let {
            val surveyGuid = it.guid
            var surveyCreatedOn = DateTime.now()
            it.createdOn?.toEpochMilli()?.let { epochMillis ->
                surveyCreatedOn = DateTime(epochMillis)
            } ?: run {
                logger.warn("No survey createdOn date was found for the schedule, using today's date")
            }
            // task is actually a survey, so use a survey archive builder
            Archive.Builder.forSurvey(surveyGuid, surveyCreatedOn)
        } ?: run {
            // First check the task result for the proper task identifier, because that is historically where it is
            // If that isn't available for whatever reason, let's assume the schedule has the correct task identifier
            if (bridgeConfig.taskToSchemaMap[taskId] == null) {
                taskId = schedule?.activityIdentifier()
            }
            val schemaKey = bridgeConfig.taskToSchemaMap[taskId] ?: run {
                logger.error("No schema key found for task with identifier ${taskId}, skipping upload.")
                return null
            }
            archiveFilename = schemaKey.id + schemaKey.revision + rsTaskUuid
            Archive.Builder.forActivity(schemaKey.id, schemaKey.revision)
        }

        val appVersion = "version ${bridgeConfig.appVersionName}, build ${bridgeConfig.appVersion}"
        builder.withAppVersionName(appVersion).withPhoneInfo(bridgeConfig.deviceName)

        schedule?.let {
            builder.addDataFile(createMetaDataFile(it, userDataGroups, userExternalId))
        } ?: run {
            logger.warn("Failed to create metadata json file for S3 upload")
        }

        // Loop through the results and add the result files to the archive
        val flattenedResultList = TaskHelper.flattenResults(taskResult)
        addFiles(builder, flattenedResultList, taskId)

        return Pair(archiveFilename, builder)
    }

    /**
     * Creates a metadata file archive for upload to bridge
     * @param scheduledActivityEntity associated with the upload
     * @param dataGroups a list of the users' data groups
     * @param externalId if the user signed in with externalId this should be non-null, null otherwise
     */
    protected open fun createMetaDataFile(
            scheduledActivityEntity: ScheduledActivityEntity,
            dataGroups: ImmutableList<String>,
            externalId: String?): JsonArchiveFile {

        val scheduledActivity = scheduledActivityEntity.bridgeMetadataCopy()
        val metaDataMap = ArchiveUtil.createMetaDataInfoMap(scheduledActivity, dataGroups, externalId)

        // Here we can add some of our own additional metadata to the uplaod
        scheduledActivity.activity?.survey?.identifier?.let {
            // Add survey identifier as taskIdentifier even though it's a survey
            // (base implementation doesn't do this)
            metaDataMap["taskIdentifier"] = it
        }

        metaDataMap["deviceTypeIdentifier"] =
                "${Build.PRODUCT} ${Build.MODEL} OS v${android.os.Build.VERSION.SDK_INT}"

        // Grab the end date
        val endDate = scheduledActivity.finishedOn ?: DateTime.now()
        val metaDataJson = RestUtils.GSON.toJson(metaDataMap)
        return JsonArchiveFile("metadata.json", endDate, metaDataJson)
    }

    /**
     * Can be overridden by sub-class for custom data archiving
     * @param archiveBuilder fill this builder up with files from the flattenedResultList
     * @param flattenedResultList read these and add them to the archiveBuilder
     * @param taskIdentifier of the task result that contained these flattened results
     */
    protected open fun addFiles(
            archiveBuilder: Archive.Builder,
            flattenedResultList: List<Result>?,
            taskIdentifier: String) {

        flattenedResultList?.forEach { result ->
            fromResult(result)?.let {
                archiveBuilder.addDataFile(it)
            } ?: run {
                logger.error("Failed to convert Result to BridgeDataInput " + result.toString())
            }
        }
    }
}