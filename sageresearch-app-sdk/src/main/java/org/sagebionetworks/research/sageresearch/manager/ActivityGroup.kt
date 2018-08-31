package org.sagebionetworks.research.sageresearch.manager

import org.sagebionetworks.research.domain.step.ui.theme.ImageTheme
import org.sagebionetworks.research.domain.task.TaskInfo

/**
 * A protocol that can be used to filter and parse the scheduled activities for a
 * variety of customized UI/UX designs based on the objects defined in the
 */
open class ActivityGroup(
        override val identifier: String, override val title: String?, override val detail: String?,
        override val imageVendor: ImageTheme?, override val tasks: Array<TaskInfo>) : TaskGroup