package org.sagebionetworks.bridge.researchstack;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.gson.reflect.TypeToken;

import org.apache.commons.lang3.StringUtils;
import org.researchstack.backbone.ResourceManager;
import org.researchstack.backbone.model.SchedulesAndTasksModel;
import org.researchstack.backbone.result.FileResult;
import org.researchstack.backbone.result.Result;
import org.researchstack.backbone.result.StepResult;
import org.researchstack.backbone.result.TaskResult;
import org.researchstack.backbone.storage.NotificationHelper;
import org.researchstack.backbone.storage.database.AppDatabase;
import org.researchstack.backbone.storage.database.TaskNotification;
import org.researchstack.backbone.task.Task;
import org.researchstack.backbone.utils.FormatHelper;
import org.researchstack.backbone.utils.LogExt;
import org.researchstack.backbone.utils.StepResultHelper;
import org.researchstack.skin.AppPrefs;
import org.researchstack.skin.model.TaskModel;
import org.researchstack.skin.notification.TaskAlertReceiver;
import org.researchstack.skin.schedule.ScheduleHelper;
import org.researchstack.skin.task.SmartSurveyTask;
import org.sagebionetworks.bridge.researchstack.survey.SurveyAnswer;
import org.sagebionetworks.bridge.researchstack.upload.BridgeDataInput;
import org.sagebionetworks.bridge.researchstack.wrapper.StorageAccessWrapper;
import org.sagebionetworks.bridge.rest.api.ForConsentedUsersApi;
import org.sagebionetworks.bridge.researchstack.upload.Info;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TaskHelper {
    private static final Logger logger = LoggerFactory.getLogger(TaskHelper.class);

    // these are used to getConsent task/step guids without rereading the json files and iterating through
    private final Map<String, String> loadedTaskGuids = new HashMap<String, String>();
    private final Map<String, String> loadedTaskDates = new HashMap<String, String>();
    private final Map<String, String> loadedTaskCrons = new HashMap<String, String>();

    private final StorageAccessWrapper storageAccess;
    private final ResourceManager resourceManager;
    private final AppPrefs appPrefs;
    private final UploadHandler uploadHandler;

    public TaskHelper(
          StorageAccessWrapper storageAccess,
          ResourceManager resourceManager,
          AppPrefs appPrefs,
          UploadHandler uploadHandler)
    {
        this.storageAccess = storageAccess;
        this.resourceManager = resourceManager;
        this.appPrefs = appPrefs;
        this.uploadHandler = uploadHandler;
    }

    public SchedulesAndTasksModel loadTasksAndSchedules(Context context) {
        SchedulesAndTasksModel schedulesAndTasksModel =
                resourceManager.getTasksAndSchedules().create(context);

        AppDatabase db = storageAccess.getAppDatabase();

        List<SchedulesAndTasksModel.ScheduleModel> schedules = new ArrayList<SchedulesAndTasksModel.ScheduleModel>();
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
            } else if (StringUtils.isNotEmpty(schedule.scheduleString)) {
                Date date = ScheduleHelper.nextSchedule(schedule.scheduleString, result.getEndDate());
                if (date.before(new Date())) {
                    schedules.add(schedule);
                }
            }
        }

        schedulesAndTasksModel.schedules = schedules;
        return schedulesAndTasksModel;
    }

    protected TaskModel loadTaskModel(Context context, SchedulesAndTasksModel.TaskScheduleModel task) {
        TaskModel taskModel = resourceManager.getTask(task.taskFileName).create(context);

        // cache guid and createdOnDate
        loadedTaskGuids.put(taskModel.identifier, taskModel.guid);
        loadedTaskDates.put(taskModel.identifier, taskModel.createdOn);

        return taskModel;
    }

    public Task loadTask(Context context, SchedulesAndTasksModel.TaskScheduleModel task) {
        // currently we only support task json files, override this method to taskClassName
        if (StringUtils.isEmpty(task.taskFileName)) {
            return null;
        }

        TaskModel taskModel = loadTaskModel(context, task);
        SmartSurveyTask smartSurveyTask = new SmartSurveyTask(context, taskModel);
        return smartSurveyTask;
    }

    public void uploadTaskResult(
            Context context,
            ForConsentedUsersApi forConsentedUsersApi,
            TaskResult taskResult)
    {
        // Update/Create TaskNotificationService
        if (appPrefs.isTaskReminderEnabled()) {
            logger.info("SampleDataProvider", "uploadTaskResult() _ isTaskReminderEnabled() = true");

            String chronTime = findChronTime(taskResult.getIdentifier());

            // If chronTime is null then either the task is not repeating OR its not found within
            // the task_and_schedules.xml
            if (chronTime != null) {
                scheduleReminderNotification(context, taskResult.getEndDate(), chronTime);
            }
        }

        List<BridgeDataInput> files = new ArrayList<BridgeDataInput>();

        // Traverse through the StepResult maps and get an ordered list of Results
        List<Result> resultList = flattenResults(taskResult);

        // Package all the Results as BridgeDataInput objects to send up to server as an Archive
        for (Result result : resultList) {
            BridgeDataInput bridgeDataInput = toBridgeDataInput(result);
            if (bridgeDataInput != null) {
                files.add(bridgeDataInput);
            } else {
                LogExt.e(getClass(), "Failed to convert Result to BridgeDataInput " + result.toString());
            }
        }

        uploadHandler.uploadBridgeData(forConsentedUsersApi,
            new Info(context, getGuid(taskResult.getIdentifier()),
                getCreatedOnDate(taskResult.getIdentifier())), files);
    }

    private void scheduleReminderNotification(Context context, Date endDate, String chronTime) {
        logger.info("SampleDataProvider", "scheduleReminderNotification()");

        // Save TaskNotification to DB
        TaskNotification notification = new TaskNotification();
        notification.endDate = endDate;
        notification.chronTime = chronTime;
        NotificationHelper.getInstance(context).saveTaskNotification(notification);

        // Add notification to Alarm Manager
        Intent intent = new Intent(TaskAlertReceiver.ALERT_CREATE);
        intent.putExtra(TaskAlertReceiver.KEY_NOTIFICATION, notification);
        context.sendBroadcast(intent);
    }

    protected static BridgeDataInput toBridgeDataInput(Result result) {
        if (result instanceof StepResult) {
            StepResult stepResult = (StepResult)result;

            // If a step result has an answer format, we know that it was formed from a QuestionStep
            if (stepResult.getAnswerFormat() != null) {

                SurveyAnswer surveyAnswer = SurveyAnswer.create(stepResult);
                return new BridgeDataInput(surveyAnswer, SurveyAnswer.class,
                        bridgifyIdentifier(stepResult.getIdentifier()) + ".json",
                        FormatHelper.DEFAULT_FORMAT.format(stepResult.getStartDate()));

            } else {  // otherwise make a generic String, Object JSON Map

                Type typeOfMap = new TypeToken<Map<String, Object>>() { }.getType();
                return new BridgeDataInput(stepResult.getResults(), typeOfMap,
                        bridgifyIdentifier(stepResult.getIdentifier()) + ".json",
                        FormatHelper.DEFAULT_FORMAT.format(stepResult.getStartDate()));

            }

        } else if (result instanceof FileResult) {

            FileResult fileResult = (FileResult)result;
            return new BridgeDataInput(fileResult.getFile(),
                    FormatHelper.DEFAULT_FORMAT.format(fileResult.getStartDate()));
        }

        return null;
    }

    private static String bridgifyIdentifier(String identifier) {
        return identifier.replace(".", "_");
    }

    // these stink, I should be able to query the DB and find these

    protected String getCreatedOnDate(String identifier) {
        return loadedTaskDates.get(identifier);
    }

    protected String getGuid(String identifier) {
        return loadedTaskGuids.get(identifier);
    }

    protected String findChronTime(String identifier) {
        return loadedTaskCrons.get(identifier);
    }

    /**
     * This tasks a map structure, which step results are, and flattens it to a List<Result>s
     *
     * @param taskResult from the result of a Task, can contain any combination of Result objects
     *                   they can be nested and they can be as deep as desired
     *
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

                    StepResult nestedStepResult = (StepResult)value;
                    if (!nestedStepResult.getResults().isEmpty()) {
                        addResultsRecursively((StepResult) value, resultList);
                    }
                } else if (value instanceof FileResult) {
                    resultList.add((Result) value);
                }
            }
        }

        if (!wentDeeper) {
            resultList.add(stepResult);
        }

        return wentDeeper;
    }
}