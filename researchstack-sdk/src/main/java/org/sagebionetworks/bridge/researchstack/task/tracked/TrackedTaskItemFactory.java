package org.sagebionetworks.bridge.researchstack.task.tracked;

import static org.sagebionetworks.bridge.researchstack.task.creation.BridgeSurveyItemAdapter.TRACKED_SELECTION_TYPE_GSON;

import android.content.Context;

import com.google.gson.Gson;

import org.researchstack.backbone.model.survey.FormSurveyItem;
import org.researchstack.backbone.model.survey.QuestionSurveyItem;
import org.researchstack.backbone.model.survey.SurveyItem;
import org.researchstack.backbone.model.taskitem.TaskItem;
import org.researchstack.backbone.model.taskitem.factory.TaskItemFactory;
import org.researchstack.backbone.step.QuestionStep;
import org.researchstack.backbone.step.Step;
import org.researchstack.backbone.task.Task;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by TheMDP on 3/24/17.
 */

public class TrackedTaskItemFactory extends TaskItemFactory {

    private Gson gson;

    /**
     * This is used to keep track of additional step info while the task is parsing
     * a TrackedDataObjectCollection Task
     */
    private List<TrackedStepHolder> trackedStepHolderList;

    public TrackedTaskItemFactory() {
        super();
        gson = new Gson();
    }

    @Override
    @SuppressWarnings("unchecked") // generic type List<T> cast in trackedTaskItems.getItems()
    public Task createCustomTask(Context context, TaskItem item) {
        trackedStepHolderList = new ArrayList<>();

        List<Step> stepList = super.createSurveySteps(context, item.getTaskSteps());

        if (!(item instanceof TrackedTaskItem)) {
            throw new IllegalStateException("Do not use TrackedTaskItemFactory for anything other than a TrackedTaskItem");
        }
        TrackedTaskItem trackedTaskItem = (TrackedTaskItem)item;

        TrackedObjectTask task = new TrackedObjectTask(
                context, item.getTaskIdentifier(), stepList,
                trackedTaskItem.getItems(), trackedStepHolderList);

        super.fillTaskWithDefaultTaskItemAdditions(context, task, trackedTaskItem);

        return task;
    }

    @Override
    public Step createCustomStep(Context context, SurveyItem item, boolean isSubtaskStep) {
        Step step;
        if (item.getTypeIdentifier().equals(TRACKED_SELECTION_TYPE_GSON)) {
            if (!(item instanceof FormSurveyItem)) {
                throw new IllegalStateException("Error in json parsing, trackingSelection types must be CompoundQuestionSurveyItem");
            }
            step = createFormStep(context, (FormSurveyItem)item);
        } else {
            step = super.createSurveyStep(context, item, isSubtaskStep);
        }
        addTrackedStepModel(step, item);
        return step;
    }

    @Override
    public Step createSurveyStep(Context context, SurveyItem item, boolean isSubtaskStep) {
        Step step = super.createSurveyStep(context, item, isSubtaskStep);
        addTrackedStepModel(step, item);
        return step;
    }

    @Override
    public QuestionStep createQuestionStep(Context context, QuestionSurveyItem item) {
        QuestionStep step = super.createQuestionStep(context, item);
        addTrackedStepModel(step, item);
        return step;
    }

    private void addTrackedStepModel(Step step, SurveyItem item) {
        // Check for tracking type, which means this was a a TrackedTaskItem, and we need
        // to do extra conversion to get our task to build properly
        TrackedStepHolder trackingStepModel = gson.fromJson(item.getRawJson(), TrackedStepHolder.class);
        if (!trackedStepHolderListContains(step.getIdentifier()) &&
            trackingStepModel != null && trackingStepModel.getTrackingType() != null)
        {
            TrackedStepHolder trackedStep = new TrackedStepHolder(step, trackingStepModel.getTrackingType());
            trackedStep.setTextFormat(trackingStepModel.getTextFormat());
            trackedStep.setTrackEach(trackingStepModel.trackEach());
            trackedStepHolderList.add(trackedStep);
        }
    }

    private boolean trackedStepHolderListContains(String stepIdentifier) {
        if (trackedStepHolderList == null) {
            return false;
        }
        for (TrackedStepHolder holder : trackedStepHolderList) {
            if (holder.getRootStep().getIdentifier().equals(stepIdentifier)) {
                return true;
            }
        }
        return false;
    }
}
