package org.sagebionetworks.bridge.researchstack.task.tracked.medication;

import org.sagebionetworks.bridge.researchstack.task.tracked.TrackedMedication;
import org.sagebionetworks.bridge.researchstack.task.tracked.TrackedTaskItem;

/**
 * Created by TheMDP on 3/24/17.
 */

public class MedicationTaskItem extends TrackedTaskItem<TrackedMedication> {

    /* Default constructor needed for serialization/deserialization of object */
    public MedicationTaskItem() {
        super();
    }
}
