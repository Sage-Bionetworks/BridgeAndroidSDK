package org.sagebionetworks.bridge.researchstack;

import android.content.Context;
import android.content.Intent;

import org.apache.commons.lang3.time.DateUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.researchstack.backbone.ResourceManager;
import org.researchstack.backbone.answerformat.AnswerFormat;
import org.researchstack.backbone.answerformat.BooleanAnswerFormat;
import org.researchstack.backbone.answerformat.ChoiceAnswerFormat;
import org.researchstack.backbone.model.Choice;
import org.researchstack.backbone.result.AudioResult;
import org.researchstack.backbone.result.FileResult;
import org.researchstack.backbone.result.Result;
import org.researchstack.backbone.result.StepResult;
import org.researchstack.backbone.result.TappingIntervalResult;
import org.researchstack.backbone.result.TaskResult;
import org.researchstack.backbone.result.TimedWalkResult;
import org.researchstack.backbone.step.QuestionStep;
import org.researchstack.backbone.step.active.AudioStep;
import org.researchstack.backbone.step.active.CountdownStep;
import org.researchstack.backbone.step.active.TappingIntervalStep;
import org.researchstack.backbone.step.active.TimedWalkStep;
import org.researchstack.backbone.storage.NotificationHelper;
import org.researchstack.backbone.storage.database.TaskNotification;
import org.researchstack.backbone.task.factory.TappingTaskFactory;
import org.researchstack.skin.AppPrefs;
import org.researchstack.skin.notification.TaskAlertReceiver;
import org.sagebionetworks.bridge.android.data.Archive;
import org.sagebionetworks.bridge.android.manager.BridgeManagerProvider;
import org.sagebionetworks.bridge.android.manager.UploadManager;
import org.sagebionetworks.bridge.researchstack.wrapper.StorageAccessWrapper;
import org.sagebionetworks.bridge.rest.model.UploadValidationStatus;
import org.spongycastle.cms.CMSException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import rx.Completable;
import rx.Single;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.researchstack.backbone.result.TappingIntervalResult.TappingButtonIdentifier.TappedButtonLeft;
import static org.researchstack.backbone.result.TappingIntervalResult.TappingButtonIdentifier.TappedButtonNone;
import static org.researchstack.backbone.task.factory.TaskFactory.Constants.AccelerometerRecorderIdentifier;
import static org.researchstack.backbone.task.factory.TaskFactory.Constants.ActiveTaskLeftHandIdentifier;
import static org.researchstack.backbone.task.factory.TaskFactory.Constants.ActiveTaskRightHandIdentifier;
import static org.researchstack.backbone.task.factory.TaskFactory.Constants.DeviceMotionRecorderIdentifier;
import static org.researchstack.backbone.task.factory.TaskFactory.Constants.LocationRecorderIdentifier;
import static org.researchstack.backbone.task.factory.TaskFactory.Constants.PedometerRecorderIdentifier;
import static org.researchstack.backbone.task.factory.TaskFactory.Constants.TappingStepIdentifier;
import static org.researchstack.backbone.task.factory.WalkingTaskFactory.TimedWalkFormAFOStepIdentifier;
import static org.researchstack.backbone.task.factory.WalkingTaskFactory.TimedWalkFormAssistanceStepIdentifier;
import static org.researchstack.backbone.task.factory.WalkingTaskFactory.TimedWalkTrial1StepIdentifier;
import static org.researchstack.backbone.task.factory.WalkingTaskFactory.TimedWalkTrial2StepIdentifier;
import static org.researchstack.backbone.task.factory.WalkingTaskFactory.TimedWalkTurnAroundStepIdentifier;

/**
 * Created by TheMDP on 3/3/17.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(TaskAlertReceiver.class)
public class TaskHelperTest {

    private static final String JSON_CONTENT_TYPE = "application/json";

    private TaskHelper taskHelper;

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
    UploadManager uploadManager;
    @Mock
    BridgeManagerProvider bridgeManagerProvider;

    @Before
    public void setupTest() {
        MockitoAnnotations.initMocks(this);

        when(bridgeManagerProvider.getUploadManager()).thenReturn(uploadManager);
        when(bridgeManagerProvider.getApplicationContext()).thenReturn(applicationContext);

        taskHelper = new TaskHelper(storageAccess, resourceManager, appPrefs, notificationHelper,
                bridgeManagerProvider);
    }

    @Test
    public void testUploadTaskResult() throws IOException, CMSException {
        Date endDate = mock(Date.class);
        String taskId = "taskId";
        String taskChron = "chron";

        TaskResult taskResult = mock(TaskResult.class);
        when(taskResult.getIdentifier()).thenReturn(taskId);
        when(taskResult.getEndDate()).thenReturn(endDate);

        Archive archive = mock(Archive.class);
        Archive.Builder archiveBuilder = mock(Archive.Builder.class);
        when(archiveBuilder.build()).thenReturn(archive);

        Archive.WithBridgeConfig withBridgeConfig = mock(Archive.WithBridgeConfig.class);
        when(withBridgeConfig.withBridgeConfig(any())).thenReturn(archiveBuilder);

        when(appPrefs.isTaskReminderEnabled()).thenReturn(true);
        taskHelper.putTaskChron(taskId, taskChron);

        ArgumentCaptor<TaskNotification> taskNotificationCaptor = ArgumentCaptor
                .forClass(TaskNotification.class);

        when(uploadManager.upload(any(), eq(archive)))
                .thenReturn(Single.just(new UploadValidationStatus()));

        Intent notificationCreateIntent = mock(Intent.class);
        mockStatic(TaskAlertReceiver.class);
        when(TaskAlertReceiver.createCreateIntent(any())).thenReturn(notificationCreateIntent);

        taskHelper.uploadTaskResult(taskResult, withBridgeConfig);

        verify(uploadManager).upload(any(), eq(archive));

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
        assertEquals(resultList.size(), 4); // 12 for data recorder files, 5 for step results

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
                result.setStartDate(new Date(System.currentTimeMillis() - DateUtils.MILLIS_PER_SECOND));
                result.setEndDate(new Date(System.currentTimeMillis()));
                stepResult.getResults().put(result.getIdentifier(), result);
            }

            {
                TappingIntervalResult result = new TappingIntervalResult(step.getIdentifier());
                result.setStepViewSize(new TappingIntervalResult.Size(200, 200));
                result.setButtonRect1(new TappingIntervalResult.Rect(40, 40, 80, 80));
                result.setButtonRect2(new TappingIntervalResult.Rect(120, 120, 160, 160));

                // Add all the samples of Mock taps
                int sampleCount = 20;
                int timePerSample = step.getStepDuration() / sampleCount;
                List<TappingIntervalResult.Sample> sampleList = new ArrayList<>(sampleCount);
                long timestamp = System.currentTimeMillis();
                for (int j = 0; j < sampleCount; j++) {
                    TappingIntervalResult.Sample sample = new TappingIntervalResult.Sample();
                    sample.setLocation(new TappingIntervalResult.Point(50, 50));
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
                result.setStartDate(new Date(System.currentTimeMillis() - DateUtils.MILLIS_PER_SECOND));
                result.setEndDate(new Date(System.currentTimeMillis()));
                stepResult.getResults().put(result.getIdentifier(), result);
            }

            TimedWalkResult result = new TimedWalkResult(step.getIdentifier());
            result.setTimeLimit(duration);
            result.setDuration(duration);
            result.setDistanceInMeters(distanceInMeters);
            result.setStartDate(new Date(System.currentTimeMillis() - DateUtils.MILLIS_PER_SECOND));
            result.setEndDate(new Date(System.currentTimeMillis()));
            stepResult.getResults().put(result.getIdentifier(), result);

            taskResult.setStepResultForStepIdentifier(stepResult.getIdentifier(), stepResult);
        }

        return taskResult;
    }
}
