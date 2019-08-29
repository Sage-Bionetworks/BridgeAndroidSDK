package org.sagebionetworks.bridge.researchstack.survey;

import org.joda.time.DateTime;
import org.sagebionetworks.researchstack.backbone.model.SchedulesAndTasksModel;

/**
 * Subclass of TaskScheduleModel to include Bridge-specific attributes, such as surveyGuid and
 * surveyCreatedOn.
 */
public class SurveyTaskScheduleModel extends SchedulesAndTasksModel.TaskScheduleModel {
    /** Survey GUID, which uniquely identifies the survey. */
    public String surveyGuid;

    /**
     * Survey createdOn timestamp, which identifies the survey version. Important note: This is the
     * version timestamp of the survey as reported by the server, _not_ the timestamp of when the
     * participant responded to the survey.
     */
    public DateTime surveyCreatedOn;
}
