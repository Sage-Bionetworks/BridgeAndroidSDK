/*
 *    Copyright 2018 Sage Bionetworks
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package org.sagebionetworks.bridge.android.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.android.util.ScheduledActivityUtil.TO_SCHEDULE_PLAN_GUID;
import static org.sagebionetworks.bridge.android.util.ScheduledActivityUtil.TO_TASK_IDENTIFIER;
import static org.sagebionetworks.bridge.android.util.ScheduledActivityUtil.groupBySchedulePlan;

import com.google.common.collect.Multimap;

import org.junit.Test;
import org.sagebionetworks.bridge.rest.model.Activity;
import org.sagebionetworks.bridge.rest.model.ScheduledActivity;
import org.sagebionetworks.bridge.rest.model.SurveyReference;
import org.sagebionetworks.bridge.rest.model.TaskReference;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Created by liujoshua on 2/19/2018.
 */
public class ScheduledActivityUtilTest {
    @Test
    public void testToSchedulePlanGuid() {
        String spGuid = "spGuid";

        ScheduledActivity sa = mock(ScheduledActivity.class);
        when(sa.getSchedulePlanGuid()).thenReturn(spGuid);

        String spGuidResult = TO_SCHEDULE_PLAN_GUID.apply(sa);

        assertEquals(spGuid, spGuidResult);
        verify(sa).getSchedulePlanGuid();
    }

    @Test
    public void testToTaskIdentifier_Survey() {
        SurveyReference survey = mock(SurveyReference.class);

        Activity activity = mock(Activity.class);
        when(activity.getSurvey()).thenReturn(survey);

        ScheduledActivity sa = mock(ScheduledActivity.class);
        when(sa.getActivity()).thenReturn(activity);

        String taskIdResult = TO_TASK_IDENTIFIER.apply(sa);
        assertNull(taskIdResult);

        verify(sa, atLeastOnce()).getActivity();
        verify(activity, atLeastOnce()).getTask();
        verify(activity, never()).getSurvey();
    }

    @Test
    public void testToTaskIdentifier_Task() {
        String taskId = "taskId";
        TaskReference taskReference = mock(TaskReference.class);
        when(taskReference.getIdentifier()).thenReturn(taskId);

        Activity activity = mock(Activity.class);
        when(activity.getSurvey()).thenReturn(null);
        when(activity.getTask()).thenReturn(taskReference);

        ScheduledActivity sa = mock(ScheduledActivity.class);
        when(sa.getActivity()).thenReturn(activity);

        String taskIdResult = TO_TASK_IDENTIFIER.apply(sa);
        assertEquals(taskId, taskIdResult);

        verify(sa, atLeastOnce()).getActivity();
        verify(activity, atLeastOnce()).getTask();
        verify(activity, never()).getSurvey();
    }

    @Test
    public void testGroupBySchedulePlan() {
        ScheduledActivity saA1 = mock(ScheduledActivity.class);
        when(saA1.getSchedulePlanGuid()).thenReturn("A");

        ScheduledActivity saA2 = mock(ScheduledActivity.class);
        when(saA2.getSchedulePlanGuid()).thenReturn("A");

        ScheduledActivity saB1 = mock(ScheduledActivity.class);
        when(saB1.getSchedulePlanGuid()).thenReturn("B");

        ScheduledActivity saB2 = mock(ScheduledActivity.class);
        when(saB2.getSchedulePlanGuid()).thenReturn("B");

        ScheduledActivity saC1 = mock(ScheduledActivity.class);
        when(saC1.getSchedulePlanGuid()).thenReturn("C");

        List<ScheduledActivity> scheduledActivities = Arrays.asList(saA1, saB1, saB2, saC1, saA2);

        Multimap<String, ScheduledActivity> spGuidToScheduledActivities = groupBySchedulePlan
                (scheduledActivities);

        Collection<ScheduledActivity> saAs = spGuidToScheduledActivities.get("A");
        assertEquals(Arrays.asList(saA1, saA2), saAs);

        Collection<ScheduledActivity> saBs = spGuidToScheduledActivities.get("B");
        assertEquals(Arrays.asList(saB1, saB2), saBs);

        Collection<ScheduledActivity> saCs = spGuidToScheduledActivities.get("C");
        assertEquals(Collections.singletonList(saC1), saCs);
    }
}