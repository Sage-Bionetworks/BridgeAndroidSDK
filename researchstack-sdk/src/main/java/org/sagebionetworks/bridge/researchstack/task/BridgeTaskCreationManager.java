package org.sagebionetworks.bridge.researchstack.task;

import com.google.gson.GsonBuilder;

import org.researchstack.backbone.model.survey.SurveyItem;
import org.researchstack.backbone.model.taskitem.TaskItem;
import org.researchstack.backbone.model.taskitem.factory.TaskItemFactory;
import org.researchstack.backbone.task.TaskCreationManager;
import org.sagebionetworks.bridge.researchstack.task.creation.BridgeSurveyItemAdapter;
import org.sagebionetworks.bridge.researchstack.task.creation.BridgeTaskItemAdapter;
import org.sagebionetworks.bridge.researchstack.task.tracked.TrackedTaskItemFactory;

/**
 * Created by TheMDP on 3/24/17.
 */

public class BridgeTaskCreationManager extends TaskCreationManager {

    public static final String MEDICATION_TASK_IDENTIFIER = "Medication Task";

    public BridgeTaskCreationManager() {
        super();
    }

    @Override
    public TaskItemFactory getTaskItemFactory(TaskItem item) {
        if (item.getTaskIdentifier() != null) {
            if (item.getTaskIdentifier().equals(MEDICATION_TASK_IDENTIFIER)) {
                return new TrackedTaskItemFactory();
            }
        }
        return getDefaultTaskItemFactory();
    }

    @Override
    public void registerSurveyItemAdapter(GsonBuilder builder) {
        builder.registerTypeAdapter(SurveyItem.class, new BridgeSurveyItemAdapter());
    }

    @Override
    public void registerTaskItemAdapter(GsonBuilder builder) {
        builder.registerTypeAdapter(SurveyItem.class, new BridgeTaskItemAdapter());
    }
}
