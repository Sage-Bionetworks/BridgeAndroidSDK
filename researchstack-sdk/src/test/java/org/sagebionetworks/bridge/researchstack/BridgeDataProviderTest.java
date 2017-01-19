package org.sagebionetworks.bridge.researchstack;

import android.content.Context;
import android.support.annotation.NonNull;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.researchstack.backbone.ResourcePathManager;
import org.researchstack.backbone.result.TaskResult;
import org.researchstack.backbone.storage.file.EncryptionProvider;
import org.researchstack.backbone.storage.file.FileAccess;
import org.researchstack.backbone.storage.file.PinCodeConfig;
import org.researchstack.backbone.task.Task;
import org.researchstack.skin.AppPrefs;
import org.researchstack.backbone.DataProvider;
import org.researchstack.backbone.DataResponse;
import org.researchstack.backbone.model.SchedulesAndTasksModel;
import org.researchstack.backbone.model.User;
import org.sagebionetworks.bridge.researchstack.wrapper.StorageAccessWrapper;
import org.sagebionetworks.bridge.rest.ApiClientProvider;
import org.sagebionetworks.bridge.rest.api.AuthenticationApi;
import org.sagebionetworks.bridge.rest.api.ForConsentedUsersApi;
import org.sagebionetworks.bridge.rest.model.Email;
import org.sagebionetworks.bridge.rest.model.Message;
import org.sagebionetworks.bridge.rest.model.SignIn;
import org.sagebionetworks.bridge.rest.model.SignUp;
import org.sagebionetworks.bridge.sdk.restmm.UserSessionInfo;
import org.sagebionetworks.bridge.sdk.restmm.model.BridgeMessageResponse;
import org.sagebionetworks.bridge.sdk.restmm.model.SignInBody;
import org.sagebionetworks.bridge.sdk.restmm.model.SignUpBody;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Response;
import rx.Observable;
import rx.observers.TestSubscriber;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
  @Mock
  private ApiClientProvider apiClientProvider;
  @Mock
  private ForConsentedUsersApi forConsentedUsersApi;
  @Mock
  private AuthenticationApi authenticationApi;
  private BridgeService bridgeService;
  private StorageAccessWrapper storageAccess;
  private Context context;
  private PinCodeConfig pinCodeConfig;
  private EncryptionProvider encryptionProvider;
  private FileAccess fileAccess;
  private BridgeEncryptedDatabase appDatabase;
  private ConsentLocalStorage consentLocalStorage;
  private UserLocalStorage userLocalStorage;
  @Mock
  private TaskHelper taskHelper;
  @Mock
  private UploadHandler uploadHandler;

  @Before
  public void beforeTest() {
    MockitoAnnotations.initMocks(this);

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

    when(apiClientProvider.getClient(AuthenticationApi.class)).thenReturn(authenticationApi);

    dataProvider =
        new TestBridgeDataProvider(publicKeyRes, tasksAndSchedulesRes, apiClientProvider,
            bridgeService, appPrefs, storageAccess, userLocalStorage, consentLocalStorage,
            taskHelper, uploadHandler);
    context = mock(Context.class);
  }

  @Ignore
  @Test
  public void testInitialize() {
    Observable<DataResponse> dataResponseObservable = dataProvider.initialize(context);

    TestSubscriber<DataResponse> testSubscriber = getTestSubscriber(
        dataResponseObservable);

    assertTestSubscriberCompletion(testSubscriber);
  }

  private <T> Call<T> setupCall(T body) throws IOException {
    Call<T> call = mock(Call.class);
    when(call.clone()).thenReturn(call);
    when(call.execute()).thenReturn(Response.success(body));
    return call;
  }

  @Test
  public void testSignUp() throws IOException {
    when(bridgeService.signUp(isA(SignUpBody.class))).thenReturn(
        Observable.just(new BridgeMessageResponse()));

    Call<Message> call = setupCall(mock(Message.class));
    when(authenticationApi.signUp(isA(SignUp.class))).thenReturn(call);

    Observable<DataResponse> dataResponseObservable =
        dataProvider.signUp(context, "email", "username", "password");

    TestSubscriber<DataResponse> testSubscriber = getTestSubscriber(
        dataResponseObservable);

    verify(authenticationApi).signUp(isA(SignUp.class));
    verify(userLocalStorage).saveUser(isA(User.class));

    assertTestSubscriberCompletion(testSubscriber);
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
    TestSubscriber<DataResponse> testSubscriber = getTestSubscriber(
        dataResponseObservable);

    verify(bridgeService).signIn(isA(SignInBody.class));
    verify(userLocalStorage).saveUserSession(eq(session), isA(SignIn.class));
    verify(uploadHandler).uploadPendingFiles(bridgeService);

    assertTestSubscriberCompletion(testSubscriber);
  }

  @Test
  public void testSignOut() {
    Observable<Response> bridgeResponse = Observable.just(Response.success(null));
    when(bridgeService.signOut()).thenReturn(bridgeResponse);

    Observable<DataResponse> dataResponseObservable = dataProvider.signOut(context);

    TestSubscriber<DataResponse> testSubscriber = getTestSubscriber(
        dataResponseObservable);

    verify(bridgeService).signOut();
    verify(userLocalStorage).clearUserSession();
    verify(userLocalStorage).clearSignIn();
    verify(userLocalStorage, never()).clearUser();

    assertTestSubscriberCompletion(testSubscriber);
  }

  @Test
  public void testResendEmailVerification() throws IOException {
    Call<Message> call = setupCall(new Message());
    when(authenticationApi.resendEmailVerification(isA(Email.class))).thenReturn(call);

    Observable<DataResponse> dataResponseObservable =
        dataProvider.resendEmailVerification(context, "email");

    TestSubscriber<DataResponse> testSubscriber = getTestSubscriber(
        dataResponseObservable);

    verify(authenticationApi).resendEmailVerification(isA(Email.class));

    assertTestSubscriberCompletion(testSubscriber);
  }

  @Test
  public void testIsSignedUp() {
    when(userLocalStorage.isSignedUp()).thenReturn(true);

    boolean isSignedUp = dataProvider.isSignedUp(context);

    assertTrue(isSignedUp);
    verify(userLocalStorage).isSignedUp();
  }

  @Test
  public void testIsSignedIn() {
    when(userLocalStorage.isSignedIn()).thenReturn(true);
    boolean isSignedIn = dataProvider.isSignedIn(context);
    assertTrue(isSignedIn);
    verify(userLocalStorage).isSignedIn();
  }

  @Test
  public void testIsConsented() {
    UserSessionInfo userSessionInfo = mock(UserSessionInfo.class);
    when(userSessionInfo.isConsented()).thenReturn(false);
    when(userLocalStorage.loadUserSession()).thenReturn(userSessionInfo);
    when(consentLocalStorage.hasConsent()).thenReturn(true);

    boolean isConsented = dataProvider.isConsented();

    assertTrue(isConsented);
    verify(userSessionInfo).isConsented();
    verify(userLocalStorage).loadUserSession();
    verify(consentLocalStorage).hasConsent();
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

  @Test
  public void testGetUser() {
    User user = mock(User.class);
    when(userLocalStorage.loadUser()).thenReturn(user);

    User userResult = dataProvider.getUser(context);

    assertEquals(user, userResult);
    verify(userLocalStorage).loadUser();
  }

  @Test
  public void testGetUserSharingScope() {
    UserSessionInfo session = mock(UserSessionInfo.class);
    String scope = "sharing-scope";
    when(session.getSharingScope()).thenReturn(scope);
    when(userLocalStorage.loadUserSession()).thenReturn(session);

    String scopeResult = dataProvider.getUserSharingScope(context);

    assertEquals(scope, scopeResult);
    verify(session).getSharingScope();
    verify(userLocalStorage).loadUserSession();
  }

  @Test
  public void testGetUserEmail() {
    String email = "email@example.com";
    User user = mock(User.class);
    when(user.getEmail()).thenReturn(email);
    when(userLocalStorage.loadUser()).thenReturn(user);

    String emailResult = dataProvider.getUserEmail(context);

    assertEquals(email, emailResult);

    verify(user).getEmail();
    verify(userLocalStorage).loadUser();
  }

  @Test
  public void testGetUserEmail_Null() {
    String email = null;
    User user = mock(User.class);
    when(user.getEmail()).thenReturn(email);
    when(userLocalStorage.loadUser()).thenReturn(user);

    String emailResult = dataProvider.getUserEmail(context);

    assertEquals(email, emailResult);

    verify(user).getEmail();
    verify(userLocalStorage).loadUser();
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

  @Test
  public void testForgotPassword() throws IOException {
    Call<Message> call = setupCall(mock(Message.class));
    when(authenticationApi.requestResetPassword(isA(Email.class))).thenReturn(call);

    Observable<DataResponse> dataResponseObservable = dataProvider.forgotPassword(context, "email");

    TestSubscriber<DataResponse> testSubscriber = getTestSubscriber(dataResponseObservable);

    verify(authenticationApi).requestResetPassword(isA(Email.class));

    assertTestSubscriberCompletion(testSubscriber);
  }

  @NonNull
  private <T> TestSubscriber<T> getTestSubscriber(@NonNull Observable<T> observable) {
    TestSubscriber<T> testSubscriber = new TestSubscriber<>();
    observable.subscribe(testSubscriber);
    testSubscriber.awaitTerminalEvent();

    return testSubscriber;
  }

  private void assertTestSubscriberCompletion(
      @NonNull TestSubscriber<DataResponse> testSubscriber) {
    testSubscriber.assertNoErrors();
    testSubscriber.assertCompleted();
    testSubscriber.assertUnsubscribed();
    testSubscriber.assertValueCount(1);
  }
}