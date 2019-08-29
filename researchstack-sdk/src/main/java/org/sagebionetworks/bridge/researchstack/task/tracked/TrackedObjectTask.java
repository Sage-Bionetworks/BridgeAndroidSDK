package org.sagebionetworks.bridge.researchstack.task.tracked;

import android.content.Context;

import org.sagebionetworks.researchstack.backbone.result.TaskResult;
import org.sagebionetworks.researchstack.backbone.step.Step;
import org.sagebionetworks.researchstack.backbone.task.NavigableOrderedTask;

import java.util.List;

/**
 * Created by TheMDP on 3/25/17.
 */

public class TrackedObjectTask extends NavigableOrderedTask {

    private TrackedDataObjectCollection collection;
    private List<TrackedStepHolder> trackedStepHolderList;

    /* Default constructor needed for serilization/deserialization of object */
    public TrackedObjectTask() {
        super();
    }

    /**
     * A MedicationTrackerTask
     * @param context can be app or activity, used for file access
     * @param identifier of the task
     * @param stepList for the navigable ordered task
     * @param trackedDataObjectList the tracked data object list to create in TrackedDataObjectCollection
     * @param trackedStepHolderList the holder for the TrackedStep info and the actual Step
     */
    public TrackedObjectTask(
            Context context,
            String identifier,
            List<Step> stepList,
            List<? extends TrackedDataObject> trackedDataObjectList,
            List<TrackedStepHolder> trackedStepHolderList)
    {
        super(identifier, stepList);
        this.trackedStepHolderList = trackedStepHolderList;
        loadTrackedCollection(context, identifier, trackedDataObjectList);
    }

    @Override
    public Step getStepAfterStep(Step step, TaskResult result) {
        Step returnStep = super.getStepAfterStep(step, result);
        return returnStep;
    }

    @Override
    public Step getStepBeforeStep(Step step, TaskResult result) {
        Step returnStep = super.getStepBeforeStep(step, result);
        return returnStep;
    }

    protected List<TrackedStepHolder> getTrackedStepHolderList() {
        return trackedStepHolderList;
    }

    public void setTrackedStepHolderList(List<TrackedStepHolder> trackedStepHolderList) {
        this.trackedStepHolderList = trackedStepHolderList;
    }

    public TrackedDataObjectCollection getCollection() {
        return collection;
    }

    /**
     * This is part of what the constructor does, but if you can't use the constructor,
     * this is how you set the TrackedDataObjectCollection
     * @param context can be app or activity, used for file access
     * @param identifier of the task
     * @param trackedDataObjectList the tracked data object list to create in TrackedDataObjectCollection
     */
    public void loadTrackedCollection(
            Context context,
            String identifier,
            List<? extends TrackedDataObject> trackedDataObjectList)
    {
        collection = TrackedDataObjectCollection.loadSavedCollection(context, identifier, trackedDataObjectList);
    }
}
