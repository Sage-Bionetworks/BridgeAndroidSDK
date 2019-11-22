package org.sagebionetworks.research.sageresearch.dao.room

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.reactivex.Flowable

@Dao
interface ResourceEntityDao {


    /**
     * Get the resource specified by the given identifier.
     * @return - a list that will either contain 1 resource or empty if not found
     */
    @Query(RoomSql.SELECT_RESOURCE_BY_IDENTIFIER)
    fun getResource(resourceIdentifier: String, resourceType: ResourceEntity.ResourceType): Flowable<List<ResourceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(resourceEntity: ResourceEntity)

    /**
     * Deletes all rows in the table.  To be called on sign out or a cache clear.
     */
    @Query(RoomSql.RESOURCE_DELETE)
    fun clear()

}