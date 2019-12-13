/*
 * BSD 3-Clause License
 *
 * Copyright 2019  Sage Bionetworks. All rights reserved.
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

package org.sagebionetworks.research.sageresearch.dao.room

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.Observer
import androidx.paging.toLiveData
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.sagebionetworks.research.sageresearch.viewmodel.RoomTestHelper
import org.threeten.bp.Instant

@RunWith(AndroidJUnit4::class)
class HistoryItemEntityDaoTests: RoomTestHelper() {

    inline fun <reified T> lambdaMock(): T = mock(T::class.java)

    companion object {
        const val RESOURCE_ID = "testId"
        val historyItemEntity = HistoryItemEntity("testType", "jsonString", "testReportId", "2019-11-29", Instant.now())
        val historyItems = listOf<HistoryItemEntity>(
                HistoryItemEntity("testType", "item4", "testReportId", "2019-11-28", Instant.now()),
                HistoryItemEntity("testType", "item1", "testReportId", "2019-11-30", Instant.now()),
                HistoryItemEntity("testType", "item2", "testReportId", "2019-11-30", Instant.now().plusMillis(1000)),
                HistoryItemEntity("testType", "item3", "testReportId", "2019-11-29", Instant.now())
        )
    }

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Before
    fun setupForEachTestWithEmptyDatabase() {
        historyDao.clear()
    }

    @Test
    fun test_upsert_and_get() {
        for (item in historyItems) {
            historyDao.upsert(item)
        }

        historyDao.historyItems()

        val value = getValue(historyDao.historyItems().toLiveData(10))
        Assert.assertEquals(historyItems.size, value.size)
        Assert.assertEquals("item1", value[0]?.dataJson)
        Assert.assertEquals("item2", value[1]?.dataJson)
        Assert.assertEquals("item3", value[2]?.dataJson)
        Assert.assertEquals("item4", value[3]?.dataJson)


    }




}