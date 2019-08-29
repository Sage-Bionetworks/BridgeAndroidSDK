package org.sagebionetworks.bridge.researchstack;

import android.content.Context;
import android.content.Intent;

import com.google.common.collect.Maps;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sagebionetworks.researchstack.backbone.ResourceManager;
import org.sagebionetworks.researchstack.backbone.ResourcePathManager;
import org.sagebionetworks.researchstack.backbone.answerformat.AnswerFormat;
import org.sagebionetworks.researchstack.backbone.answerformat.BooleanAnswerFormat;
import org.sagebionetworks.researchstack.backbone.answerformat.ChoiceAnswerFormat;
import org.sagebionetworks.researchstack.backbone.model.Choice;
import org.sagebionetworks.researchstack.backbone.model.SchedulesAndTasksModel;
import org.sagebionetworks.researchstack.backbone.model.TaskModel;
import org.sagebionetworks.researchstack.backbone.model.survey.factory.SurveyFactory;
import org.sagebionetworks.researchstack.backbone.result.AudioResult;
import org.sagebionetworks.researchstack.backbone.result.FileResult;
import org.sagebionetworks.researchstack.backbone.result.Result;
import org.sagebionetworks.researchstack.backbone.result.StepResult;
import org.sagebionetworks.researchstack.backbone.result.TappingIntervalResult;
import org.sagebionetworks.researchstack.backbone.result.TaskResult;
import org.sagebionetworks.researchstack.backbone.result.TimedWalkResult;
import org.sagebionetworks.researchstack.backbone.step.QuestionStep;
import org.sagebionetworks.researchstack.backbone.step.active.AudioStep;
import org.sagebionetworks.researchstack.backbone.step.active.CountdownStep;
import org.sagebionetworks.researchstack.backbone.step.active.TappingIntervalStep;
import org.sagebionetworks.researchstack.backbone.step.active.TimedWalkStep;
import org.sagebionetworks.researchstack.backbone.storage.NotificationHelper;
import org.sagebionetworks.researchstack.backbone.storage.database.TaskNotification;
import org.sagebionetworks.researchstack.backbone.task.SmartSurveyTask;
import org.sagebionetworks.researchstack.backbone.task.Task;
import org.sagebionetworks.researchstack.backbone.task.factory.TappingTaskFactory;
import org.sagebionetworks.researchstack.backbone.AppPrefs;
import org.sagebionetworks.researchstack.backbone.notification.TaskAlertReceiver;
import org.sagebionetworks.bridge.android.BridgeConfig;
import org.sagebionetworks.bridge.android.manager.BridgeManagerProvider;
import org.sagebionetworks.bridge.android.manager.SurveyManager;
import org.sagebionetworks.bridge.android.manager.UploadManager;
import org.sagebionetworks.bridge.data.Archive;
import org.sagebionetworks.bridge.data.ArchiveFile;
import org.sagebionetworks.bridge.researchstack.factory.ArchiveFactory;
import org.sagebionetworks.bridge.researchstack.factory.ArchiveFileFactory;
import org.sagebionetworks.bridge.researchstack.survey.SurveyTaskScheduleModel;
import org.sagebionetworks.bridge.researchstack.wrapper.StorageAccessWrapper;
import org.sagebionetworks.bridge.rest.model.Survey;
import org.spongycastle.cms.CMSException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import rx.Completable;
import rx.Single;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.sagebionetworks.researchstack.backbone.result.TappingIntervalResult.TappingButtonIdentifier.TappedButtonLeft;
import static org.sagebionetworks.researchstack.backbone.result.TappingIntervalResult.TappingButtonIdentifier.TappedButtonNone;
import static org.sagebionetworks.researchstack.backbone.task.factory.TaskFactory.Constants.AccelerometerRecorderIdentifier;
import static org.sagebionetworks.researchstack.backbone.task.factory.TaskFactory.Constants.ActiveTaskLeftHandIdentifier;
import static org.sagebionetworks.researchstack.backbone.task.factory.TaskFactory.Constants.ActiveTaskRightHandIdentifier;
import static org.sagebionetworks.researchstack.backbone.task.factory.TaskFactory.Constants.DeviceMotionRecorderIdentifier;
import static org.sagebionetworks.researchstack.backbone.task.factory.TaskFactory.Constants.LocationRecorderIdentifier;
import static org.sagebionetworks.researchstack.backbone.task.factory.TaskFactory.Constants.PedometerRecorderIdentifier;
import static org.sagebionetworks.researchstack.backbone.task.factory.TaskFactory.Constants.TappingStepIdentifier;
import static org.sagebionetworks.researchstack.backbone.task.factory.WalkingTaskFactory.TimedWalkFormAFOStepIdentifier;
import static org.sagebionetworks.researchstack.backbone.task.factory.WalkingTaskFactory.TimedWalkFormAssistanceStepIdentifier;
import static org.sagebionetworks.researchstack.backbone.task.factory.WalkingTaskFactory.TimedWalkTrial1StepIdentifier;
import static org.sagebionetworks.researchstack.backbone.task.factory.WalkingTaskFactory.TimedWalkTrial2StepIdentifier;
import static org.sagebionetworks.researchstack.backbone.task.factory.WalkingTaskFactory.TimedWalkTurnAroundStepIdentifier;

/**
 * Created by TheMDP on 3/3/17.
 */
@RunWith(PowerMockRunner.class)
@SuppressWarnings("unchecked")
@PrepareForTest({TaskAlertReceiver.class, AndroidSchedulers.class})
public class TaskHelperTest {
    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final String SCHEMA_ID = "my-schema";
    private static final int SCHEMA_REV = 3;
    private static final String SURVEY_CREATED_ON_STRING = "2017-09-22T13:52:26.685Z";
    private static final DateTime SURVEY_CREATED_ON = DateTime.parse(SURVEY_CREATED_ON_STRING);
    private static final String SURVEY_GUID = "my-survey-guid";
    private static final String SURVEY_IDENTIFIER = "my-survey";
    private static final String TASK_ID = "my-task";

    private TaskHelper taskHelper;

    @Mock
    private ArchiveFactory archiveFactory;
    @Mock
    private ArchiveFileFactory archiveFileFactory;
    @Mock
    StorageAccessWrapper storageAccess;
    @Mock
    ResourceManager resourceManager;
    @Mock
    AppPrefs appPrefs;
    @Mock
    NotificationHelper notificationHelper;
    @Mock
    Context applicationContext;
    @Mock
    private SurveyManager surveyManager;
    @Mock
    private SurveyFactory surveyFactory;
    @Mock
    UploadManager uploadManager;
    @Mock
    BridgeManagerProvider bridgeManagerProvider;
    @Mock
    BridgeConfig bridgeConfig;

    @Before
    public void setupTest() {
        MockitoAnnotations.initMocks(this);

        when(bridgeManagerProvider.getSurveyManager()).thenReturn(surveyManager);
        when(bridgeManagerProvider.getUploadManager()).thenReturn(uploadManager);
        when(bridgeManagerProvider.getApplicationContext()).thenReturn(applicationContext);
        when(bridgeManagerProvider.getBridgeConfig()).thenReturn(bridgeConfig);

        taskHelper = spy(new TaskHelper(storageAccess, resourceManager, appPrefs, notificationHelper,
                bridgeManagerProvider));
        taskHelper.setArchiveFactory(archiveFactory);
        taskHelper.setArchiveFileFactory(archiveFileFactory);
        taskHelper.setSurveyFactory(surveyFactory);
    }

    @Test
    public void loadTask_ServerSideSurvey() {
        // Mock SurveyManager - For Survey, we only care about identifier, guid, and createdOn.
        // Everything else is handled by and tested in SmartSurveyTask.
        Survey survey = new Survey().guid(SURVEY_GUID).createdOn(SURVEY_CREATED_ON).identifier(
                SURVEY_IDENTIFIER);
        when(surveyManager.getSurvey(SURVEY_GUID, SURVEY_CREATED_ON)).thenReturn(Single.just(
                survey));

        // Mock TaskFactory
        SmartSurveyTask factoryOutput = mock(SmartSurveyTask.class);
        when(surveyFactory.createSmartSurveyTask(any(), any())).thenReturn(factoryOutput);

        // Make SurveyTaskScheduleModel - The only params we care about are guid and createdOn.
        SurveyTaskScheduleModel surveyScheduleModel = new SurveyTaskScheduleModel();
        surveyScheduleModel.surveyGuid = SURVEY_GUID;
        surveyScheduleModel.surveyCreatedOn = SURVEY_CREATED_ON;

        // Execute and validate
        Task helperOutput = taskHelper.loadTask(applicationContext, surveyScheduleModel)
                .toBlocking().value();
        assertSame(factoryOutput, helperOutput);

        // Verify back-ends
        verify(surveyManager).getSurvey(SURVEY_GUID, SURVEY_CREATED_ON);

        ArgumentCaptor<TaskModel> taskModelCaptor = ArgumentCaptor.forClass(TaskModel.class);
        verify(surveyFactory).createSmartSurveyTask(same(applicationContext), taskModelCaptor
                .capture());
        TaskModel taskModel = taskModelCaptor.getValue();
        assertEquals(SURVEY_GUID, taskModel.guid);
        assertEquals(SURVEY_CREATED_ON_STRING, taskModel.createdOn);
        assertEquals(SURVEY_IDENTIFIER, taskModel.identifier);

        // Verify cached survey guid/createdOn
        assertEquals(SURVEY_GUID, taskHelper.getGuid(SURVEY_IDENTIFIER));
        assertEquals(SURVEY_CREATED_ON_STRING, taskHelper.getCreatedOnDate(SURVEY_IDENTIFIER));
    }

    @Test
    public void loadTask_FileBasedSurvey() {
        // Mock Resource and ResourceManager
        TaskModel loadedTaskModel = new TaskModel();
        ResourcePathManager.Resource mockResource = mock(ResourcePathManager.Resource.class);
        when(mockResource.create(any())).thenReturn(loadedTaskModel);
        when(resourceManager.getTask(TASK_ID)).thenReturn(mockResource);

        // Mock TaskFactory
        SmartSurveyTask factoryOutput = mock(SmartSurveyTask.class);
        when(surveyFactory.createSmartSurveyTask(any(), any())).thenReturn(factoryOutput);

        // Make TaskScheduleModel - The only param we care about is taskID.
        SchedulesAndTasksModel.TaskScheduleModel taskScheduleModel =
                new SchedulesAndTasksModel.TaskScheduleModel();
        taskScheduleModel.taskID = TASK_ID;

        // Execute and validate
        Task helperOutput = taskHelper.loadTask(applicationContext, taskScheduleModel).toBlocking()
                .value();
        assertSame(factoryOutput, helperOutput);

        // Verify ResourceManager and Resource
        verify(resourceManager).getTask(TASK_ID);
        verify(mockResource).create(applicationContext);

        // Verify SurveyFactory
        verify(surveyFactory).createSmartSurveyTask(same(applicationContext),
                same(loadedTaskModel));
    }

    @Test
    public void loadTask_CannotLoadSurvey() {
        // Mock Resource and ResourceManager
        ResourcePathManager.Resource mockResource = mock(ResourcePathManager.Resource.class);
        when(mockResource.create(any())).thenThrow(RuntimeException.class);
        when(resourceManager.getTask(TASK_ID)).thenReturn(mockResource);

        // Make TaskScheduleModel - It's not a server-side survey (no guid/createdOn), and the
        // taskID doesn't load in the ResourceManager.
        SchedulesAndTasksModel.TaskScheduleModel taskScheduleModel =
                new SchedulesAndTasksModel.TaskScheduleModel();
        taskScheduleModel.taskID = TASK_ID;

        // Execute and validate.
        Task helperOutput = taskHelper.loadTask(applicationContext, taskScheduleModel).toBlocking()
                .value();
        assertNull(helperOutput);

        // Verify ResourceManager and Resource
        verify(resourceManager).getTask(TASK_ID);
        verify(mockResource).create(applicationContext);

        // Verify SurveyFactory never called.
        verify(surveyFactory, never()).createSmartSurveyTask(any(), any());
    }

    @Test
    public void uploadActivityResult_SchemaIdOnly() {
        // Mock ArchiveFactory
        Archive.Builder factoryOutput = mock(Archive.Builder.class);
        when(archiveFactory.forActivity(any())).thenReturn(factoryOutput);

        // Spy taskHelper.uploadTaskResult(). This is tested in the next test, so we don't need to
        // test it here.
        doNothing().when(taskHelper).uploadTaskResult(any(), any(), any());

        // Execute
        TaskResult helperInput = new TaskResult(TASK_ID);
        taskHelper.uploadActivityResult(SCHEMA_ID, null, helperInput);

        // Verify backends
        verify(archiveFactory).forActivity(SCHEMA_ID);
        verify(taskHelper).uploadTaskResult(any(), same(helperInput), same(factoryOutput));
    }

    @Test
    public void uploadActivityResult_SchemaIdAndRev() {
        // Mock ArchiveFactory
        Archive.Builder factoryOutput = mock(Archive.Builder.class);
        when(archiveFactory.forActivity(any(), anyInt())).thenReturn(factoryOutput);

        // Spy taskHelper.uploadTaskResult(). This is tested in the next test, so we don't need to
        // test it here.
        doNothing().when(taskHelper).uploadTaskResult(any(), any(), any());

        // Execute
        TaskResult helperInput = new TaskResult(TASK_ID);
        taskHelper.uploadActivityResult(SCHEMA_ID, SCHEMA_REV, null, helperInput);

        // Verify backends
        verify(archiveFactory).forActivity(SCHEMA_ID, SCHEMA_REV);
        verify(taskHelper).uploadTaskResult(any(), same(helperInput), same(factoryOutput));
    }

    @Test
    public void uploadSurveyResult_GuidAndCreatedOn() {
        // Spy getGuid() and getCreatedOn with the correct values.
        doReturn(SURVEY_GUID).when(taskHelper).getGuid(SURVEY_IDENTIFIER);
        doReturn(SURVEY_CREATED_ON_STRING).when(taskHelper).getCreatedOnDate(SURVEY_IDENTIFIER);

        // Mock ArchiveFactory
        Archive.Builder factoryOutput = mock(Archive.Builder.class);
        when(archiveFactory.forSurvey(any(), any())).thenReturn(factoryOutput);

        // Spy taskHelper.uploadTaskResult(). This is tested in the next test, so we don't need to
        // test it here.
        doNothing().when(taskHelper).uploadTaskResult(any(), any(), any());

        // Execute
        TaskResult helperInput = new TaskResult(SURVEY_IDENTIFIER);
        taskHelper.uploadSurveyResult(null, helperInput);

        // Verify backends
        verify(archiveFactory).forSurvey(SURVEY_GUID, SURVEY_CREATED_ON);
        verify(taskHelper).uploadTaskResult(any(), same(helperInput), same(factoryOutput));
    }

    @Test
    public void uploadSurveyResult_MissingGuid() {
        // Spy getGuid() and getCreatedOn with the correct values.
        doReturn(null).when(taskHelper).getGuid(SURVEY_IDENTIFIER);
        doReturn(SURVEY_CREATED_ON_STRING).when(taskHelper).getCreatedOnDate(SURVEY_IDENTIFIER);

        // Mock ArchiveFactory
        Archive.Builder factoryOutput = mock(Archive.Builder.class);
        when(archiveFactory.forActivity(any())).thenReturn(factoryOutput);

        // Spy taskHelper.uploadTaskResult(). This is tested in the next test, so we don't need to
        // test it here.
        doNothing().when(taskHelper).uploadTaskResult(any(), any(), any());

        // Execute
        TaskResult helperInput = new TaskResult(SURVEY_IDENTIFIER);
        taskHelper.uploadSurveyResult(null, helperInput);

        // Verify backends
        verify(archiveFactory).forActivity(SURVEY_IDENTIFIER);
        verify(taskHelper).uploadTaskResult(any(), same(helperInput), same(factoryOutput));
    }

    @Test
    public void uploadSurveyResult_MissingCreatedOn() {
        // Spy getGuid() and getCreatedOn with the correct values.
        doReturn(SURVEY_GUID).when(taskHelper).getGuid(SURVEY_IDENTIFIER);
        doReturn(null).when(taskHelper).getCreatedOnDate(SURVEY_IDENTIFIER);

        // Mock ArchiveFactory
        Archive.Builder factoryOutput = mock(Archive.Builder.class);
        when(archiveFactory.forActivity(any())).thenReturn(factoryOutput);

        // Spy taskHelper.uploadTaskResult(). This is tested in the next test, so we don't need to
        // test it here.
        doNothing().when(taskHelper).uploadTaskResult(any(), any(), any());

        // Execute
        TaskResult helperInput = new TaskResult(SURVEY_IDENTIFIER);
        taskHelper.uploadSurveyResult(null, helperInput);

        // Verify backends
        verify(archiveFactory).forActivity(SURVEY_IDENTIFIER);
        verify(taskHelper).uploadTaskResult(any(), same(helperInput), same(factoryOutput));
    }

    @Test
    public void testUploadTaskResult() throws IOException, CMSException {
        Date endDate = mock(Date.class);
        String taskId = "taskId";
        String taskChron = "chron";

        Map<String, StepResult> stepResults = Maps.newHashMap();

        String step1 = "step1";
        StepResult result1 = mock(StepResult.class);
        ArchiveFile file1 = mock(ArchiveFile.class);
        stepResults.put(step1, result1);
        when(archiveFileFactory.fromResult(result1)).thenReturn(file1);

        String step2 = "step2";
        StepResult result2 = mock(StepResult.class);
        ArchiveFile file2 = mock(ArchiveFile.class);
        stepResults.put(step2, result2);
        when(archiveFileFactory.fromResult(result2)).thenReturn(file2);

        TaskResult taskResult = mock(TaskResult.class);
        when(taskResult.getIdentifier()).thenReturn(taskId);
        when(taskResult.getEndDate()).thenReturn(endDate);
        when(taskResult.getResults()).thenReturn(stepResults);

        String appVersionName = "1.0.5";
        String phoneInfo = "Android";
        when(bridgeConfig.getAppVersionName()).thenReturn(appVersionName);
        when(bridgeConfig.getAppVersion()).thenReturn(2);
        when(bridgeConfig.getDeviceName()).thenReturn(phoneInfo);
        
        String archiveAppVersion = "version 1.0.5, build 2";

        Archive archive = mock(Archive.class);
        Archive.Builder archiveBuilder = mock(Archive.Builder.class);
        when(archiveBuilder.withAppVersionName(archiveAppVersion)).thenReturn(archiveBuilder);
        when(archiveBuilder.withPhoneInfo(phoneInfo)).thenReturn(archiveBuilder);
        when(archiveBuilder.build()).thenReturn(archive);

        when(appPrefs.isTaskReminderEnabled()).thenReturn(true);
        taskHelper.putTaskChron(taskId, taskChron);

        mockStatic(AndroidSchedulers.class);
        when(AndroidSchedulers.mainThread()).thenReturn(Schedulers.immediate());

        ArgumentCaptor<TaskNotification> taskNotificationCaptor = ArgumentCaptor
                .forClass(TaskNotification.class);

        when(uploadManager.queueUpload(any(), eq(archive)))
                .thenReturn(Single.just(new UploadManager.UploadFile()));
        when(uploadManager.processUploadFile(any())).thenReturn(Completable.complete());

        Intent notificationCreateIntent = mock(Intent.class);
        mockStatic(TaskAlertReceiver.class);
        when(TaskAlertReceiver.createCreateIntent(any())).thenReturn(notificationCreateIntent);

        taskHelper.uploadTaskResult(null, taskResult, archiveBuilder);

        verify(archiveFileFactory).fromResult(result1);
        verify(archiveFileFactory).fromResult(result2);

        verify(archiveBuilder).withPhoneInfo(phoneInfo);
        verify(archiveBuilder).withAppVersionName(archiveAppVersion);

        verify(uploadManager).queueUpload(any(), eq(archive));
        verify(uploadManager).processUploadFile(any());

        verify(notificationHelper).saveTaskNotification(taskNotificationCaptor.capture());
        verify(applicationContext).sendBroadcast(notificationCreateIntent);

        TaskNotification taskNotification = taskNotificationCaptor.getValue();

        assertEquals(endDate, taskNotification.endDate);
        assertEquals(taskChron, taskNotification.chronTime);
    }

    @Test
    public void testFlattenMapForAudioTaskResult() {
        TaskResult taskResult = TaskHelperTest.audioTaskResult();
        List<Result> resultList = TaskHelper.flattenResults(taskResult);

        assertNotNull(resultList);
        assertFalse(resultList.isEmpty());
        assertEquals(resultList.size(), 4); // 2 for audio files, 2 for step results

        int fileCount = 0;
        int stepResultCount = 0;
        for (Result result : resultList) {
            if (result instanceof StepResult) {
                stepResultCount++;
            } else if (result instanceof FileResult) {
                fileCount++;
            }
        }
        assertEquals(fileCount, 2);
        assertEquals(stepResultCount, 2);
    }

    @Test
    public void testFlattenMapForTimedWalkTaskResult() {
        TaskResult taskResult = TaskHelperTest.timedWalkTaskResult();
        List<Result> resultList = TaskHelper.flattenResults(taskResult);

        assertNotNull(resultList);
        assertFalse(resultList.isEmpty());
        assertEquals(resultList.size(), 17); // 12 for data recorder files, 5 for step results

        int fileCount = 0;
        int stepResultCount = 0;
        for (Result result : resultList) {
            if (result instanceof StepResult) {
                stepResultCount++;
            } else if (result instanceof FileResult) {
                fileCount++;
            }
        }
        assertEquals(fileCount, 12);
        assertEquals(stepResultCount, 5);
    }

    @Test
    public void testFlattenMapForTappingTaskResult() {
        TaskResult taskResult = TaskHelperTest.tappingTaskResult();
        List<Result> resultList = TaskHelper.flattenResults(taskResult);

        assertNotNull(resultList);
        assertFalse(resultList.isEmpty());
        assertEquals(resultList.size(), 4); // 2 data accel data recorders, and 2 step results, and 2 TappingIntervalResult

        int fileCount = 0;
        int tappingResultCount = 0;
        for (Result result : resultList) {
            if (result instanceof FileResult) {
                fileCount++;
            } else if (result instanceof TappingIntervalResult) {
                tappingResultCount++;
            }
        }
        assertEquals(fileCount, 2);
        assertEquals(tappingResultCount, 2);
    }

    public static TaskResult tappingTaskResult() {
        // This test is based on the results of the tapping task
        TaskResult taskResult = new TaskResult("tappingtaskresultid");

        for (int i = 0; i < 2; i++) {
            String handId = (i == 0) ? ActiveTaskRightHandIdentifier : ActiveTaskLeftHandIdentifier;
            String tappingHandIdentifier =
                    TappingTaskFactory.stepIdentifierWithHandId(TappingStepIdentifier, handId);

            TappingIntervalStep step = new TappingIntervalStep(tappingHandIdentifier);
            step.setStepDuration(40);
            StepResult<Result> stepResult = new StepResult<>(step);

            {
                FileResult result = new FileResult(
                        AccelerometerRecorderIdentifier,
                        new File(AccelerometerRecorderIdentifier + File.separator + UUID.randomUUID().toString()),
                        JSON_CONTENT_TYPE);
                result.setContentType(JSON_CONTENT_TYPE);
                result.setStartDate(new Date(System.currentTimeMillis() - 100));
                result.setEndDate(new Date(System.currentTimeMillis()));
                stepResult.getResults().put(result.getIdentifier(), result);
            }

            {
                TappingIntervalResult result = new TappingIntervalResult(step.getIdentifier());
                result.setStepViewSize(200, 200);
                result.setButtonRect1(40, 40, 80, 80);
                result.setButtonRect2(120, 120, 160, 160);

                // Add all the samples of Mock taps
                int sampleCount = 20;
                int timePerSample = step.getStepDuration() / sampleCount;
                List<TappingIntervalResult.Sample> sampleList = new ArrayList<>(sampleCount);
                long timestamp = System.currentTimeMillis();
                for (int j = 0; j < sampleCount; j++) {
                    TappingIntervalResult.Sample sample = new TappingIntervalResult.Sample();
                    sample.setLocation(50, 50);
                    sample.setTimestamp(timestamp + (timePerSample * j));
                    sample.setDuration(50);

                    if (j % 10 == 0) {
                        sample.setButtonIdentifier(TappedButtonNone);
                    } else if (j % 2 == 0) {
                        sample.setButtonIdentifier(TappedButtonLeft);
                    } else {
                        sample.setButtonIdentifier(TappedButtonLeft);
                    }

                    sampleList.add(sample);
                }
                result.setSamples(sampleList);

                stepResult.getResults().put(result.getIdentifier(), result);
            }

            taskResult.getResults().put(stepResult.getIdentifier(), stepResult);
        }

        return taskResult;
    }

    public static TaskResult audioTaskResult() {
        // This test is based on the results of the audio task
        TaskResult taskResult = new TaskResult("audiotaskresultid");

        CountdownStep countdownStep = new CountdownStep("countdown");
        StepResult<AudioResult> stepResult1 = new StepResult<>(countdownStep);
        AudioResult audio1 = new AudioResult("audio1", new File("a1.mp4"), "audio/mpeg");
        stepResult1.setResult(audio1);
        taskResult.setStepResultForStepIdentifier(stepResult1.getIdentifier(), stepResult1);

        AudioStep audioStep = new AudioStep("audiostep");
        StepResult<AudioResult> stepResult2 = new StepResult<>(audioStep);
        AudioResult audio2 = new AudioResult("audio2", new File("a2.mp4"), "audio/mpeg");
        stepResult2.setResult(audio2);
        taskResult.setStepResultForStepIdentifier(stepResult2.getIdentifier(), stepResult2);

        return taskResult;
    }

    public static TaskResult timedWalkTaskResult() {
        // This test is based on the results of the timed walk task
        TaskResult taskResult = new TaskResult("timedwalktaskresultid");

        {
            BooleanAnswerFormat answerFormat = new BooleanAnswerFormat("yes", "no");
            QuestionStep questionStep = new QuestionStep(TimedWalkFormAFOStepIdentifier, null, answerFormat);
            StepResult<Boolean> stepResult = new StepResult<>(questionStep);
            stepResult.setResult(true);
            taskResult.setStepResultForStepIdentifier(stepResult.getIdentifier(), stepResult);
        }

        {
            ChoiceAnswerFormat answerFormat = new ChoiceAnswerFormat(
                    AnswerFormat.ChoiceAnswerStyle.SingleChoice,
                    new Choice<>("None", "None"), new Choice<>("Unilateral Cane", "Unilateral Cane"));
            QuestionStep questionStep = new QuestionStep(TimedWalkFormAssistanceStepIdentifier, null, answerFormat);
            StepResult<String> stepResult = new StepResult<>(questionStep);
            stepResult.setResult("Unilateral Cane");
            taskResult.setStepResultForStepIdentifier(stepResult.getIdentifier(), stepResult);
        }

        String[] timedWalkIds = new String[]{
                TimedWalkTrial1StepIdentifier, TimedWalkTurnAroundStepIdentifier,
                TimedWalkTrial2StepIdentifier};

        String[] recorderIds = new String[]{
                PedometerRecorderIdentifier, AccelerometerRecorderIdentifier,
                DeviceMotionRecorderIdentifier, LocationRecorderIdentifier};

        for (String timedWalkId : timedWalkIds) {
            double distanceInMeters = 30.0;
            int duration = 10;

            TimedWalkStep step = new TimedWalkStep(timedWalkId, null, null, distanceInMeters);
            StepResult<Result> stepResult = new StepResult<>(step);
            for (String recorderId : recorderIds) {
                FileResult result = new FileResult(
                        recorderId,
                        new File(recorderId + File.separator + UUID.randomUUID().toString()),
                        JSON_CONTENT_TYPE);
                result.setContentType(JSON_CONTENT_TYPE);
                result.setStartDate(new Date(System.currentTimeMillis() - 100));
                result.setEndDate(new Date(System.currentTimeMillis()));
                stepResult.getResults().put(result.getIdentifier(), result);
            }

            TimedWalkResult result = new TimedWalkResult(step.getIdentifier());
            result.setTimeLimit(duration);
            result.setDuration(duration);
            result.setDistanceInMeters(distanceInMeters);
            result.setStartDate(new Date(System.currentTimeMillis() - 100));
            result.setEndDate(new Date(System.currentTimeMillis()));
            stepResult.getResults().put(result.getIdentifier(), result);

            taskResult.setStepResultForStepIdentifier(stepResult.getIdentifier(), stepResult);
        }

        return taskResult;
    }
}
