package org.sagebionetworks.bridge.researchstack.task.tracked;

import android.content.Context;
import androidx.annotation.NonNull;

import com.google.gson.Gson;

import org.researchstack.backbone.StorageAccess;
import org.researchstack.backbone.result.StepResult;
import org.researchstack.backbone.step.Step;
import org.researchstack.backbone.storage.file.FileAccess;
import org.researchstack.backbone.utils.StepResultHelper;
import org.researchstack.backbone.utils.StepResultHelper.ResultClassComparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by TheMDP on 3/21/17.
 */

public class TrackedDataObjectCollection implements Serializable {

    private static final Logger LOG = LoggerFactory.getLogger(TrackedDataObjectCollection.class);
    public static final String TRACKED_DATA_STORE_PATH = "TRACKED_DATA_STORE_PATH";

    private static final int DEFAULT_TRACKING_SURVEY_REPEAT_INTERVAL = 30 * 24 * 60 * 60;   // Every 30 days by default
    private static final int DEFAULT_MOMENT_IN_DAY_REPEAT_INTERVAL   = 20 * 60;   // Every 20 minutes by default

    /**
     * Filename associated we loading and saving this tracked data object collection
     */
    private String filename;

    /**
     * Timestamp for the last time the tracked data survey questions were asked.
     * (ex. What medication, etc.)
     */
    private Date lastTrackingSurveyDate;

    /**
     * Timestamp for the last time the "Moment in Day" survey questions were asked.
     */
    private Date lastCompletionDate;

    /**
     * The repeat interval in seconds of the trackingSurvey
     */
    private long trackingSurveyRepeatTimeInterval;

    /**
     * The repeat interval in seconds of the momentInDay
     */
    private int momentInDayRepeatTimeInterval;

    /**
     * Selected items from the tracked data survey questions. Assumes only one set of items.
     */
    private List<? extends TrackedDataObject> selectedItems;

    /**
     * Items from the tracked data survey questions that are *tracked* with "Moment in Day"
     * follow-up. Assumes only one set of items. This is a subset of the selected items that includes
     * only the selected items that are tracked with a follow-up question.
     */
    private List<? extends TrackedDataObject> trackedItems;

    /**
     * Results that map to "Moment in Day" steps. These results are stored in memory only.
     */
    private List<StepResult> momentInDayResults;

    /**
     * All step results for steps involved with the TrackedDataObjectCollection
     */
    private Map<String, StepResult> managedResults;

    protected TrackedDataObjectCollection() {
        super();
    }

    /**
     * Update the "Moment in Day" result set.
     * @param stepResult The step result to add/replace in the "Moment in Day" result set.
     */
    public void updateMomentInDay(StepResult stepResult) {
        if (stepResult == null || stepResult.getIdentifier() == null) {
            return;
        }

        if (momentInDayResults == null) {
            momentInDayResults = new ArrayList<>();
        }

        int indexOfPreviousResult = StepResultHelper.indexOfStepResultKey(
                momentInDayResults, stepResult.getIdentifier());

        if (indexOfPreviousResult >= 0) {
            momentInDayResults.set(indexOfPreviousResult, stepResult);
        } else {
            momentInDayResults.add(stepResult);
        }
    }

    /**
     * Update the tracked data result set. If this is recognized as including the `selectedItems`
     * then that property will be updated from this result.
     * @param     stepResult  The step result to use to add/replace the tracked data set
     */
    public void updateTrackedData(StepResult stepResult) {
        if (stepResult == null || stepResult.getIdentifier() == null) {
            return;
        }

        // Check if this step result has a selected items result
        TrackedDataSelectionResult result = StepResultHelper.findResultOfClass(stepResult,
                new ResultClassComparator<TrackedDataSelectionResult>()
        {
            public boolean isTypeOfClass(Object object) {
                return object instanceof TrackedDataSelectionResult;
            }
        });

        if (result != null) {
            // If the selected Items are found then that is the only result that needs to be set
            setSelectedItems(result.getSelectedItems());
        } else {
            // Otherwise, add to a general-purpose result set
            if (managedResults == null) {
                managedResults = new HashMap<>();
            }
            managedResults.put(stepResult.getIdentifier(), stepResult);
        }
    }

    /**
     * Return the step result that is associated with a given step.
     * @param     step    The step for which a result is requested.
     * @return            The step result for this step (if found in the data store)
     */
    public StepResult getStepResult(Step step) {
        if (step == null || step.getIdentifier() == null) {
            return null;
        }

        // Check the moment in day results set for a result that matches the step identifier
        StepResult momentInDayResult = StepResultHelper.findStepResult(momentInDayResults, step.getIdentifier());
        if (momentInDayResult != null) {
            return momentInDayResult;
        }

        // Check if the step can be built from the selected items
        if (step instanceof TrackedDataSelectedItemsInterface) {
            if (selectedItems == null) {
                return null;
            } else {
                TrackedDataSelectedItemsInterface trackedInterface = (TrackedDataSelectedItemsInterface)step;
                StepResult stepResult = trackedInterface.stepResultForSelectedItems(selectedItems);
                if (stepResult != null) {
                    stepResult.setEndDate(stepResult.getStartDate());
                    stepResult.setStartDate(lastTrackingSurveyDate);
                }
                return stepResult;
            }
        }

        return StepResultHelper.findStepResult(managedResults.values(), step.getIdentifier());
    }

    /**
     * Save the current changes to this object to disk using its filename
     * @param context Can be app or activity, used to load tracked data collection
     * @return true if changes were saved successfully, false otherwise
     */
    public boolean commitChanges(Context context) {
        FileAccess fileAccess = verifyFileAccess();

        if (fileAccess == null) {
            return false;
        }

        String fullFilePath = TRACKED_DATA_STORE_PATH + File.separator + filename;
        Gson gson = createGson();
        String trackedDataCollectionJson = gson.toJson(fullFilePath, TrackedDataObjectCollection.class);
        byte[] jsonBytes = trackedDataCollectionJson.getBytes();
        fileAccess.writeData(context, fullFilePath, jsonBytes);

        return true;
    }

    /**
     * @param context Can be app or activity, used to load tracked data collection
     * @return a new TrackedDataObjectCollection object with the current one's changes reverted
     */
    public TrackedDataObjectCollection reset(Context context, List<TrackedDataObject> trackedDataObjectList) {
        return loadSavedCollection(context, filename, trackedDataObjectList);
    }

    /**
     * Reset the changes without committing them.
     * @param filename that will be used to save this collection if commitChanges is called during its execution
     */
    protected static TrackedDataObjectCollection newInstance(
            String filename,
            List<? extends TrackedDataObject> trackedDataObjectList)
    {
        TrackedDataObjectCollection collection = new TrackedDataObjectCollection();
        collection.filename = filename;
        collection.setTrackedItems(trackedDataObjectList);
        return collection;
    }

    /**
     * @param context   Can be app or activity, used to load tracked data collection
     * @param filename  The filename of the saved collection, must be unique
     * @param trackedDataObjectList The list of TrackedDataObject that this collection uses
     * @return          a new instance of the collection if none exists,
     */
    public static @NonNull TrackedDataObjectCollection loadSavedCollection(
            Context context,
            String filename,
            List<? extends TrackedDataObject> trackedDataObjectList)
    {
        FileAccess fileAccess = verifyFileAccess();

        if (fileAccess == null) {
            return newInstance(filename, trackedDataObjectList);
        }

        String fullFilePath = TRACKED_DATA_STORE_PATH + File.separator + filename;
        byte[] jsonBytes = fileAccess.readData(context, fullFilePath);

        // If the collection does not exists, create a blank one we can edit
        TrackedDataObjectCollection collection;
        if (jsonBytes == null) {
            collection = newInstance(filename, trackedDataObjectList);
        } else { // parse the collection from the saved json
            String trackedDataCollectionJson = new String(jsonBytes);
            Gson gson = createGson();
            collection = gson.fromJson(trackedDataCollectionJson, TrackedDataObjectCollection.class);
        }
        collection.setTrackedItems(trackedDataObjectList);
        return collection;
    }

    /**
     * @return a FileAccess object, if one is available and valid, null otherwise
     */
    protected static FileAccess verifyFileAccess() {
        StorageAccess storageAccess = StorageAccess.getInstance();
        if (storageAccess == null) {
            LOG.error("StorageAccess is null");
            return null;
        }

        FileAccess fileAccess = StorageAccess.getInstance().getFileAccess();
        if (fileAccess == null) {
            LOG.error("File access is null");
            return null;
        }

        return fileAccess;
    }

    public String getFilename() {
        return filename;
    }

    protected static Gson createGson() {
        return new Gson();
    }

    public int getMomentInDayRepeatTimeInterval() {
        return momentInDayRepeatTimeInterval;
    }

    public void setMomentInDayRepeatTimeInterval(int momentInDayRepeatTimeInterval) {
        this.momentInDayRepeatTimeInterval = momentInDayRepeatTimeInterval;
    }

    public long getTrackingSurveyRepeatTimeInterval() {
        return trackingSurveyRepeatTimeInterval;
    }

    public void setTrackingSurveyRepeatTimeInterval(long trackingSurveyRepeatTimeInterval) {
        this.trackingSurveyRepeatTimeInterval = trackingSurveyRepeatTimeInterval;
    }

    public Date getLastCompletionDate() {
        return lastCompletionDate;
    }

    public void setLastCompletionDate(Date lastCompletionDate) {
        this.lastCompletionDate = lastCompletionDate;
    }

    public Date getLastTrackingSurveyDate() {
        return lastTrackingSurveyDate;
    }

    public void setLastTrackingSurveyDate(Date lastTrackingSurveyDate) {
        this.lastTrackingSurveyDate = lastTrackingSurveyDate;
    }

    public List<? extends TrackedDataObject> getSelectedItems() {
        return selectedItems;
    }

    public void setSelectedItems(List<? extends TrackedDataObject> selectedItems) {
        this.selectedItems = selectedItems;
    }

    public List<? extends TrackedDataObject> getTrackedItems() {
        return trackedItems;
    }

    public void setTrackedItems(List<? extends TrackedDataObject> trackedItems) {
        this.trackedItems = trackedItems;
    }
}
