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

package org.sagebionetworks.research.sageresearch.dao.room

import com.google.gson.Gson
import java.util.TreeMap

/**
 * Implementing ReportResultDataMap allows for Result objects to provide custom Report data maps.
 */
interface ReportResultDataMap {
    /**
     * Implementing ReportResultDataMap allows for Result objects to provide custom Report data maps.
     *
     * For example, say we have...
     * Result1 that returns mapOf(Pair("a", 1), Pair("b", 2)) from asDataMap()
     * Result2 that returns mapOf(Pair("c", 1)) from asDataMap()
     *
     * The final ReportEntity created will have it's data property look like this when converted to JSON...
     * {
     *   "a":1,
     *   "b":2,
     *   "c":1
     * }
     *
     * If your custom Result does not implement ReportDataMap.toDataMap() it will not be included in a Report,
     * unless it is of type AnswerResult.
     *
     * @return a custom map that will added to the report data.
     */
    fun toDataMap(): Map<String, Any>?
}

/**
 * ReportResultDataMapBase can be used with object delegation to attach
 * base report result data map functionality automatically to any class.
 *
 * For example, add it to a custom AnswerResultBase class like this,
 * class CustomClass: AnswerResultBase(...), ReportResultDataMap by ReportResultDataMapBase() {
 * }
 */
class ReportResultDataMapBase: ReportResultDataMap {
    private val bridgeGson: Gson by lazy {
        EntityTypeConverters().bridgeGson
    }

    /**
     * @return a data map identical to how this object looks as json,
     *         that will properly merge with other TaskResult's stepHistory data maps in a ReportEntity.
     */
    override fun toDataMap(): Map<String, Any>? {
        val json = bridgeGson.toJsonTree(this).asJsonObject
        val dataMap = TreeMap<String, Any>()
        json.entrySet().forEach {
            dataMap.put(it.key, it.value)
        }
        return dataMap
    }
}

/**
 * ReportResultDataMapHelper can be used from Java classes to access base report data map functionality.
 */
class ReportResultDataMapHelper {
    companion object {
        /**
         * @param src any object that can be represented and merged into a ReportEntity.
         * @return a data map identical to how this object looks as json,
         *         that will properly merge with other TaskResult's stepHistory data maps in a ReportEntity.
         */
        @JvmStatic fun toDataMap(src: Any): Map<String, Any>? {
            val bridgeGson = EntityTypeConverters().bridgeGson
            val json = bridgeGson.toJsonTree(src).asJsonObject
            val dataMap = TreeMap<String, Any>()
            json.entrySet().forEach {
                dataMap[it.key] = it.value
            }
            return dataMap
        }
    }
}