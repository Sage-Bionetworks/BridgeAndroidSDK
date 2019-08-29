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

package org.sagebionetworks.research.sageresearch.viewmodel

import org.sagebionetworks.researchstack.backbone.result.StepResult
import org.sagebionetworks.researchstack.backbone.step.Step
import org.sagebionetworks.research.domain.result.AnswerResultType.BOOLEAN
import org.sagebionetworks.research.domain.result.AnswerResultType.DATE
import org.sagebionetworks.research.domain.result.AnswerResultType.DECIMAL
import org.sagebionetworks.research.domain.result.AnswerResultType.INTEGER
import org.sagebionetworks.research.domain.result.AnswerResultType.JSON
import org.sagebionetworks.research.domain.result.AnswerResultType.STRING
import org.sagebionetworks.research.domain.result.implementations.AnswerResultBase
import org.sagebionetworks.research.domain.result.implementations.TaskResultBase
import org.sagebionetworks.research.domain.result.interfaces.TaskResult
import org.threeten.bp.Instant
import org.threeten.bp.ZoneId
import org.threeten.bp.ZonedDateTime
import java.util.Date
import java.util.UUID

class TaskResultHelper {
    companion object {
        val taskIdentifier = "id1"
        val subtaskIdentifier1 = "subid1"
        val subtaskIdentifier2 = "subid2"

        val booleanAnswerResultId = "boolAnswer"
        val booleanAnswer = true

        val decimalAnswerResultId = "decimalAnswer"
        val decimalAnswer = 1.0

        val intAnswerResultId = "intAnswer"
        val intAnswer = 1

        val strAnswerResultId = "strAnswer"
        val strAnswer = "string"

        val dateAnswerResultId = "dateAnswer"
        //of(int year, int month, int dayOfMonth, int hour, int minute, int second, int nanoOfSecond, ZoneId zone) {
        val dateAnswer = ZonedDateTime.of(
                2017, 11, 8,
                5, 5, 0, 0,
                ZoneId.of("Z"))

        val jsonAnswerResultId = "jsonAnswer"
        val jsonAnswer = JsonAnswer(strAnswer, intAnswer)

        val expectedSurveyClientDataMap = mapOf(
                Pair(TaskResultHelper.booleanAnswerResultId, TaskResultHelper.booleanAnswer),
                Pair(TaskResultHelper.intAnswerResultId, TaskResultHelper.intAnswer),
                Pair(TaskResultHelper.decimalAnswerResultId, TaskResultHelper.decimalAnswer),
                Pair(TaskResultHelper.strAnswerResultId, TaskResultHelper.strAnswer),
                Pair(TaskResultHelper.dateAnswerResultId, TaskResultHelper.dateAnswer),
                Pair(TaskResultHelper.jsonAnswerResultId, TaskResultHelper.jsonAnswer))

        val expectedSurveyClientDataMapSubtask1 = mapOf(
                Pair(subtaskIdentifier1+TaskResultHelper.booleanAnswerResultId, TaskResultHelper.booleanAnswer),
                Pair(subtaskIdentifier1+TaskResultHelper.intAnswerResultId, TaskResultHelper.intAnswer),
                Pair(subtaskIdentifier1+TaskResultHelper.decimalAnswerResultId, TaskResultHelper.decimalAnswer),
                Pair(subtaskIdentifier1+TaskResultHelper.strAnswerResultId, TaskResultHelper.strAnswer),
                Pair(subtaskIdentifier1+TaskResultHelper.dateAnswerResultId, TaskResultHelper.dateAnswer),
                Pair(subtaskIdentifier1+TaskResultHelper.jsonAnswerResultId, TaskResultHelper.jsonAnswer))

        val expectedSurveyClientDataMapSubtask2 = mapOf(
                Pair(subtaskIdentifier2+TaskResultHelper.booleanAnswerResultId, TaskResultHelper.booleanAnswer),
                Pair(subtaskIdentifier2+TaskResultHelper.intAnswerResultId, TaskResultHelper.intAnswer),
                Pair(subtaskIdentifier2+TaskResultHelper.decimalAnswerResultId, TaskResultHelper.decimalAnswer),
                Pair(subtaskIdentifier2+TaskResultHelper.strAnswerResultId, TaskResultHelper.strAnswer),
                Pair(subtaskIdentifier2+TaskResultHelper.dateAnswerResultId, TaskResultHelper.dateAnswer),
                Pair(subtaskIdentifier2+TaskResultHelper.jsonAnswerResultId, TaskResultHelper.jsonAnswer))

        fun researchStackSurveyTaskResult(): org.sagebionetworks.researchstack.backbone.result.TaskResult {
            return researchStackSurveyTaskResult(taskIdentifier)
        }

        fun surveyTaskResult(): TaskResult {
            return surveyTaskResult(taskIdentifier)
        }

        fun compoundTaskResult(): TaskResult {
            var taskResult = TaskResultBase(taskIdentifier, UUID.randomUUID())
            val subTaskResult1 = surveyTaskResult(subtaskIdentifier1, subtaskIdentifier1)
            taskResult = taskResult.addStepHistory(subTaskResult1)
            val subTaskResult2 = surveyTaskResult(subtaskIdentifier2, subtaskIdentifier2)
            taskResult = taskResult.addStepHistory(subTaskResult2)
            return taskResult
        }

        private fun surveyTaskResult(identifier: String, subTaskId: String): TaskResult {
            return TaskResultBase(identifier, UUID.randomUUID())
                    .addStepHistory(AnswerResultBase(
                            subTaskId + booleanAnswerResultId, Instant.now(), Instant.now(), booleanAnswer, BOOLEAN))
                    .addStepHistory(AnswerResultBase(
                            subTaskId + intAnswerResultId, Instant.now(), Instant.now(), intAnswer, INTEGER))
                    .addStepHistory(AnswerResultBase(
                            subTaskId + decimalAnswerResultId, Instant.now(), Instant.now(), decimalAnswer, DECIMAL))
                    .addStepHistory(AnswerResultBase(
                            subTaskId + strAnswerResultId, Instant.now(), Instant.now(), strAnswer, STRING))
                    .addStepHistory(AnswerResultBase(
                            subTaskId + dateAnswerResultId, Instant.now(), Instant.now(), dateAnswer, DATE))
                    .addStepHistory(AnswerResultBase(
                            subTaskId + jsonAnswerResultId, Instant.now(), Instant.now(), jsonAnswer, JSON))
        }

        private fun surveyTaskResult(identifier: String): TaskResult {
            return surveyTaskResult(identifier, "")
        }

        private fun researchStackSurveyTaskResult(identifier: String): org.sagebionetworks.researchstack.backbone.result.TaskResult {
            val taskResult = org.sagebionetworks.researchstack.backbone.result.TaskResult(identifier)
            addResearchStackResult(booleanAnswerResultId, booleanAnswer, taskResult)
            addResearchStackResult(intAnswerResultId, intAnswer, taskResult)
            addResearchStackResult(decimalAnswerResultId, decimalAnswer, taskResult)
            addResearchStackResult(strAnswerResultId, strAnswer, taskResult)
            addResearchStackResult(dateAnswerResultId, dateAnswer, taskResult)
            addResearchStackResult(jsonAnswerResultId, jsonAnswer, taskResult)
            return taskResult
        }

        fun <T>addResearchStackResult(identifier: String, answer: T, taskResult: org.sagebionetworks.researchstack.backbone.result.TaskResult) {
            val formId = "${identifier}Form"
            val formResult = StepResult<StepResult<T>>(Step(formId))
            formResult.startDate = Date()
            formResult.endDate = Date()
            val result = StepResult<T>(Step(identifier))
            result.startDate = Date()
            result.endDate = Date()
            result.result = answer
            formResult.result = result
            taskResult.results[formId] = formResult
        }
    }
}

data class JsonAnswer(val key: String, val value: Int)