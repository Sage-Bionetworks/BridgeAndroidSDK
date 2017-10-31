package org.sagebionetworks.bridge.researchstack;

import android.content.Context;
import android.os.Looper;
import android.preference.PreferenceManager;

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
import org.researchstack.backbone.task.Task;
import org.researchstack.skin.AppPrefs;
import org.sagebionetworks.bridge.android.BridgeConfig;
import org.sagebionetworks.bridge.android.manager.AuthenticationManager;
import org.sagebionetworks.bridge.android.manager.BridgeManagerProvider;
import org.sagebionetworks.bridge.android.manager.dao.AccountDAO;
import org.sagebionetworks.bridge.android.manager.dao.ConsentDAO;
import org.sagebionetworks.bridge.researchstack.wrapper.StorageAccessWrapper;
import org.sagebionetworks.bridge.rest.ApiClientProvider;
import org.sagebionetworks.bridge.rest.api.AuthenticationApi;
import org.sagebionetworks.bridge.rest.api.ForConsentedUsersApi;
import org.sagebionetworks.bridge.rest.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.rest.model.ConsentSignature;
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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
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
    protected AuthenticationManager authenticationManager;

    @Before
    public void setupTest() {
        MockitoAnnotations.initMocks(this);

        BridgeManagerProvider.init(bridgeManagerProvider);

        when(bridgeManagerProvider.getApplicationContext()).thenReturn(context);
        when(bridgeManagerProvider.getBridgeConfig()).thenReturn(bridgeConfig);
        when(bridgeManagerProvider.getAccountDao()).thenReturn(accountDAO);
        when(bridgeManagerProvider.getConsentDao()).thenReturn(consentDAO);
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
                new LocalDate(body.birthdate),
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