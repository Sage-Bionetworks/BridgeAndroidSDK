package org.sagebionetworks.bridge.researchstack.task.tracked;

import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.researchstack.backbone.StorageAccess;
import org.researchstack.backbone.answerformat.ChoiceAnswerFormat;
import org.researchstack.backbone.model.survey.SurveyItem;
import org.researchstack.backbone.model.taskitem.TaskItem;

import org.researchstack.backbone.step.FormStep;
import org.researchstack.backbone.step.InstructionStep;
import org.researchstack.backbone.step.NavigationExpectedAnswerQuestionStep;
import org.researchstack.backbone.step.QuestionStep;
import org.researchstack.backbone.task.Task;
import org.researchstack.backbone.utils.LogExt;
import org.sagebionetworks.bridge.researchstack.R;
import org.sagebionetworks.bridge.researchstack.task.creation.BridgeSurveyItemAdapter;
import org.sagebionetworks.bridge.researchstack.task.creation.BridgeTaskItemAdapter;

import static junit.framework.Assert.*;

/**
 * Created by TheMDP on 3/25/17.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({StorageAccess.class, Looper.class, LogExt.class})
public class TrackedTaskItemFactoryTest {

    private Gson gson;
    private TrackedTaskItemFactory trackedFactory;
    private SurveyFactoryHelper surveyFactoryHelper;
    private ResourceParserHelper resourceHelper;

    @Before
    public void setupTest() {
        resourceHelper = new ResourceParserHelper();
        trackedFactory = new TrackedTaskItemFactory();
        surveyFactoryHelper = new SurveyFactoryHelper();

        Mockito.when(surveyFactoryHelper.mockContext.getString(R.string.rsb_less_than_ago)).thenReturn("Less than %s ago");
        Mockito.when(surveyFactoryHelper.mockContext.getString(R.string.rsb_more_than_ago)).thenReturn("More than %s ago");
        Mockito.when(surveyFactoryHelper.mockContext.getString(R.string.rsb_range_ago)).thenReturn("%s ago");

        Mockito.when(surveyFactoryHelper.mockContext.getString(R.string.rsb_time_seconds)).thenReturn("seconds");
        Mockito.when(surveyFactoryHelper.mockContext.getString(R.string.rsb_time_minutes)).thenReturn("minutes");
        Mockito.when(surveyFactoryHelper.mockContext.getString(R.string.rsb_time_hours)).thenReturn("hours");
        Mockito.when(surveyFactoryHelper.mockContext.getString(R.string.rsb_time_days)).thenReturn("days");
        Mockito.when(surveyFactoryHelper.mockContext.getString(R.string.rsb_time_weeks)).thenReturn("weeks");
        Mockito.when(surveyFactoryHelper.mockContext.getString(R.string.rsb_time_months)).thenReturn("months");
        Mockito.when(surveyFactoryHelper.mockContext.getString(R.string.rsb_time_years)).thenReturn("years");

        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(TaskItem.class, new BridgeTaskItemAdapter());
        builder.registerTypeAdapter(SurveyItem.class, new BridgeSurveyItemAdapter());
        gson = builder.create();

        PowerMockito.mockStatic(LogExt.class);
        PowerMockito.mockStatic(Looper.class);
        Looper mockMainThreadLooper = Mockito.mock(Looper.class);
        Mockito.when(Looper.getMainLooper()).thenReturn(mockMainThreadLooper);
        Handler mockMainThreadHandler = Mockito.mock(Handler.class);
        try {
            PowerMockito.whenNew(Handler.class).withArguments(null).thenReturn(mockMainThreadHandler);
        } catch (Exception e) {
            assertNotNull(e);
        }
        PowerMockito.mock(StorageAccess.class);
    }

    @Test
    public void testTrackedTaskItemDeserialization() {
        String medicationTaskItemJson = resourceHelper.getJsonStringForResourceName("medication_tracking");
        TaskItem taskItem = gson.fromJson(medicationTaskItemJson, TaskItem.class);
        Task task = trackedFactory.createTask(surveyFactoryHelper.mockContext, taskItem);

        assertNotNull(task);
        assertEquals("Medication Task", task.getIdentifier());

        assertTrue(task instanceof TrackedObjectTask);

        TrackedObjectTask trackedTask = (TrackedObjectTask)task;

        assertNotNull(trackedTask.getCollection());
        assertNotNull(trackedTask.getCollection().getTrackedItems());
        assertEquals(15, trackedTask.getCollection().getTrackedItems().size());

        {
            TrackedDataObject dataObject = trackedTask.getCollection().getTrackedItems().get(0);
            assertEquals("Levodopa", dataObject.getIdentifier());
            assertTrue(dataObject.isTracking());
        }

        assertNotNull(trackedTask.getTrackedStepHolderList());
        assertEquals(8, trackedTask.getTrackedStepHolderList().size());

        {
            TrackedStepHolder holder = trackedTask.getTrackedStepHolderList().get(0);
            assertTrue(holder.getRootStep() instanceof InstructionStep);
            assertEquals("medicationIntroduction", holder.getRootStep().getIdentifier());
            assertEquals(TrackedStepHolder.Type.INTRODUCTION, holder.getTrackingType());
        }

        {
            TrackedStepHolder holder = trackedTask.getTrackedStepHolderList().get(1);
            assertTrue(holder.getRootStep() instanceof NavigationExpectedAnswerQuestionStep);
            assertEquals("medicationChanged", holder.getRootStep().getIdentifier());
            assertEquals(TrackedStepHolder.Type.CHANGED, holder.getTrackingType());
        }

        // The question step (frequency) in the form step (selection) actually comes first due to order of operations
        {
            TrackedStepHolder holder = trackedTask.getTrackedStepHolderList().get(2);
            assertTrue(holder.getRootStep() instanceof QuestionStep);
            assertEquals("medicationFrequency", holder.getRootStep().getIdentifier());
            assertEquals(TrackedStepHolder.Type.FREQUENCY, holder.getTrackingType());
        }

        {
            TrackedStepHolder holder = trackedTask.getTrackedStepHolderList().get(3);
            assertTrue(holder.getRootStep() instanceof FormStep);
            assertEquals("medicationSelection", holder.getRootStep().getIdentifier());
            assertEquals(TrackedStepHolder.Type.SELECTION, holder.getTrackingType());
        }

        {
            TrackedStepHolder holder = trackedTask.getTrackedStepHolderList().get(4);
            assertTrue(holder.getRootStep() instanceof FormStep);
            assertEquals("momentInDay", holder.getRootStep().getIdentifier());
            assertEquals(TrackedStepHolder.Type.ACTIVITY, holder.getTrackingType());
        }

        {
            TrackedStepHolder holder = trackedTask.getTrackedStepHolderList().get(5);
            assertTrue(holder.getRootStep() instanceof QuestionStep);
            assertTrue(((QuestionStep)holder.getRootStep()).getAnswerFormat() instanceof ChoiceAnswerFormat);
            assertEquals("medicationActivityTiming", holder.getRootStep().getIdentifier());
            assertEquals(TrackedStepHolder.Type.ACTIVITY, holder.getTrackingType());
        }

        {
            TrackedStepHolder holder = trackedTask.getTrackedStepHolderList().get(6);
            assertTrue(holder.getRootStep() instanceof QuestionStep);
            assertTrue(((QuestionStep)holder.getRootStep()).getAnswerFormat() instanceof ChoiceAnswerFormat);
            assertEquals("medicationTrackEach", holder.getRootStep().getIdentifier());
            assertEquals(TrackedStepHolder.Type.ACTIVITY, holder.getTrackingType());
            assertEquals("We want to find out if taking medicine influences the results of this activity.\n\nWhen was the last time you took your %@?\n\nScroll down for the complete list.", holder.getTextFormat());
            assertTrue(holder.trackEach());
        }

        {
            TrackedStepHolder holder = trackedTask.getTrackedStepHolderList().get(7);
            assertTrue(holder.getRootStep() instanceof InstructionStep);
            assertEquals("medicationConclusion", holder.getRootStep().getIdentifier());
            assertEquals(TrackedStepHolder.Type.COMPLETION, holder.getTrackingType());
        }

        assertEquals(8, trackedTask.getSteps().size());
    }
}
