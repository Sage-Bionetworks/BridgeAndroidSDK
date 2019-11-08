package org.sagebionetworks.research.sageresearch.repos

import org.sagebionetworks.research.sageresearch.dao.room.ReportRepository
import org.sagebionetworks.research.sageresearch.dao.room.ScheduleRepository
import org.sagebionetworks.research.sageresearch.dao.room.ScheduledActivityEntity
import org.sagebionetworks.researchstack.backbone.result.TaskResult
import org.threeten.bp.Instant

/**
 * This class encapsulates business logic that require calls to multiple repositories.
 */
class BridgeRepositoryManager(val scheduleRepo: ScheduleRepository,
                              val reportRepo: ReportRepository) {

    fun saveTaskResult(taskResult: TaskResult, scheduledActivityEntity: ScheduledActivityEntity?) {

        scheduledActivityEntity?.startedOn = Instant.ofEpochMilli(taskResult.getStartDate().getTime())
        scheduledActivityEntity?.finishedOn = Instant.ofEpochMilli(taskResult.getEndDate().getTime())
        scheduledActivityEntity?.let {
            // This function updates the schedule on bridge and in the ScheduleRepository
            scheduleRepo.updateScheduleToBridge(it)
        }
        // This function uploads the result of the task to S3
        scheduleRepo.uploadResearchStackTaskResultToS3(scheduledActivityEntity, taskResult)
        // This function will generate a client data report for the research stack task result
        reportRepo.saveResearchStackReports(taskResult)
    }

}