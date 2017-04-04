package org.sagebionetworks.bridge.researchstack.task.creation;

import com.google.gson.JsonElement;

import org.researchstack.backbone.model.taskitem.TaskItem;
import org.researchstack.backbone.model.taskitem.TaskItemAdapter;
import org.sagebionetworks.bridge.researchstack.task.tracked.medication.MedicationTaskItem;

/**
 * Created by TheMDP on 3/24/17.
 */

public class BridgeTaskItemAdapter extends TaskItemAdapter {

    public static final String MEDICATION_TASK_TYPE = "Medication Task";

    @Override
    public Class<? extends TaskItem> getCustomClass(String customType, JsonElement json) {
        switch (customType) {
            case MEDICATION_TASK_TYPE:
                return MedicationTaskItem.class;
        }
        return super.getCustomClass(customType, json);
    }
}
