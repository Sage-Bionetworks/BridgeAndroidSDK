package org.sagebionetworks.research.sageresearch.dao.room

import android.support.test.runner.AndroidJUnit4
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.sagebionetworks.research.sageresearch.viewmodel.RoomTestHelper


@RunWith(AndroidJUnit4::class)
class ResourceEntityDaoTests: RoomTestHelper() {

    companion object {
        const val RESOURCE_ID = "testId"
        val resourceEntity = ResourceEntity(RESOURCE_ID,
                ResourceEntity.ResourceType.APP_CONFIG,
                "this should be json",
                0)
    }

    @Before
    fun setupForEachTestWithEmptyDatabase() {
        resourceDao.clear()
    }

    @Test
    fun test_upsert_and_get() {
        resourceDao.upsert(resourceEntity)
        Assert.assertEquals(resourceEntity, getValue(resourceDao.getResource(RESOURCE_ID)))
    }

    @Test
    fun test_clear() {
        resourceDao.upsert(resourceEntity)
        resourceDao.clear()
        Assert.assertNull(getValue(resourceDao.getResource(RESOURCE_ID)))
    }
//    @Rule
//    var instantTaskExecutorRule = InstantTaskExecutorRule()
//
//
//    private lateinit var resourceDao: ResourceEntityDao
//    private lateinit var db: ResearchDatabase
//
//    @Before
//    fun createDb() {
//        val context = ApplicationProvider.getApplicationContext<Context>()
//        db = Room.inMemoryDatabaseBuilder(context, ResearchDatabase::class.java)
//                .allowMainThreadQueries()
//                .build()
//        resourceDao = db.resourceDao()
//    }
//
//    @After
//    @Throws(IOException::class)
//    fun closeDb() {
//        db.close()
//    }


}