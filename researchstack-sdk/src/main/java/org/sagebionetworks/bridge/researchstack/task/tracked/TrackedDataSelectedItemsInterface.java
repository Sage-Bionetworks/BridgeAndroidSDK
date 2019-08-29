package org.sagebionetworks.bridge.researchstack.task.tracked;

import org.sagebionetworks.researchstack.backbone.result.StepResult;

import java.util.List;

/**
 * Created by TheMDP on 3/23/17.
 */

public interface TrackedDataSelectedItemsInterface {
    /**
     * @param selectedItems the list of selected tracked data objects
     * @return a step result based off of the selected items
     */
    StepResult stepResultForSelectedItems(List<? extends TrackedDataObject> selectedItems);
}
