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

import org.researchstack.backbone.result.FileResult
import org.researchstack.backbone.result.StepResult
import org.sagebionetworks.bridge.researchstack.TaskHelper
import org.sagebionetworks.bridge.researchstack.survey.SurveyAnswer
import org.sagebionetworks.research.domain.result.interfaces.AnswerResult
import org.sagebionetworks.research.domain.result.interfaces.CollectionResult
import org.sagebionetworks.research.domain.result.interfaces.Result
import org.sagebionetworks.research.domain.result.interfaces.TaskResult
import org.sagebionetworks.research.sageresearch.dao.room.ReportResultDataMap
import java.util.ArrayList
import java.util.HashMap
import java.util.TreeMap

/**
 * @return the flattened results of all StepResults
 */
fun org.researchstack.backbone.result.TaskResult.clientDataAnswerMap(): Map< String, Any> {
    val answersMap = HashMap<String, Any>()
    val flattenedResults = TaskHelper.flattenResults(this)
    flattenedResults.forEach { result ->
        (result as? StepResult<*>)?.let { stepResult ->
            stepResult.results?.forEach { mapEntry ->
                if (mapEntry.value !is FileResult) {
                    val key =
                            if (StepResult.DEFAULT_KEY != mapEntry.key) {
                                mapEntry.key
                            } else {
                                stepResult.identifier
                            }
                    answersMap[key] = mapEntry.value
                }
            }
        }
    }
    return answersMap
}

/**
 * @return the flattened results of all AnswerResults
 */
fun TaskResult.clientDataAnswerMap(): Map<String, Any> {
    val answerMap = TreeMap<String, Any>()
    this.flattenResult().forEach { result ->
        (result as? ReportResultDataMap)?.let { customDataMap ->
            customDataMap.toDataMap()?.forEach {
                answerMap[it.key] = it.value
            }
        } ?: run {
            (result as? AnswerResult<*>)?.let { answerResult ->
                answerResult.answer?.let {
                    answerMap.put(answerResult.identifier, it)
                }
            }
        }
    }
    return answerMap
}

/**
 * This tasks a map structure, which step results are, and flattens it to a List
 *
 * @param taskResult from the result of a Task, can contain any combination of Result objects
 * they can be nested and they can be as deep as desired
 * @return a list of Result objects from recursively investigating all StepResult objects
 */
fun TaskResult.flattenResult(): List<Result> {
    val resultList = ArrayList<Result>()
    this.stepHistory.forEach {
        addResultsRecursively(it, resultList)
    }
    return resultList
}

/**
 * @param stepResult can contain nested step results
 * @param resultList the result list to add a StepResult or Result to
 * @return false if there are no more results look into, true if the method went deeper
 */
private fun addResultsRecursively(result: Result?, resultList: MutableList<Result>): Boolean {
    var wentDeeper = false

    result?.let { it ->
        (it as? CollectionResult)?.let { collectionResult ->
            wentDeeper = true
            collectionResult.inputResults.forEach {
                addResultsRecursively(it, resultList)
            }
        }

        if (!wentDeeper) {
            resultList.add(it)
        }
    }

    return wentDeeper
}