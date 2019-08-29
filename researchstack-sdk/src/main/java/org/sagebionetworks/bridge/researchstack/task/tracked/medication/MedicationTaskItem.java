package org.sagebionetworks.bridge.researchstack.task.tracked.medication;

import com.google.gson.annotations.SerializedName;

import org.sagebionetworks.researchstack.backbone.model.taskitem.TaskItem;
import org.sagebionetworks.bridge.researchstack.task.tracked.TrackedDataObject;
import org.sagebionetworks.bridge.researchstack.task.tracked.TrackedMedication;
import org.sagebionetworks.bridge.researchstack.task.tracked.TrackedTaskItem;

import java.util.List;

/**
 * Created by TheMDP on 3/24/17.
 */

public class MedicationTaskItem extends TrackedTaskItem<TrackedMedication> {

    /* Default constructor needed for serialization/deserialization of object */
    public MedicationTaskItem() {
        super();
    }
}
