package org.sagebionetworks.bridge.researchstack.task.tracked.medication;

import android.content.Context;

import org.researchstack.backbone.step.Step;
import org.sagebionetworks.bridge.researchstack.task.tracked.TrackedMedication;
import org.sagebionetworks.bridge.researchstack.task.tracked.TrackedObjectTask;
import org.sagebionetworks.bridge.researchstack.task.tracked.TrackedStepHolder;

import java.util.List;

/**
 * Created by TheMDP on 3/24/17.
 */

public class MedicationTrackerTask extends TrackedObjectTask {

    /* Default constructor needed for serilization/deserialization of object */
    public MedicationTrackerTask() {
        super();
    }

    /**
     * A MedicationTrackerTask
     * @param identifier of the task
     * @param stepList for the navigable ordered task
     * @param medicationList the tracked data object list to create in TrackedDataObjectCollection
     * @param trackedStepHolderList
     */
    public MedicationTrackerTask(
            Context context,
            String identifier,
            List<Step> stepList,
            List<TrackedMedication> medicationList,
            List<TrackedStepHolder> trackedStepHolderList)
    {
        super(context, identifier, stepList, medicationList, trackedStepHolderList);
    }
}
