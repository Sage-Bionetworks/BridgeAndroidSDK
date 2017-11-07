package org.sagebionetworks.bridge.researchstack.factory;

import android.content.Context;
import android.support.annotation.NonNull;
import org.researchstack.backbone.model.TaskModel;
import org.researchstack.backbone.task.SmartSurveyTask;

/**
 * This class encapsulates creating Task instances, to allow us to separate the logic of creating
 * Tasks from using tasks. This helps simplify tests.
 */
public class TaskFactory {
    /** Singleton instance. */
    public static final TaskFactory INSTANCE = new TaskFactory();

    /**
     * Private constructor, to enforce the singleton property. This prevents creating additional
     * instances, but the factory can still be mocked.
     */
    private TaskFactory() {
    }

    /**
     * Wrapper for the SmartSurveyTask constructor.
     *
     * @param context   activity context
     * @param taskModel task model to create the survey task from
     * @return created survey task
     */
    @NonNull
    public SmartSurveyTask newSmartSurveyTask(
            @NonNull Context context, @NonNull TaskModel taskModel) {
        return new SmartSurveyTask(context, taskModel);
    }
}
