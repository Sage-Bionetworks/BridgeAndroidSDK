package org.sagebionetworks.research.sageresearch.manager

import org.sagebionetworks.research.domain.step.ui.theme.ImageTheme

//
//  Copyright © 2018 Sage Bionetworks. All rights reserved.
//
// Redistribution and use in source and binary forms, with or without modification,
// are permitted provided that the following conditions are met:
//
// 1.  Redistributions of source code must retain the above copyright notice, this
// list of conditions and the following disclaimer.
//
// 2.  Redistributions in binary form must reproduce the above copyright notice,
// this list of conditions and the following disclaimer in the documentation and/or
// other materials provided with the distribution.
//
// 3.  Neither the name of the copyright holder(s) nor the names of any contributors
// may be used to endorse or promote products derived from this software without
// specific prior written permission. No license is granted to the trademarks of
// the copyright holders even if such marks are included in this software.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
// FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
// DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
// SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
// CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
// OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
//

/**
 * A protocol that can be used to filter and parse the scheduled activities for a
 * variety of customized UI/UX designs based on the objects defined
 */
interface ActivityGroup: TaskGroup {

    /**
     * @property journeyTitle The text to display for the task group when displaying this in a list or
     * collection where the format of the string is compact or extended, depending
     * upon the requirements of the UI design.
     */
    val journeyTitle: String?

    /**
     * @property activityIdentifiers A list of the activity identifiers associated with this task group.
     */
    val activityIdentifiers : Set<String>

    /**
     * The schedule plan guid that can be used to map scheduled activities to
     * the appropriate group in the case where more than one group may contain
     * the same tasks.
     */
    val schedulePlanGuid : String?

    /**
     * The activity guid map that can be used to map scheduled activities to
     * the appropriate group in the case where more than one group may contain
     * the same tasks **but** where the activities are not all grouped on the server
     * using the same schedule. This guid can be found in the Bridge Study Manager UI
     * by hovering your cursor over the copy icon and selecting "Copy GUID".
     */
    val activityGuidMap : Map<String, String>?

    // TODO: mdephillips 9/4/18 not sure we need this
    /// An identifier that can be used to associate an `SBBScheduledActivity` instance
    /// with setting up a local reminder for when to perform a task.
    //var notificationIdentifier : RSDIdentifier? { get }
}

/**
 * `ActivityGroupObject` is a concrete implementation of interface ActivityGroup
 *
 * - example:
 * ````
 *    // Example activity group using a shared `schedulePlanGuid`.
 *    let json = """
 *            {
 *                "identifier": "foo",
 *                "title": "Title",
 *                "journeyTitle": "Journey title",
 *                "detail": "A detail about the object",
 *                "imageSource": "fooImage",
 *                "activityIdentifiers": ["taskA", "taskB", "taskC"],
 *                "notificationIdentifier": "scheduleFoo",
 *                "schedulePlanGuid": "abcdef12-3456-7890",
 *            }
 *            """.data(using: .utf8)! // our data in native (JSON) format
 *
 *    // Example activity group using the `activity.guid` identifiers to map schedules to tasks.
 *    let json = """
 *            {
 *                "identifier": "foo",
 *                "activityIdentifiers": ["taskA", "taskB", "taskC"],
 *                "activityGuidMap": {
 *                                     "taskA":"ababab12-3456-7890",
 *                                     "taskB":"cdcdcd12-3456-7890",
 *                                     "taskC":"efefef12-3456-7890"
 *                                     }
 *            }
 *            """.data(using: .utf8)! // our data in native (JSON) format
 *
 *    // Example activity group where the first schedule matching the given activity identifer is used.
 *    let json = """
 *            {
 *                "identifier": "foo",
 *                "activityIdentifiers": ["taskA", "taskB", "taskC"]
 *            }
 *            """.data(using: .utf8)! // our data in native (JSON) format
 * ````
 */
data class ActivityGroupObject(
        override val identifier: String,
        override val title: String? = null,
        override val detail: String? = null,
        override val imageName: String? = null,
        override val tasks: Set<TaskInfo> = setOf(),
        override val journeyTitle: String? = null,
        override val activityIdentifiers : Set<String> = setOf(),
        override val schedulePlanGuid : String? = null,
        override val activityGuidMap : Map<String, String>? = null): ActivityGroup

