package org.sagebionetworks.bridge.researchstack;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;

import com.google.common.base.Strings;

import org.joda.time.DateTime;
import org.researchstack.backbone.ResourceManager;
import org.researchstack.backbone.model.SchedulesAndTasksModel;
import org.researchstack.backbone.model.TaskModel;
import org.researchstack.backbone.model.survey.factory.SurveyFactory;
import org.researchstack.backbone.result.FileResult;
import org.researchstack.backbone.result.Result;
import org.researchstack.backbone.result.StepResult;
import org.researchstack.backbone.result.TappingIntervalResult;
import org.researchstack.backbone.result.TaskResult;
import org.researchstack.backbone.result.logger.DataLoggerManager;
import org.researchstack.backbone.storage.NotificationHelper;
import org.researchstack.backbone.storage.database.AppDatabase;
import org.researchstack.backbone.storage.database.TaskNotification;
import org.researchstack.backbone.task.Task;
import org.researchstack.skin.AppPrefs;
import org.researchstack.skin.notification.TaskAlertReceiver;
import org.researchstack.skin.schedule.ScheduleHelper;
import org.sagebionetworks.bridge.android.BridgeConfig;
import org.sagebionetworks.bridge.android.manager.BridgeManagerProvider;
import org.sagebionetworks.bridge.data.Archive;
import org.sagebionetworks.bridge.data.ArchiveFile;
import org.sagebionetworks.bridge.researchstack.factory.ArchiveFactory;
import org.sagebionetworks.bridge.researchstack.factory.ArchiveFileFactory;
import org.sagebionetworks.bridge.researchstack.survey.SurveyTaskScheduleModel;
import org.sagebionetworks.bridge.researchstack.wrapper.StorageAccessWrapper;
import org.sagebionetworks.bridge.rest.RestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import rx.Single;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class TaskHelper {
    private static final Logger logger = LoggerFactory.getLogger(TaskHelper.class);

    // these are used to getConsent task/step guids without rereading the json files and iterating through
    private final Map<String, String> loadedTaskGuids = new HashMap<>();
    private final Map<String, String> loadedTaskDates = new HashMap<>();
    private final Map<String, String> loadedTaskCrons = new HashMap<>();

    private final StorageAccessWrapper storageAccess;
    private final ResourceManager resourceManager;
    private final AppPrefs appPrefs;
    private final NotificationHelper notificationHelper;
    private final BridgeManagerProvider bridgeManagerProvider;

    private ArchiveFactory archiveFactory = ArchiveFactory.INSTANCE;
    private ArchiveFileFactory archiveFileFactory = ArchiveFileFactory.INSTANCE;
    private SurveyFactory surveyFactory = SurveyFactory.INSTANCE;

    public TaskHelper(
            StorageAccessWrapper storageAccess,
            ResourceManager resourceManager,
            AppPrefs appPrefs,
            NotificationHelper notificationHelper,
            BridgeManagerProvider bridgeManagerProvider) {
        this.storageAccess = storageAccess;
        this.resourceManager = resourceManager;
        this.appPrefs = appPrefs;
        this.notificationHelper = notificationHelper;
        this.bridgeManagerProvider = bridgeManagerProvider;
    }

    // To allow unit tests to mock.
    @VisibleForTesting
    void setArchiveFactory(@NonNull ArchiveFactory archiveFactory) {
        this.archiveFactory = archiveFactory;
    }

    // To allow unit tests to mock.
    @VisibleForTesting
    void setArchiveFactory(@NonNull ArchiveFileFactory archiveFileFactory) {
        this.archiveFileFactory = archiveFileFactory;
    }

    // To allow unit tests to mock.
    @VisibleForTesting
    void setSurveyFactory(@NonNull SurveyFactory surveyFactory) {
        this.surveyFactory = surveyFactory;
    }

    public SchedulesAndTasksModel loadTasksAndSchedules(Context context) {
        SchedulesAndTasksModel schedulesAndTasksModel =
                resourceManager.getTasksAndSchedules().create(context);

        AppDatabase db = storageAccess.getAppDatabase();

        List<SchedulesAndTasksModel.ScheduleModel> schedules = new ArrayList<>();
        for (SchedulesAndTasksModel.ScheduleModel schedule : schedulesAndTasksModel.schedules) {
            if (schedule.tasks.size() == 0) {
                logger.error("No tasks in schedule");
                continue;
            }

            // only supporting one task per schedule for now
            SchedulesAndTasksModel.TaskScheduleModel task = schedule.tasks.get(0);

            if (task.taskFileName == null) {
                logger.error(
                        "No filename found for task with id: " + task.taskID);
                continue;
            }

            // loading the task json here is bad, but the taskID is in the schedule
            // json but the readable id is in the task json
            TaskModel taskModel = loadTaskModel(context, task);
            TaskResult result = db.loadLatestTaskResult(taskModel.identifier);

            // cache cron string for later lookup
            loadedTaskCrons.put(taskModel.identifier, schedule.scheduleString);

            if (result == null) {
                schedules.add(schedule);
            } else if (!Strings.isNullOrEmpty(schedule.scheduleString)) {
                Date date = ScheduleHelper.nextSchedule(schedule.scheduleString, result.getEndDate());
                if (date.before(new Date())) {
                    schedules.add(schedule);
                }
            }
        }

        schedulesAndTasksModel.schedules = schedules;
        return schedulesAndTasksModel;
    }

    @NonNull
    protected TaskModel loadTaskModel(
            @NonNull Context context, @NonNull SchedulesAndTasksModel.TaskScheduleModel task) {
        TaskModel taskModel = resourceManager.getTask(task.taskFileName).create(context);
        cacheSurveyGuidCreatedOn(taskModel);
        return taskModel;
    }

    // Helper method to cache the survey guid and createdOn for a give survey task model.
    private void cacheSurveyGuidCreatedOn(@NonNull TaskModel taskModel) {
        loadedTaskGuids.put(taskModel.identifier, taskModel.guid);
        loadedTaskDates.put(taskModel.identifier, taskModel.createdOn);
    }

    /**
     * Given the ResearchStack task model, load a survey. This can either load a survey from a
     * static file, or it can call Bridge Server to get the survey. The returned Single will never
     * be null, though it may contain a null result if loading the survey failed, or if the task
     * model doesn't represent a survey.
     *
     * @param context activity context
     * @param task    task model, which may or may not represent a survey
     * @return constructed survey, or null if the task model wasn't a survey or could not be loaded
     */
    @NonNull
    public Single<Task> loadTask(
            @NonNull Context context, @NonNull SchedulesAndTasksModel.TaskScheduleModel task) {
        Single<TaskModel> taskModelSingle;
        if (task instanceof SurveyTaskScheduleModel) {
            // Call server, then convert the server's survey model to ResearchStack's equivalent
            // TaskModel.
            SurveyTaskScheduleModel surveyTaskScheduleModel = (SurveyTaskScheduleModel) task;
            taskModelSingle = bridgeManagerProvider.getSurveyManager()
                    .getSurvey(surveyTaskScheduleModel.surveyGuid, surveyTaskScheduleModel
                            .surveyCreatedOn)
                    .map(survey -> {
                        TaskModel taskModel = RestUtils.toType(survey, TaskModel.class);
                        cacheSurveyGuidCreatedOn(taskModel);
                        return taskModel;
                    });
        } else if (!Strings.isNullOrEmpty(task.taskFileName)) {
            // Load survey from static JSON.
            taskModelSingle = Single.just(loadTaskModel(context, task));
        } else {
            // Unsupported. Return null.
            return Single.just(null);
        }

        return taskModelSingle.map(taskModel -> surveyFactory.createSmartSurveyTask(context,
                taskModel));
    }

    /**
     * Uploads the task result to Bridge with the given schema ID and default revision 1.
     *
     * @param schemaId   schema ID for this task
     * @param taskResult task results
     */
    public void uploadActivityResult(@NonNull String schemaId, @NonNull TaskResult taskResult) {
        uploadTaskResult(taskResult, archiveFactory.forActivity(schemaId));
    }

    /**
     * Uploads the task result to Bridge with the given schema ID and revision.
     *
     * @param schemaId         schema ID for this task
     * @param schemaRevisionId schema revision for this task
     * @param taskResult       task results
     */
    public void uploadActivityResult(
            @NonNull String schemaId, int schemaRevisionId, @NonNull TaskResult taskResult) {
        uploadTaskResult(taskResult, archiveFactory.forActivity(schemaId, schemaRevisionId));
    }

    /**
     * Uploads the task result to Bridge, where the task was a server-side survey. Survey guid and
     * createdOn are loaded from the cache when the task was originally loaded. Task ID should match
     * the survey's identifier. See loadTask().
     *
     * @param taskResult task results
     */
    public void uploadSurveyResult(@NonNull TaskResult taskResult) {
        // Figure out surveyGuid and createdOn from task.
        String taskId = taskResult.getIdentifier();
        String surveyGuid = getGuid(taskId);
        DateTime surveyCreatedOn = DateTimeUtils.parseDateTime(getCreatedOnDate(taskId));

        // Upload only if we have a surveyGuid/CreatedOn. Otherwise, the Archive library crashes.
        if (!Strings.isNullOrEmpty(surveyGuid) && surveyCreatedOn != null) {
            uploadTaskResult(taskResult, archiveFactory.forSurvey(surveyGuid, surveyCreatedOn));
        } else {
            logger.error("No surveyGuid/CreatedOn for task " + taskId +
                    ", falling back to task ID as schema ID");
            uploadActivityResult(taskId, taskResult);
        }
    }

    //package private for test access
    void uploadTaskResult(TaskResult taskResult, Archive.Builder builder) {
        BridgeConfig config = bridgeManagerProvider.getBridgeConfig();
        builder.withAppVersionName(config.getAppVersionName())
                .withPhoneInfo(config.getDeviceName());

        String taskId = taskResult.getIdentifier();

        // Update/Create TaskNotificationService
        if (appPrefs.isTaskReminderEnabled()) {
            logger.info("SampleDataProvider", "uploadTaskResult() _ isTaskReminderEnabled() = true");

            String chronTime = findChronTime(taskId);

            // If chronTime is null then either the task is not repeating OR its not found within
            // the task_and_schedules.xml
            if (chronTime != null) {
                scheduleReminderNotification(taskResult.getEndDate(), chronTime);
            }
        }

        // Traverse through the StepResult maps and get an ordered list of Results
        List<Result> results = flattenResults(taskResult);
        for (Result result : results) {
            ArchiveFile archiveFile = archiveFileFactory.fromResult(result);
            if (archiveFile != null) {
                builder.addDataFile(archiveFile);
            } else {
                logger.error("Failed to convert Result to BridgeDataInput " + result.toString());
            }
        }

        String archiveFilename = taskId + "_" + UUID.randomUUID().toString() + ".zip";

        bridgeManagerProvider.getUploadManager()
                .queueUpload(archiveFilename, builder.build())
                .doOnSuccess(uploadFile -> {
                    logger.debug("Attempting upload in io() thread");
                    bridgeManagerProvider.getUploadManager()
                            .processUploadFile(uploadFile)
                            .subscribeOn(Schedulers.io())
                            .subscribe();
                })
                .toCompletable()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                    logger.debug("Successfully queued upload");
                    for (Result result : results) {
                        if (result instanceof FileResult) {
                            FileResult fileResult = (FileResult) result;
                            DataLoggerManager.getInstance().deleteFileStatus(fileResult.getFile());
                        }
                    }
                });
    }

    private void scheduleReminderNotification(Date endDate, String chronTime) {
        logger.info("SampleDataProvider", "scheduleReminderNotification()");

        // Save TaskNotification to DB
        TaskNotification notification = new TaskNotification();
        notification.endDate = endDate;
        notification.chronTime = chronTime;
        notificationHelper.saveTaskNotification(notification);

        // Add notification to Alarm Manager
        Intent intent = TaskAlertReceiver.createCreateIntent(notification);
        bridgeManagerProvider.getApplicationContext().sendBroadcast(intent);
    }

    /**
     * @param identifier identifier for the result
     * @return the filename to use for the bridge result
     */
    public String bridgifyIdentifier(String identifier) {
        return identifier.replace(".", "_");
    }

    // these stink, I should be able to query the DB and find these
    protected String getCreatedOnDate(String identifier) {
        return loadedTaskDates.get(identifier);
    }

    protected String getGuid(String identifier) {
        return loadedTaskGuids.get(identifier);
    }

    // for unit testing
    void putTaskChron(String identifier, String chron) {
        loadedTaskCrons.put(identifier, chron);
    }

    protected String findChronTime(String identifier) {
        return loadedTaskCrons.get(identifier);
    }

    /**
     * This tasks a map structure, which step results are, and flattens it to a List
     *
     * @param taskResult from the result of a Task, can contain any combination of Result objects
     *                   they can be nested and they can be as deep as desired
     * @return a list of Result objects from recursively investigating all StepResult objects
     */
    public static List<Result> flattenResults(TaskResult taskResult) {
        List<Result> resultList = new ArrayList<>();

        if (taskResult != null) {
            for (String key : taskResult.getResults().keySet()) {
                StepResult stepResult = taskResult.getResults().get(key);
                addResultsRecursively(stepResult, resultList);
            }
        }

        return resultList;
    }

    /**
     * @param stepResult can contain nested step results
     * @param resultList the result list to add a StepResult or Result to
     * @return false if there are no more results look into, true if the method went deeper
     */
    private static boolean addResultsRecursively(StepResult stepResult, List<Result> resultList) {
        boolean wentDeeper = false;

        if (stepResult != null) {
            Map stepResultMap = stepResult.getResults();

            for (Object key : stepResultMap.keySet()) {
                Object value = stepResultMap.get(key);

                // The StepResult is a special case, because it could contain nested StepResults,
                // or it could contain FileResults, which need added themselves,
                // while the StepResult still needs added too if it isn't nested
                if (value instanceof StepResult) {
                    wentDeeper = true;

                    StepResult nestedStepResult = (StepResult) value;
                    if (!nestedStepResult.getResults().isEmpty()) {
                        addResultsRecursively((StepResult) value, resultList);
                    }
                } else if (value instanceof FileResult) {
                    resultList.add((Result) value);
                } else if (value instanceof TappingIntervalResult) {
                    resultList.add((Result) value);
                    wentDeeper = true;
                }
            }
        }

        if (!wentDeeper) {
            resultList.add(stepResult);
        }

        return wentDeeper;
    }
}