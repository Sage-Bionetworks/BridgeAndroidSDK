package org.sagebionetworks.bridge.researchstack;

import android.content.Context;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.researchstack.backbone.ResourcePathManager;
import org.researchstack.backbone.result.TaskResult;
import org.researchstack.backbone.storage.file.EncryptionProvider;
import org.researchstack.backbone.storage.file.FileAccess;
import org.researchstack.backbone.storage.file.PinCodeConfig;
import org.researchstack.backbone.task.Task;
import org.researchstack.skin.AppPrefs;
import org.researchstack.skin.DataProvider;
import org.researchstack.skin.DataResponse;
import org.researchstack.skin.model.SchedulesAndTasksModel;
import org.researchstack.skin.model.User;
import org.sagebionetworks.bridge.researchstack.wrapper.StorageAccessWrapper;
import org.sagebionetworks.bridge.sdk.restmm.UserSessionInfo;
import org.sagebionetworks.bridge.sdk.restmm.model.BridgeMessageResponse;
import org.sagebionetworks.bridge.sdk.restmm.model.SignInBody;
import org.sagebionetworks.bridge.sdk.restmm.model.SignUpBody;

import retrofit2.Response;
import rx.Observable;
import rx.observers.TestSubscriber;

import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Created by liujoshua on 9/12/16.
 */
public class BridgeDataProviderTest {

  private ResourcePathManager.Resource publicKeyRes;
  private ResourcePathManager.Resource tasksAndSchedulesRes;
  private AppPrefs appPrefs;
  private DataProvider dataProvider;
  private BridgeService bridgeService;
  private StorageAccessWrapper storageAccess;
  private Context context;
  private PinCodeConfig pinCodeConfig;
  private EncryptionProvider encryptionProvider;
  private FileAccess fileAccess;
  private BridgeEncryptedDatabase appDatabase;
  private ConsentLocalStorage consentLocalStorage;
  private UserLocalStorage userLocalStorage;

  @Before
  public void beforeTest() {
    publicKeyRes = mock(ResourcePathManager.Resource.class);
    tasksAndSchedulesRes = mock(ResourcePathManager.Resource.class);
    bridgeService = mock(BridgeService.class);
    appPrefs = mock(AppPrefs.class);

    pinCodeConfig = mock(PinCodeConfig.class);
    encryptionProvider = mock(EncryptionProvider.class);
    fileAccess = mock(FileAccess.class);
    appDatabase = mock(BridgeEncryptedDatabase.class);

    storageAccess = mock(StorageAccessWrapper.class);
    when(storageAccess.getPinCodeConfig()).thenReturn(pinCodeConfig);
    when(storageAccess.getAppDatabase()).thenReturn(appDatabase);
    when(storageAccess.getFileAccess()).thenReturn(fileAccess);

    consentLocalStorage = mock(ConsentLocalStorage.class);
    userLocalStorage = mock(UserLocalStorage.class);

    dataProvider =
        new TestBridgeDataProvider(publicKeyRes, tasksAndSchedulesRes, bridgeService, appPrefs,
            storageAccess, userLocalStorage, consentLocalStorage);
    context = mock(Context.class);
  }

  @Ignore
  @Test
  public void testInitialize() {
    Observable<DataResponse> dataResponseObservable = dataProvider.initialize(context);

    TestSubscriber<DataResponse> testSubscriber = new TestSubscriber<>();
    dataResponseObservable.subscribe(testSubscriber);
    testSubscriber.awaitTerminalEvent();

    testSubscriber.assertNoErrors();
    testSubscriber.assertCompleted();
    testSubscriber.assertUnsubscribed();
    testSubscriber.assertValueCount(1);
  }

  @Test
  public void testSignUp() {
    when(bridgeService.signUp(isA(SignUpBody.class))).thenReturn(
        Observable.just(new BridgeMessageResponse()));

    Observable<DataResponse> dataResponseObservable =
        dataProvider.signUp(context, "email", "username", "password");

    TestSubscriber<DataResponse> testSubscriber = new TestSubscriber<>();
    dataResponseObservable.subscribe(testSubscriber);
    testSubscriber.awaitTerminalEvent();

    verify(bridgeService).signUp(isA(SignUpBody.class));
    verify(userLocalStorage).saveUser(isA(User.class));

    testSubscriber.assertNoErrors();
    testSubscriber.assertCompleted();
    testSubscriber.assertUnsubscribed();
    testSubscriber.assertValueCount(1);
  }

  @Test
  public void testSignIn() {
    UserSessionInfo session = new UserSessionInfo();

    Observable<Response<UserSessionInfo>> bridgeResponse =
        Observable.just(Response.success(session));
    when(bridgeService.signIn(isA(SignInBody.class))).thenReturn(bridgeResponse);
    //when(appDatabase.loadUploadRequests()).thenReturn(Lists.newArrayList());

    Observable<DataResponse> dataResponseObservable =
        dataProvider.signIn(context, "email", "password");
    TestSubscriber<DataResponse> testSubscriber = new TestSubscriber<>();
    dataResponseObservable.subscribe(testSubscriber);
    testSubscriber.awaitTerminalEvent();

    verify(bridgeService).signIn(isA(SignInBody.class));
    verify(userLocalStorage).saveUserSession(eq(session));
    verify(appDatabase).loadUploadRequests();

    testSubscriber.assertNoErrors();
    testSubscriber.assertCompleted();
    testSubscriber.assertUnsubscribed();
    testSubscriber.assertValueCount(1);
  }

  @Test
  public void testSignOut() {
    Observable<Response> bridgeResponse = Observable.just(Response.success(null));
    when(bridgeService.signOut()).thenReturn(bridgeResponse);

    Observable<DataResponse> dataResponseObservable = dataProvider.signOut(context);

    TestSubscriber<DataResponse> testSubscriber = new TestSubscriber<>();
    dataResponseObservable.subscribe(testSubscriber);
    testSubscriber.awaitTerminalEvent();

    verify(bridgeService).signOut();

    testSubscriber.assertNoErrors();
    testSubscriber.assertCompleted();
    testSubscriber.assertUnsubscribed();
    testSubscriber.assertValueCount(1);
  }

  @Ignore
  @Test
  public void testResendEmailVerification() {
    Observable<DataResponse> dataResponseObservable =
        dataProvider.resendEmailVerification(context, "email");
  }

  @Ignore
  @Test
  public void testIsSignedUp() {
    boolean isSignedUp = dataProvider.isSignedUp(context);
  }

  @Ignore
  @Test
  public void testIsSignedIn() {
    boolean isSignedIn = dataProvider.isSignedIn(context);
  }

  @Ignore
  @Test
  public void testIsConsented() {
    boolean isConsented = dataProvider.isConsented(context);
  }

  @Ignore
  @Test
  public void testWithdrawConsent() {
    String reasonString = "reason";
    Observable<DataResponse> dataResponseObservable =
        dataProvider.withdrawConsent(context, reasonString);
  }

  @Ignore
  @Test
  public void testUploadConsent() {
    TaskResult consentResult = mock(TaskResult.class);
    dataProvider.uploadConsent(context, consentResult);
  }

  @Ignore
  @Test
  public void testSaveConsent() {
    TaskResult consentResult = mock(TaskResult.class);
    dataProvider.saveConsent(context, consentResult);
  }

  @Ignore
  @Test
  public void testGetUser() {
    User user = dataProvider.getUser(context);
  }

  @Ignore
  @Test
  public void testGetUserSharingScope() {
    String scope = dataProvider.getUserSharingScope(context);
  }

  @Ignore
  @Test
  public void testGetUserEmail() {
    String email = dataProvider.getUserEmail(context);
  }

  @Ignore
  @Test
  public void testUploadTaskResult() {
    TaskResult taskResult = mock(TaskResult.class);
    dataProvider.uploadTaskResult(context, taskResult);
  }

  @Ignore
  @Test
  public void testLoadTasksAndSchedules() {
    SchedulesAndTasksModel schedulesAndTasksModel = dataProvider.loadTasksAndSchedules(context);
  }

  @Ignore
  @Test
  public void testLoadTask() {
    SchedulesAndTasksModel.TaskScheduleModel taskScheduleModel =
        mock(SchedulesAndTasksModel.TaskScheduleModel.class);
    Task task = dataProvider.loadTask(context, taskScheduleModel);
  }

  @Ignore
  @Test
  public void testProcessInitialTaskResult() {
    TaskResult taskResult = mock(TaskResult.class);
    dataProvider.processInitialTaskResult(context, taskResult);
  }

  @Ignore
  @Test
  public void testForgotPassword() {
    Observable<DataResponse> dataResponseObservable = dataProvider.forgotPassword(context, "email");
  }
}