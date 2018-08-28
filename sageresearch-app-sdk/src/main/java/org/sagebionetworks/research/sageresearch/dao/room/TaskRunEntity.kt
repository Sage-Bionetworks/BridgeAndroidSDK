package org.sagebionetworks.research.sageresearch.dao.room;

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.ForeignKey
import android.arch.persistence.room.ForeignKey.CASCADE
import android.arch.persistence.room.ForeignKey.RESTRICT
import android.arch.persistence.room.PrimaryKey
import org.sagebionetworks.research.domain.result.interfaces.TaskResult
import java.util.UUID

@Entity
data class TaskRunEntity(@PrimaryKey var uuid: UUID) {

    var taskResult: TaskResult? = null
}

@Entity(tableName = "taskrun_scheduledactivity_join",
        primaryKeys = ["taskRunUUID", "scheduledActivityGuid"],
        foreignKeys = [
            ForeignKey(entity = ScheduledActivityEntity::class,
                    parentColumns = ["guid"],
                    childColumns = ["scheduledActivityGuid"],
                    onDelete = RESTRICT),
            ForeignKey(entity = TaskRunEntity::class,
                    parentColumns = ["uuid"],
                    childColumns = ["taskRunUUID"],
                    onDelete = CASCADE)
        ])
data class TaskRunScheduledActivityJoin(@ColumnInfo(index = true) var taskRunUUID: UUID,
        @ColumnInfo(index = true) var scheduledActivityGuid: String) {
}