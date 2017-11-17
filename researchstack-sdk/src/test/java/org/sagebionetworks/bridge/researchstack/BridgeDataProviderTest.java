package org.sagebionetworks.bridge.researchstack;

import android.content.Context;
import android.os.Looper;
import android.preference.PreferenceManager;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.researchstack.backbone.DataProvider;
import org.researchstack.backbone.DataResponse;
import org.researchstack.backbone.model.ConsentSignatureBody;
import org.researchstack.backbone.model.SchedulesAndTasksModel;
import org.researchstack.backbone.model.User;
import org.researchstack.backbone.result.TaskResult;
import org.researchstack.backbone.storage.file.FileAccess;
import org.researchstack.backbone.storage.file.PinCodeConfig;
import org.researchstack.backbone.task.OrderedTask;
import org.researchstack.backbone.task.Task;
import org.researchstack.backbone.ui.ActiveTaskActivity;
import org.researchstack.skin.AppPrefs;
import org.sagebionetworks.bridge.android.BridgeConfig;
import org.sagebionetworks.bridge.android.manager.ActivityManager;
import org.sagebionetworks.bridge.android.manager.AuthenticationManager;
import org.sagebionetworks.bridge.android.manager.BridgeManagerProvider;
import org.sagebionetworks.bridge.android.manager.dao.AccountDAO;
import org.sagebionetworks.bridge.android.manager.dao.ConsentDAO;
import org.sagebionetworks.bridge.android.manager.upload.SchemaKey;
import org.sagebionetworks.bridge.researchstack.survey.SurveyTaskScheduleModel;
import org.sagebionetworks.bridge.researchstack.wrapper.StorageAccessWrapper;
import org.sagebionetworks.bridge.rest.ApiClientProvider;
import org.sagebionetworks.bridge.rest.api.AuthenticationApi;
import org.sagebionetworks.bridge.rest.api.ForConsentedUsersApi;
import org.sagebionetworks.bridge.rest.model.Activity;
import org.sagebionetworks.bridge.rest.model.ActivityType;
import org.sagebionetworks.bridge.rest.model.ConsentSignature;
import org.sagebionetworks.bridge.rest.model.Message;
import org.sagebionetworks.bridge.rest.model.ScheduledActivity;
import org.sagebionetworks.bridge.rest.model.ScheduledActivityList;
import org.sagebionetworks.bridge.rest.model.SharingScope;
import org.sagebionetworks.bridge.rest.model.SignIn;
import org.sagebionetworks.bridge.rest.model.SurveyReference;
import org.sagebionetworks.bridge.rest.model.TaskReference;
import org.sagebionetworks.bridge.rest.model.UserSessionInfo;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import rx.Completable;
import rx.Observable;
import rx.Single;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Created by liujoshua on 9/12/16.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({PreferenceManager.class, Looper.class})
public class BridgeDataProviderTest {
    private static final String EXTERNAL_ID = "dummy-external-id";
    private static final String SCHEMA_ID = "my-schema-id";
    private static final int SCHEMA_REV = 3;
    private static final SchemaKey SCHEMA_KEY = new SchemaKey(SCHEMA_ID, SCHEMA_REV);
    private static final String TASK_ID = "my-task-id";

    private DataProvider dataProvider;
    @Mock
    private ApiClientProvider apiClientProvider;
    @Mock
    private ForConsentedUsersApi forConsentedUsersApi;
    @Mock
    private AuthenticationApi authenticationApi;
    private StorageAccessWrapper storageAccess;
    private PinCodeConfig pinCodeConfig;
    private FileAccess fileAccess;
    private BridgeEncryptedDatabase appDatabase;
    @Mock
    private TaskHelper taskHelper;

    @Mock
    protected BridgeManagerProvider bridgeManagerProvider;
    @Mock
    protected Context context;
    @Mock
    protected BridgeConfig bridgeConfig;
    @Mock
    protected AccountDAO accountDAO;
    @Mock
    protected ConsentDAO consentDAO;
    @Mock
    protected ResearchStackDAO researchStackDAO;
    @Mock
    private ActivityManager activityManager;
    @Mock
    protected AuthenticationManager authenticationManager;

    @Before
    public void setupTest() {
        MockitoAnnotations.initMocks(this);

        BridgeManagerProvider.init(bridgeManagerProvider);

        when(bridgeManagerProvider.getApplicationContext()).thenReturn(context);
        when(bridgeManagerProvider.getBridgeConfig()).thenReturn(bridgeConfig);
        when(bridgeManagerProvider.getAccountDao()).thenReturn(accountDAO);
        when(bridgeManagerProvider.getConsentDao()).thenReturn(consentDAO);
        when(bridgeManagerProvider.getActivityManager()).thenReturn(activityManager);
        when(bridgeManagerProvider.getAuthenticationManager()).thenReturn(authenticationManager);

        pinCodeConfig = mock(PinCodeConfig.class);
        fileAccess = mock(FileAccess.class);
        appDatabase = mock(BridgeEncryptedDatabase.class);

        storageAccess = mock(StorageAccessWrapper.class);
        when(storageAccess.getPinCodeConfig()).thenReturn(pinCodeConfig);
        when(storageAccess.getAppDatabase()).thenReturn(appDatabase);
        when(storageAccess.getFileAccess()).thenReturn(fileAccess);

        when(apiClientProvider.getClient(AuthenticationApi.class)).thenReturn(authenticationApi);
        when(apiClientProvider
                .getClient(same(ForConsentedUsersApi.class), any(SignIn.class)))
                .thenReturn(forConsentedUsersApi);

        dataProvider =
                new BridgeDataProvider(researchStackDAO, storageAccess, taskHelper) {

                    @Override
                    public void processInitialTaskResult(Context context, TaskResult taskResult) {
                        throw new UnsupportedOperationException();
                    }
                };

        PowerMockito.mockStatic(PreferenceManager.class);
        PowerMockito.mockStatic(Looper.class);

        AppPrefs.init(context);
    }

    @Test
    public void testInitialize() {
        dataProvider.initialize(context).test().assertCompleted();
    }

    @Test
    public void testSignUp() throws IOException {
        when(authenticationManager.signUp("email", "password"))
                .thenReturn(Completable.complete());

        dataProvider.signUp(context, "email", "name", "password").test()
                .assertCompleted();

        verify(authenticationManager).signUp("email", "password");
    }

    @Test
    public void testSignIn() throws IOException {
        UserSessionInfo session = mock(UserSessionInfo.class);

        when(authenticationManager.signIn("email", "password")).thenReturn(Single.just(session));
        Observable<DataResponse> completable = dataProvider.signIn(context, "email", "password");
        completable.test().assertCompleted();

        verify(authenticationManager).signIn("email", "password");

        // TODO: verify background tasks are triggered when session is established
        // verify(uploadHandler).uploadPendingFiles(forConsentedUsersApi);
    }

    @Test
    public void signInWithExternalId() {
        // Mock BridgeConfig
        when(bridgeConfig.getEmailForExternalId(EXTERNAL_ID)).thenReturn(
                "example+extId@example.com");
        when(bridgeConfig.getPasswordForExternalId(EXTERNAL_ID)).thenReturn(
                "extId's dummy password");

        // Mock Authentication Manager
        when(authenticationManager.signIn(any(), any())).thenReturn(Single.just(
                new UserSessionInfo()));

        // Execute and validate
        Observable<DataResponse> loginResult = dataProvider.signInWithExternalId(context,
                EXTERNAL_ID);
        loginResult.test().assertCompleted();

        // Verify dependencies
        verify(bridgeConfig).getEmailForExternalId(EXTERNAL_ID);
        verify(bridgeConfig).getPasswordForExternalId(EXTERNAL_ID);
        verify(authenticationManager).signIn("example+extId@example.com",
                "extId's dummy password");
    }

    @Test
    public void testSignOut() throws IOException {

        when(authenticationManager.signOut()).thenReturn(Completable.complete());

        Observable<DataResponse> responseObservable = dataProvider.signOut(context);

        responseObservable
                .doOnNext(dataResponse -> assertTrue(dataResponse.isSuccess()))
                .test().assertCompleted();

        verify(authenticationManager).signOut();
    }


    @Test
    public void testResendEmailVerification() throws IOException {
        when(authenticationManager.resendEmailVerification("email"))
                .thenReturn(Completable.complete());

        dataProvider.resendEmailVerification(context, "email").test()
                .assertCompleted()
                .assertValueCount(1);

        verify(authenticationManager).resendEmailVerification("email");
    }

    @Test
    public void testIsSignedUp() {
        when(authenticationManager.getEmail()).thenReturn("Email");

        boolean isSignedUp = dataProvider.isSignedUp(context);
        assertTrue(isSignedUp);

        verify(authenticationManager).getEmail();
    }


    @Test
    public void testIsConsented() {
        when(authenticationManager.isConsented()).thenReturn(true);

        boolean isConsented = dataProvider.isConsented();

        assertTrue(isConsented);
        verify(authenticationManager).isConsented();
    }

    @Test
    public void testWithdrawConsent() {
        String reasonString = "reason";
        when(authenticationManager.withdrawAll(reasonString)).thenReturn(Completable.complete());

        dataProvider.withdrawConsent(context, reasonString).test().assertCompleted();

        verify(authenticationManager).withdrawAll(reasonString);
    }

    @Test
    public void saveLocalConsent() throws Exception {
        ConsentSignatureBody body = new ConsentSignatureBody();
        body.birthdate = DateTime.now().toDate();
        body.scope = SharingScope.ALL_QUALIFIED_RESEARCHERS.toString();
        body.imageData = "some image Data";
        body.imageMimeType = "image mime type";
        body.name = "name";

        when(bridgeConfig.getStudyId()).thenReturn("studyId");

        dataProvider.saveLocalConsent(context, body);

        verify(bridgeConfig).getStudyId();
        verify(authenticationManager).storeLocalConsent(
                "studyId",
                body.name,
                LocalDate.fromDateFields(body.birthdate),
                body.imageData,
                body.imageMimeType,
                SharingScope.ALL_QUALIFIED_RESEARCHERS
        );
    }

    @Test
    public void saveLocalConsent_scopeOnly() throws Exception {
        ConsentSignatureBody body = new ConsentSignatureBody();
        body.scope = SharingScope.ALL_QUALIFIED_RESEARCHERS.toString();
        // no exception when saving only the scope
        dataProvider.saveLocalConsent(context, body);
    }

    @Test
    public void toSharingScope() {
        assertSharingScope("no_sharing", SharingScope.NO_SHARING);
        assertSharingScope("all_qualified_researchers", SharingScope.ALL_QUALIFIED_RESEARCHERS);
        assertSharingScope("sponsors_and_partners", SharingScope.SPONSORS_AND_PARTNERS);
    }

    private void assertSharingScope(String jsonKey, SharingScope expectedScope) {
        SharingScope scopeResult = ((BridgeDataProvider) dataProvider).toSharingScope(jsonKey);
        assertSame(expectedScope, scopeResult);
    }

    @Test
    public void createConsentSignature_createConsentSignatureBody() {
        ConsentSignature consentSignature = new ConsentSignature()
                .name("name")
                .birthdate(LocalDate.now())
                .imageData("image")
                .imageMimeType("image mime type")
                .scope(SharingScope.SPONSORS_AND_PARTNERS);

        BridgeDataProvider bridgeDataProvider = ((BridgeDataProvider) dataProvider);

        // test functions that are inverses of each other
        ConsentSignatureBody csb = bridgeDataProvider.createConsentSignatureBody(consentSignature);
        ConsentSignature resultConsentSignature = bridgeDataProvider.createConsentSignature(csb);

        assertEquals(consentSignature, resultConsentSignature);
    }

    @Test
    public void createConsentSignature_requiredFieldsOnly() {
        ((BridgeDataProvider) dataProvider).createConsentSignature(new ConsentSignatureBody());
    }

    @Test
    public void createConsentSignatureBody_requiredFieldsOnly() {
        ((BridgeDataProvider) dataProvider).createConsentSignatureBody(new ConsentSignature());
    }

    @Test
    public void testGetUser() {
        User user = mock(User.class);
        when(researchStackDAO.getUser()).thenReturn(user);

        User userResult = dataProvider.getUser(context);

        assertEquals(user, userResult);
        verify(researchStackDAO).getUser();
    }

    @Test
    public void testGetUserSharingScope() {
        UserSessionInfo session = mock(UserSessionInfo.class);
        String scope = "SPONSORS_AND_PARTNERS";
        when(session.getSharingScope()).thenReturn(SharingScope.valueOf(scope));
        when(authenticationManager.getUserSessionInfo()).thenReturn(session);

        String scopeResult = dataProvider.getUserSharingScope(context);

        assertEquals(SharingScope.SPONSORS_AND_PARTNERS.toString(), scopeResult);
        verify(authenticationManager).getUserSessionInfo();
        verify(session, atLeastOnce()).getSharingScope();
    }

    @Test
    public void testGetUserEmail() {
        String email = "email@example.com";
        when(authenticationManager.getEmail()).thenReturn(email);

        String emailResult = dataProvider.getUserEmail(context);

        assertEquals(email, emailResult);

        verify(authenticationManager).getEmail();
    }

    @Test
    public void testUploadTaskResult_ActivityWithSchemaMapping() {
        // mock bridge config
        when(bridgeConfig.getTaskToSchemaMap()).thenReturn(ImmutableMap.of(TASK_ID, SCHEMA_KEY));

        // set up and execute
        TaskResult taskResult = makeActivityTask("my-task-id");
        dataProvider.uploadTaskResult(context, taskResult);

        // verify
        verify(taskHelper).uploadActivityResult(SCHEMA_ID, SCHEMA_REV, taskResult);
    }

    @Test
    public void testUploadTaskResult_ActivityWithNoSchemaMapping() {
        // mock bridge config
        when(bridgeConfig.getTaskToSchemaMap()).thenReturn(ImmutableMap.of());

        // set up and execute
        TaskResult taskResult = makeActivityTask("my-task-id");
        dataProvider.uploadTaskResult(context, taskResult);

        // verify
        verify(taskHelper).uploadActivityResult(TASK_ID, taskResult);
    }

    @Test
    public void testUploadTaskResult_Survey() {
        // set up and execute
        TaskResult taskResult = new TaskResult(TASK_ID);
        dataProvider.uploadTaskResult(context, taskResult);

        // verify
        verify(taskHelper).uploadSurveyResult(taskResult);
    }

    @Test
    public void testLoadTasksAndSchedules() {
        // There are two parts to this test:
        // 1. We can handle both surveys and tasks.
        // 2. We group and sort date correctly.
        //
        // With that in mind, we'll want 4 activites, 2 on the 1st and 2 on the 2nd, arranged in
        // unsorted order. Each day will have a survey and a task.
        DateTime day1 = DateTime.parse("2017-11-01T07:00-0700");
        DateTime day2 = DateTime.parse("2017-11-03T07:00-0700");

        DateTime surveyCreatedOn1 = DateTime.parse("2017-02-21T19:46:33.515Z");
        SurveyReference surveyRef1 = new SurveyReference().identifier("survey-1")
                .guid("survey-1-guid").createdOn(surveyCreatedOn1);
        Activity surveyActivity1 = new Activity().label("Survey 1").labelDetail("1 question")
                .activityType(ActivityType.SURVEY).survey(surveyRef1).guid("survey-1-guid-test");
        ScheduledActivity surveyScheduledActivity1 = makeScheduledActivity(day1, surveyActivity1,
                false);

        DateTime surveyCreatedOn2 = DateTime.parse("2016-12-09T19:23:57.424Z");
        SurveyReference surveyRef2 = new SurveyReference().identifier("survey-2")
                .guid("survey-2-guid").createdOn(surveyCreatedOn2);
        Activity surveyActivity2 = new Activity().label("Survey 2").labelDetail("2 questions")
                .activityType(ActivityType.SURVEY).survey(surveyRef2);
        ScheduledActivity surveyScheduledActivity2 = makeScheduledActivity(day2, surveyActivity2,
                true);

        TaskReference taskRef1 = new TaskReference().identifier("task-1");
        Activity taskActivity1 = new Activity().label("Task 1").labelDetail("1 minute")
                .activityType(ActivityType.TASK).task(taskRef1);
        ScheduledActivity taskScheduledActivity1 = makeScheduledActivity(day1, taskActivity1,
                false);

        TaskReference taskRef2 = new TaskReference().identifier("task-2");
        Activity taskActivity2 = new Activity().label("Task 2").labelDetail("2 minutes")
                .activityType(ActivityType.TASK).task(taskRef2);
        ScheduledActivity taskScheduledActivity2 = makeScheduledActivity(day2, taskActivity2,
                true);

        ScheduledActivityList scheduledActivityList = new ScheduledActivityList()
                .items(ImmutableList.of(surveyScheduledActivity2, surveyScheduledActivity1,
                        taskScheduledActivity2, taskScheduledActivity1));

        // Mock Bridge call
        when(activityManager.getActivities(anyInt(), anyInt())).thenReturn(Single.just(
                scheduledActivityList));

        // Execute and validate
        SchedulesAndTasksModel schedulesAndTasksModel = dataProvider.loadTasksAndSchedules(context)
                .toBlocking().value();
        List<SchedulesAndTasksModel.ScheduleModel> scheduleModelList = schedulesAndTasksModel
                .schedules;
        assertEquals(2, scheduleModelList.size());

        // Day 1
        SchedulesAndTasksModel.ScheduleModel scheduleDay1 = scheduleModelList.get(0);
        assertEquals("once", scheduleDay1.scheduleType);
        assertEquals(day1.toDate(), scheduleDay1.scheduledOn);
        List<SchedulesAndTasksModel.TaskScheduleModel> taskModelListDay1 = scheduleDay1.tasks;
        assertEquals(2, taskModelListDay1.size());

        SurveyTaskScheduleModel surveyScheduleModel1 = (SurveyTaskScheduleModel) taskModelListDay1
                .get(0);
        assertEquals("survey-1-guid", surveyScheduleModel1.surveyGuid);
        assertEquals("survey-1-guid-test", surveyScheduleModel1.taskGUID);
        assertEquals(surveyCreatedOn1, surveyScheduleModel1.surveyCreatedOn);
        assertEquals("Survey 1", surveyScheduleModel1.taskTitle);
        assertFalse(surveyScheduleModel1.taskIsOptional);
        assertEquals(ActivityType.SURVEY.toString(), surveyScheduleModel1.taskType);
        assertEquals("1 question", surveyScheduleModel1.taskCompletionTime);

        SchedulesAndTasksModel.TaskScheduleModel taskScheduleModel1 = taskModelListDay1.get(1);
        assertEquals("Task 1", taskScheduleModel1.taskTitle);
        assertEquals("task-1", taskScheduleModel1.taskID);
        assertFalse(taskScheduleModel1.taskIsOptional);
        assertEquals(ActivityType.TASK.toString(), taskScheduleModel1.taskType);
        assertEquals("1 minute", taskScheduleModel1.taskCompletionTime);

        // Day 2
        SchedulesAndTasksModel.ScheduleModel scheduleDay2 = scheduleModelList.get(1);
        assertEquals("once", scheduleDay2.scheduleType);
        assertEquals(day2.toDate(), scheduleDay2.scheduledOn);
        List<SchedulesAndTasksModel.TaskScheduleModel> taskModelListDay2 = scheduleDay2.tasks;
        assertEquals(2, taskModelListDay2.size());

        SurveyTaskScheduleModel surveyScheduleModel2 = (SurveyTaskScheduleModel) taskModelListDay2
                .get(0);
        assertEquals("survey-2-guid", surveyScheduleModel2.surveyGuid);
        assertEquals(surveyCreatedOn2, surveyScheduleModel2.surveyCreatedOn);
        assertEquals("Survey 2", surveyScheduleModel2.taskTitle);
        assertTrue(surveyScheduleModel2.taskIsOptional);
        assertEquals(ActivityType.SURVEY.toString(), surveyScheduleModel2.taskType);
        assertEquals("2 questions", surveyScheduleModel2.taskCompletionTime);

        SchedulesAndTasksModel.TaskScheduleModel taskScheduleModel2 = taskModelListDay2.get(1);
        assertEquals("Task 2", taskScheduleModel2.taskTitle);
        assertEquals("task-2", taskScheduleModel2.taskID);
        assertTrue(taskScheduleModel2.taskIsOptional);
        assertEquals(ActivityType.TASK.toString(), taskScheduleModel2.taskType);
        assertEquals("2 minutes", taskScheduleModel2.taskCompletionTime);

        // Verify back-end call
        verify(activityManager).getActivities(4, 0);
    }

    // Helper method to make a ScheduledActivity. This is because there are no setters for
    // scheduledOn and persistent.
    private static ScheduledActivity makeScheduledActivity(
            DateTime scheduledOn, Activity activity, boolean persistent) {
        ScheduledActivity scheduledActivity = mock(ScheduledActivity.class);
        when(scheduledActivity.getScheduledOn()).thenReturn(scheduledOn);
        when(scheduledActivity.getActivity()).thenReturn(activity);
        when(scheduledActivity.getPersistent()).thenReturn(persistent);
        when(scheduledActivity.getGuid()).thenReturn(activity.getGuid());
        return scheduledActivity;
    }

    @Test
    public void testLoadTask() {
        // Mock TaskHelper
        Single<Task> taskHelperOutput = Single.just(mock(Task.class));
        when(taskHelper.loadTask(any(), any())).thenReturn(taskHelperOutput);

        // Execute and validate
        SchedulesAndTasksModel.TaskScheduleModel dataProviderInput =
                new SchedulesAndTasksModel.TaskScheduleModel();
        Single<Task> dataProviderOutput = dataProvider.loadTask(context, dataProviderInput);
        assertSame(taskHelperOutput, dataProviderOutput);

        // Verify back-end call
        verify(taskHelper).loadTask(same(context), same(dataProviderInput));
    }

    @Ignore
    @Test
    public void testProcessInitialTaskResult() {
        TaskResult taskResult = mock(TaskResult.class);
        dataProvider.processInitialTaskResult(context, taskResult);
    }

    @Test
    public void testForgotPassword() throws IOException {
        when(authenticationManager.requestPasswordReset("email")).
                thenReturn(Completable.complete());

        dataProvider.forgotPassword(context, "email").test().assertCompleted();

        verify(authenticationManager).requestPasswordReset("email");
    }

    @Test
    public void testUpdateActivityOnUpload() throws IOException {
        // mock bridge config
        when(bridgeConfig.getTaskToSchemaMap()).thenReturn(ImmutableMap.of(TASK_ID, SCHEMA_KEY));
        // Mock Bridge update activity call
        when(activityManager.updateActivity(any())).thenReturn(Observable.just(new Message()));
        when(taskHelper.loadTask(any(), any())).thenReturn(Single.just(new OrderedTask(TASK_ID)));

        // set up and execute
        SchedulesAndTasksModel.TaskScheduleModel task = new SchedulesAndTasksModel.TaskScheduleModel();
        task.taskID = TASK_ID;
        task.taskGUID = TASK_ID;
        // Needs to be called for the activity to upload
        dataProvider.loadTask(context, task);

        Date taskFinished = new Date();
        TaskResult taskResult = makeActivityTask("my-task-id");
        taskResult.setStartDate(taskFinished);
        taskResult.setEndDate(taskFinished);
        dataProvider.uploadTaskResult(context, taskResult);

        // verify
        verify(taskHelper).uploadActivityResult(SCHEMA_ID, SCHEMA_REV, taskResult);

        // TODO: make GUID available in ScheduledActivity constructor and get rid of this gson nonsense
        String activityJson = String.format("{\"guid\":\"%s\"}", TASK_ID);
        ScheduledActivity activity = (new Gson()).fromJson(activityJson, ScheduledActivity.class);
        activity.setStartedOn(new DateTime(taskFinished));
        activity.setFinishedOn(new DateTime(taskFinished));
        verify(activityManager).updateActivity(eq(activity));
    }

    private static TaskResult makeActivityTask(String taskId) {
        TaskResult taskResult = new TaskResult(taskId);
        taskResult.getTaskDetails().put(ActiveTaskActivity.ACTIVITY_TASK_RESULT_KEY, true);
        return taskResult;
    }
}