package org.sagebionetworks.research.sageresearch.dao.room

import android.arch.persistence.room.Database
import android.arch.persistence.room.RoomDatabase
import android.arch.persistence.room.TypeConverters

@Database(entities = arrayOf(ScheduledActivityEntity::class),
        version = 1)
@TypeConverters(EntityTypeConverters::class)
abstract class ResearchDatabase : RoomDatabase() {

    abstract fun activitiesDao(): RoomScheduledActivityDao
}