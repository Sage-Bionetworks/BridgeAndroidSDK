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

package org.sagebionetworks.research.sageresearch.extensions

import android.content.Context
import android.content.res.Resources
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.sagebionetworks.research.sageresearch_app_sdk.R

class ListExtensionsTests {

    companion object {
        lateinit var context: Context
        lateinit var resources: Resources

        @BeforeClass
        @JvmStatic
        fun setup() {
            resources = mock(Resources::class.java)
            `when`(resources.getString(R.string.list_format_delimiter)).thenReturn(", ")
            `when`(resources.getString(R.string.two_item_list_and_format)).thenReturn("%1\$s and %2\$s")
            `when`(resources.getString(R.string.three_item_list_and_format)).thenReturn("%1\$s, %2\$s, and %3\$s")
            `when`(resources.getString(R.string.two_item_list_or_format)).thenReturn("%1\$s or %2\$s")
            `when`(resources.getString(R.string.three_item_list_or_format)).thenReturn("%1\$s, %2\$s, or %3\$s")
            context = mock(Context::class.java)
            `when`(context.resources).thenReturn(resources)
            `when`(context.getString(R.string.list_format_delimiter)).thenReturn(", ")
            `when`(context.getString(R.string.two_item_list_and_format)).thenReturn("%1\$s and %2\$s")
            `when`(context.getString(R.string.three_item_list_and_format)).thenReturn("%1\$s, %2\$s, and %3\$s")
            `when`(context.getString(R.string.two_item_list_or_format)).thenReturn("%1\$s or %2\$s")
            `when`(context.getString(R.string.three_item_list_or_format)).thenReturn("%1\$s, %2\$s, or %3\$s")
        }
    }

    @Test
    fun testlocalizedJoin_0Items() {
        val list = listOf<String>()
        assertEquals("", list.localizedAndJoin(context))
        assertEquals("", list.localizedOrJoin(context))
    }

    @Test
    fun testlocalizedJoin_1Item() {
        val list = listOf("Apples")
        assertEquals("Apples", list.localizedAndJoin(context))
        assertEquals("Apples", list.localizedOrJoin(context))
    }

    @Test
    fun testlocalizedJoin_2Items() {
        val list = listOf("Apples", "Oranges")
        assertEquals("Apples and Oranges", list.localizedAndJoin(context))
        assertEquals("Apples or Oranges", list.localizedOrJoin(context))
    }

    @Test
    fun testlocalizedJoin_3Items() {
        val list = listOf("Apples", "Oranges", "Bananas")
        assertEquals("Apples, Oranges, and Bananas", list.localizedAndJoin(context))
        assertEquals("Apples, Oranges, or Bananas", list.localizedOrJoin(context))
    }

    @Test
    fun testlocalizedJoin_4Items() {
        val list = listOf("Apples", "Oranges", "Bananas", "Grapes")
        assertEquals("Apples, Oranges, Bananas, and Grapes", list.localizedAndJoin(context))
        assertEquals("Apples, Oranges, Bananas, or Grapes", list.localizedOrJoin(context))
    }
}