package org.sagebionetworks.bridge.researchstack;

import android.content.Context;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.researchstack.backbone.DataProvider;
import org.researchstack.backbone.DataResponse;
import org.researchstack.backbone.model.SchedulesAndTasksModel;
import org.researchstack.backbone.model.User;
import org.researchstack.backbone.result.TaskResult;
import org.researchstack.backbone.storage.file.FileAccess;
import org.researchstack.backbone.storage.file.PinCodeConfig;
import org.researchstack.backbone.task.Task;
import org.sagebionetworks.bridge.android.BridgeConfig;
import org.sagebionetworks.bridge.android.manager.AccountDAO;
import org.sagebionetworks.bridge.android.manager.AuthenticationManager;
import org.sagebionetworks.bridge.android.manager.BridgeManagerProvider;
import org.sagebionetworks.bridge.android.manager.ConsentDAO;
import org.sagebionetworks.bridge.android.manager.ConsentManager;
import org.sagebionetworks.bridge.android.manager.ParticipantManager;
import org.sagebionetworks.bridge.researchstack.wrapper.StorageAccessWrapper;
import org.sagebionetworks.bridge.rest.ApiClientProvider;
import org.sagebionetworks.bridge.rest.api.AuthenticationApi;
import org.sagebionetworks.bridge.rest.api.ForConsentedUsersApi;
import org.sagebionetworks.bridge.rest.model.SharingScope;
import org.sagebionetworks.bridge.rest.model.SignIn;
import org.sagebionetworks.bridge.rest.model.UserSessionInfo;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Response;
import rx.Completable;
import rx.Observable;
import rx.Single;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Created by liujoshua on 9/12/16.
 */
public class BridgeDataProviderTest {
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
    private ConsentLocalStorage consentLocalStorage;
    private UserLocalStorage userLocalStorage;
    @Mock
    private TaskHelper taskHelper;
    @Mock
    private UploadHandler uploadHandler;

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
    protected AuthenticationManager authenticationManager;
    @Mock
    protected ParticipantManager studyParticipantManager;
    @Mock
    protected ConsentManager consentManager;

    @Before
    public void setupTest() {
        MockitoAnnotations.initMocks(this);

        BridgeManagerProvider.init(bridgeManagerProvider);

        when(bridgeManagerProvider.getApplicationContext()).thenReturn(context);
        when(bridgeManagerProvider.getBridgeConfig()).thenReturn(bridgeConfig);
        when(bridgeManagerProvider.getAccountDao()).thenReturn(accountDAO);
        when(bridgeManagerProvider.getConsentDao()).thenReturn(consentDAO);
        when(bridgeManagerProvider.getAuthenticationManager()).thenReturn(authenticationManager);
        when(bridgeManagerProvider.getParticipantManager()).thenReturn(studyParticipantManager);
        when(bridgeManagerProvider.getConsentManager()).thenReturn(consentManager);

        pinCodeConfig = mock(PinCodeConfig.class);
        fileAccess = mock(FileAccess.class);
        appDatabase = mock(BridgeEncryptedDatabase.class);

        storageAccess = mock(StorageAccessWrapper.class);
        when(storageAccess.getPinCodeConfig()).thenReturn(pinCodeConfig);
        when(storageAccess.getAppDatabase()).thenReturn(appDatabase);
        when(storageAccess.getFileAccess()).thenReturn(fileAccess);

        consentLocalStorage = mock(ConsentLocalStorage.class);
        userLocalStorage = mock(UserLocalStorage.class);

        when(apiClientProvider.getClient(AuthenticationApi.class)).thenReturn(authenticationApi);
        when(apiClientProvider.getClient(Matchers.same(ForConsentedUsersApi.class), any(SignIn.class))).thenReturn(forConsentedUsersApi);

        dataProvider =
                new BridgeDataProvider(userLocalStorage, consentLocalStorage,
                        taskHelper, uploadHandler) {

                    @Override
                    public void processInitialTaskResult(Context context, TaskResult taskResult) {

                    }
                };
    }


    @Test
    public void testInitialize() {

        dataProvider.initialize(context).test().assertCompleted();


    }

    private <T> Call<T> setupCall(T body) throws IOException {
        Call<T> call = mock(Call.class);
        when(call.clone()).thenReturn(call);
        when(call.execute()).thenReturn(Response.success(body));
        return call;
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
        Call<UserSessionInfo> sessionCall = mock(Call.class);
        UserSessionInfo session = mock(UserSessionInfo.class);

        when(sessionCall.clone()).thenReturn(sessionCall);
        when(sessionCall.execute()).thenReturn(Response.success(session));

        when(authenticationManager.signIn("email", "password")).thenReturn(Single.just(session));
        Observable<DataResponse> completable = dataProvider.signIn(context, "email", "password");
        completable.test().assertCompleted();

        verify(authenticationManager).signIn("email", "password");

        // TODO: verify background tasks are triggered when session is established
        // verify(uploadHandler).uploadPendingFiles(forConsentedUsersApi);
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
        when(userLocalStorage.isSignedUp()).thenReturn(true);

        boolean isSignedUp = dataProvider.isSignedUp(context);

        assertTrue(isSignedUp);
        verify(userLocalStorage).isSignedUp();
    }


    @Test
    public void testIsConsented() {
        when(consentManager.isConsented()).thenReturn(true);

        boolean isConsented = dataProvider.isConsented();

        assertTrue(isConsented);
        verify(consentManager).isConsented();
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
        when(authenticationManager.requestPasswordReset("email")).
                thenReturn(Completable.complete());

        dataProvider.forgotPassword(context, "email").test().assertCompleted();

        verify(authenticationManager).requestPasswordReset("email");
    }
}