package org.sagebionetworks.research.sageresearch.dao.room

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Insert
import android.arch.persistence.room.OnConflictStrategy
import android.arch.persistence.room.Query
import io.reactivex.Maybe
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.util.UUID
import javax.inject.Qualifier
import kotlin.annotation.AnnotationRetention.RUNTIME

@Dao
interface TaskRunDAO {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(taskRun: TaskRunEntity)

    @Insert
    fun insert(taskRunScheduledActivityJoin: TaskRunScheduledActivityJoin)

    @Query("SELECT * FROM taskrunentity WHERE uuid = :taskRunUUID")
    fun getTaskRun(taskRunUUID: UUID): Maybe<TaskRunEntity>

    @Query("SELECT * FROM scheduledactivityentity " +
            "INNER JOIN taskrun_scheduledactivity_join ON scheduledActivityGuid = scheduledactivityentity.guid " +
            "WHERE taskRunUUID = :taskRunUUID")
    fun getScheduledActivityForTaskRun(taskRunUUID: UUID): Maybe<ScheduledActivityEntity>
}