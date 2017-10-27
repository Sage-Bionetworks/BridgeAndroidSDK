package org.sagebionetworks.bridge.android.manager;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.sagebionetworks.bridge.android.BridgeApiTestUtils;
import org.sagebionetworks.bridge.android.BridgeConfig;
import org.sagebionetworks.bridge.android.manager.dao.AccountDAO;
import org.sagebionetworks.bridge.android.manager.dao.ConsentDAO;
import org.sagebionetworks.bridge.rest.ApiClientProvider;
import org.sagebionetworks.bridge.rest.UserSessionInfoProvider;
import org.sagebionetworks.bridge.rest.api.AuthenticationApi;
import org.sagebionetworks.bridge.rest.api.ForConsentedUsersApi;
import org.sagebionetworks.bridge.rest.exceptions.BridgeSDKException;
import org.sagebionetworks.bridge.rest.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.rest.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.rest.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.rest.model.ConsentSignature;
import org.sagebionetworks.bridge.rest.model.ConsentStatus;
import org.sagebionetworks.bridge.rest.model.Email;
import org.sagebionetworks.bridge.rest.model.Message;
import org.sagebionetworks.bridge.rest.model.SharingScope;
import org.sagebionetworks.bridge.rest.model.SignIn;
import org.sagebionetworks.bridge.rest.model.SignUp;
import org.sagebionetworks.bridge.rest.model.StudyParticipant;
import org.sagebionetworks.bridge.rest.model.UserSessionInfo;
import org.sagebionetworks.bridge.rest.model.Withdrawal;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Response;
import rx.Completable;
import rx.Observable;
import rx.Single;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertSame;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Created by jyliu on 2/8/2017.
 */
@SuppressWarnings("unchecked")
public class AuthenticationManagerTest {

    private static final String STUDY_ID = "study-id";
    private static final String EMAIL = "email@test.com";
    private static final String PASSWORD = "P4ssw0rd";
    private static final String SUBPOPULATION_GUID = "subpopulationGuid";


    @Mock
    private BridgeConfig config;
    @Mock
    private ApiClientProvider apiClientProvider;
    @Mock
    private AuthenticationApi authenticationApi;
    @Mock
    private AccountDAO accountDAO;
    @Mock
    private ConsentDAO consentDAO;

    private AuthenticationManager spyAuthenticationManager;

    @Before
    public void beforeTest() {
        MockitoAnnotations.initMocks(this);

        when(config.getStudyId()).thenReturn(STUDY_ID);
        when(apiClientProvider.getClient(AuthenticationApi.class)).thenReturn(authenticationApi);

        spyAuthenticationManager = spy(new AuthenticationManager(config, apiClientProvider, accountDAO, consentDAO));
    }

    @Test
    public void signUpEmail() throws Exception {
        SignUp signUp = new SignUp().study(STUDY_ID).email(EMAIL).password(PASSWORD);

        testSignUp_helper(signUp);
    }

    private void testSignUp_helper(SignUp signUp) throws IOException {
        Message message = mock(Message.class);
        Call<Message> messageCall = successCall(message);
        when(authenticationApi.signUp(signUp)).thenReturn(messageCall);

        Completable completable = spyAuthenticationManager.signUp(signUp);

        completable.test().awaitTerminalEvent().assertCompleted();

        verify(authenticationApi).signUp(signUp);
        verify(accountDAO).setSignIn(argThat(signIn -> matches(signUp, signIn)));
        verify(accountDAO).setStudyParticipant(argThat(participant -> matches(signUp, participant)));
    }

    @Test
    public void signUp_error() throws IOException {
        SignUp signUp = new SignUp().study(STUDY_ID).email(EMAIL).password(PASSWORD);

        Call<Message> messageCall = errorCall(new BridgeSDKException("Failed", 500));
        when(authenticationApi.signUp(signUp)).thenReturn(messageCall);

        spyAuthenticationManager.signUp(signUp).test().awaitTerminalEvent().assertError(BridgeSDKException.class);

        verify(authenticationApi).signUp(signUp);
        verify(accountDAO).setSignIn(null);
        verify(accountDAO).setStudyParticipant(null);
    }

    private boolean matches(SignUp signUp, SignIn signIn) {
        return Objects.equal(signIn.getEmail(), signUp.getEmail())
                && Objects.equal(signIn.getPassword(), signUp.getPassword())
                && Objects.equal(signIn.getStudy(), signUp.getStudy());
    }

    private boolean matches(SignUp signUp, StudyParticipant studyParticipant) {
        return Objects.equal(signUp.getEmail(), studyParticipant.getEmail())
                && Objects.equal(signUp.getFirstName(), studyParticipant.getFirstName())
                && Objects.equal(signUp.getLastName(), studyParticipant.getLastName())
                && Objects.equal(signUp.getExternalId(), studyParticipant.getExternalId());
    }


    private <T> Call<T> successCall(T value) throws IOException {
        Response<T> response = Response.success(value);

        Call<T> messageCall = mock(Call.class);
        when(messageCall.clone()).thenReturn(messageCall);
        when(messageCall.isExecuted()).thenReturn(true);
        when(messageCall.isCanceled()).thenReturn(false);
        when(messageCall.execute()).thenReturn(response);
        return messageCall;
    }

    private <T> Call<T> errorCall(Throwable t) throws IOException {
        Call<T> messageCall = mock(Call.class);

        when(messageCall.clone()).thenReturn(messageCall);
        when(messageCall.isExecuted()).thenReturn(true);
        when(messageCall.isCanceled()).thenReturn(false);
        when(messageCall.execute()).thenThrow(t);

        return messageCall;
    }

    @Test
    public void resendEmailVerification() throws Exception {
        Message message = mock(Message.class);
        Call<Message> messageCall = successCall(message);

        Email email = new Email().study(STUDY_ID).email(EMAIL);

        when(authenticationApi.resendEmailVerification(email))
                .thenReturn(messageCall);

        spyAuthenticationManager.resendEmailVerification(EMAIL).test().awaitTerminalEvent().assertCompleted();

        verify(authenticationApi).resendEmailVerification(email);
    }

    @Test
    public void signIn() throws Exception {
        UserSessionInfo userSessionInfo = mock(UserSessionInfo.class);
        Call<UserSessionInfo> userSessionInfoCall = successCall(userSessionInfo);

        SignIn signIn = new SignIn().study(STUDY_ID).email(EMAIL).password(PASSWORD);

        when(authenticationApi.signIn(signIn)).thenReturn(userSessionInfoCall);

        spyAuthenticationManager.signIn(EMAIL, PASSWORD).test().awaitTerminalEvent()
                .assertValue(userSessionInfo).assertCompleted();

        verify(accountDAO, atLeastOnce()).setSignIn(signIn);
        verify(accountDAO).setUserSessionInfo(userSessionInfo);
        verify(accountDAO).setStudyParticipant(
                argThat(participant -> EMAIL.equals(participant.getEmail())));

        verify(authenticationApi).signIn(signIn);
    }

    @Test
    public void signIn_UploadLocalConsentAndRetrySignIn() throws Exception {
        UserSessionInfo userSessionInfo = mock(UserSessionInfo.class);

        Call<UserSessionInfo> signInCall = mock(Call.class);
        when(signInCall.clone()).thenReturn(signInCall);
        when(signInCall.isExecuted()).thenReturn(true);
        when(signInCall.isCanceled()).thenReturn(false);

        // throw 412 first execution, success on second execution
        when(signInCall.execute())
                .thenThrow(new ConsentRequiredException("msg", "endpoint", userSessionInfo))
                .thenReturn(Response.success(userSessionInfo));

        Observable<UserSessionInfo> userSessionInfoObservable = Observable.just(userSessionInfo);

        SignIn signIn = new SignIn().study(STUDY_ID).email(EMAIL).password(PASSWORD);
        when(authenticationApi.signIn(signIn))
                .thenReturn(signInCall);

        // indicate that locally, we think we are consented
        doReturn(true)
                .when(spyAuthenticationManager).isConsented();
        doReturn(userSessionInfoObservable)
                .when(spyAuthenticationManager).uploadLocalConsents();

        spyAuthenticationManager.signIn(EMAIL, PASSWORD).test().awaitTerminalEvent()
                .assertValue(userSessionInfo).assertCompleted();

        verify(spyAuthenticationManager).isConsented();
        // verify there was an attempt to upload all consents
        verify(spyAuthenticationManager).uploadLocalConsents();

        verify(accountDAO, atLeastOnce()).setSignIn(signIn);
        verify(accountDAO).setUserSessionInfo(userSessionInfo);
        verify(accountDAO).setStudyParticipant(
                argThat(participant -> EMAIL.equals(participant.getEmail())));

        verify(signInCall, times(2)).execute();
        verify(authenticationApi).signIn(signIn);
    }

    @Test
    public void signIn_UploadLocalConsentRetrySignInOnce() throws Exception {
        UserSessionInfo userSessionInfo = mock(UserSessionInfo.class);

        Call<UserSessionInfo> signInCall = mock(Call.class);
        when(signInCall.clone()).thenReturn(signInCall);
        when(signInCall.isExecuted()).thenReturn(true);
        when(signInCall.isCanceled()).thenReturn(false);

        ConsentRequiredException exception = new ConsentRequiredException("msg", "endpoint", userSessionInfo);
        // throw 412 every time
        when(signInCall.execute())
                .thenThrow(exception);

        Observable<UserSessionInfo> userSessionInfoObservable = Observable.just(userSessionInfo);

        SignIn signIn = new SignIn().study(STUDY_ID).email(EMAIL).password(PASSWORD);
        when(authenticationApi.signIn(signIn))
                .thenReturn(signInCall);

        // indicate that locally, we think we are consented
        doReturn(true)
                .when(spyAuthenticationManager).isConsented();
        doReturn(userSessionInfoObservable)
                .when(spyAuthenticationManager).uploadLocalConsents();

        // don't try uploading consent and retrying sign-in multiple times, i.e.
        // the ConsentRequiredException should be propagated the second time it occurs
        spyAuthenticationManager.signIn(EMAIL, PASSWORD).test().awaitTerminalEvent()
                .assertError(exception);

        verify(spyAuthenticationManager).isConsented();
        // verify there was an attempt to upload all consents
        verify(spyAuthenticationManager).uploadLocalConsents();

        verify(accountDAO, atLeastOnce()).setSignIn(signIn);
        verify(accountDAO).setUserSessionInfo(userSessionInfo);
        verify(accountDAO).setStudyParticipant(
                argThat(participant -> EMAIL.equals(participant.getEmail())));

        // no infinite loops
        verify(signInCall, times(2)).execute();
        verify(authenticationApi).signIn(signIn);
    }

    @Test
    public void signOut() throws Exception {
        Message message = mock(Message.class);
        Call<Message> messageCall = successCall(message);

        when(authenticationApi.signOut()).thenReturn(messageCall);

        spyAuthenticationManager.signOut().test().awaitTerminalEvent()
                .assertCompleted();

        verify(accountDAO).setSignIn(null);
        verify(accountDAO).setStudyParticipant(null);
        verify(accountDAO).setUserSessionInfo(null);
    }

    @Test
    public void requestPasswordResetForEmail() throws Exception {
        Message message = mock(Message.class);
        Call<Message> messageCall = successCall(message);

        Email email = new Email().study(STUDY_ID).email(EMAIL);

        when(authenticationApi.requestResetPassword(email))
                .thenReturn(messageCall);

        spyAuthenticationManager.requestPasswordReset(EMAIL).test().awaitTerminalEvent().assertCompleted();

        verify(authenticationApi).requestResetPassword(email);
    }

    @Test
    public void getApi_NullSignIn() throws Exception {
        ForConsentedUsersApi forConsentedUsersApi = mock(ForConsentedUsersApi.class);

        when(accountDAO.getSignIn()).thenReturn(null);
        when(apiClientProvider.getClient(ForConsentedUsersApi.class))
                .thenReturn(forConsentedUsersApi);

        ForConsentedUsersApi result = spyAuthenticationManager.getApi();
        assertSame(forConsentedUsersApi, result);

        verify(apiClientProvider).getClient(ForConsentedUsersApi.class);
        verify(accountDAO).getSignIn();
    }

    @Test
    public void getApi_WithSignIn() throws Exception {
        ForConsentedUsersApi forConsentedUsersApi = mock(ForConsentedUsersApi.class);

        SignIn signIn = new SignIn();
        when(accountDAO.getSignIn()).thenReturn(signIn);
        when(apiClientProvider.getClient(ForConsentedUsersApi.class, signIn))
                .thenReturn(forConsentedUsersApi);

        ForConsentedUsersApi result = spyAuthenticationManager.getApi();
        assertSame(forConsentedUsersApi, result);

        verify(apiClientProvider).getClient(ForConsentedUsersApi.class, signIn);
        verify(accountDAO).getSignIn();
    }

    @Test
    public void getEmail() throws Exception {
        when(accountDAO.getSignIn()).thenReturn(new SignIn().email(EMAIL));

        String email = spyAuthenticationManager.getEmail();

        assertEquals(EMAIL, email);
        verify(accountDAO).getSignIn();
    }

    @Test
    public void getUserSessionInfo_NullProvider() {
        // mock dependencies
        UserSessionInfo previousSession = new UserSessionInfo();
        when(accountDAO.getUserSessionInfo()).thenReturn(previousSession);

        SignIn signIn = new SignIn();
        when(accountDAO.getSignIn()).thenReturn(signIn);
        when(apiClientProvider.getUserSessionInfoProvider(signIn)).thenReturn(null);

        // execute and validate
        UserSessionInfo result = spyAuthenticationManager.getUserSessionInfo();
        assertSame(previousSession, result);
        verify(accountDAO, never()).setUserSessionInfo(any());
    }

    @Test
    public void getUserSessionInfo_NoCachedSession() {
        // mock dependencies
        UserSessionInfo previousSession = new UserSessionInfo();
        when(accountDAO.getUserSessionInfo()).thenReturn(previousSession);

        SignIn signIn = new SignIn();
        when(accountDAO.getSignIn()).thenReturn(signIn);

        UserSessionInfoProvider sessionProvider = mock(UserSessionInfoProvider.class);
        when(apiClientProvider.getUserSessionInfoProvider(signIn)).thenReturn(sessionProvider);
        when(sessionProvider.getSession()).thenReturn(null);

        // execute and validate
        UserSessionInfo result = spyAuthenticationManager.getUserSessionInfo();
        assertSame(previousSession, result);
        verify(accountDAO, never()).setUserSessionInfo(any());
    }

    @Test
    public void getUserSessionInfo_WithCachedSession() {
        // mock dependencies
        SignIn signIn = new SignIn();
        when(accountDAO.getSignIn()).thenReturn(signIn);

        UserSessionInfoProvider sessionProvider = mock(UserSessionInfoProvider.class);
        when(apiClientProvider.getUserSessionInfoProvider(signIn)).thenReturn(sessionProvider);

        UserSessionInfo cachedSession = new UserSessionInfo();
        when(sessionProvider.getSession()).thenReturn(cachedSession);

        // execute and validate
        UserSessionInfo result = spyAuthenticationManager.getUserSessionInfo();
        assertSame(cachedSession, result);
        verify(accountDAO).setUserSessionInfo(same(cachedSession));
    }

    // region Consent

    @Test
    public void isConsentedForSubpopulationInSessionOrLocal_ConsentedInSession() {
        Map<String, ConsentStatus> consentStatuses = Maps.newHashMap();
        consentStatuses.put(SUBPOPULATION_GUID, new ConsentStatus().consented(true));

        UserSessionInfo userSessionInfo = mock(UserSessionInfo.class);
        doReturn(consentStatuses).when(userSessionInfo).getConsentStatuses();

        doReturn(userSessionInfo).when(spyAuthenticationManager).getUserSessionInfo();

        boolean result = spyAuthenticationManager.isConsentedInSessionOrLocal(userSessionInfo, SUBPOPULATION_GUID);

        assertTrue(result);

        verify(spyAuthenticationManager).getUserSessionInfo();
        verify(userSessionInfo).getConsentStatuses();
    }

    @Test
    public void isConsentedForSubpopulationInSessionOrLocal_NotConsentedInSession() {
        Map<String, ConsentStatus> consentStatuses = Maps.newHashMap();
        consentStatuses.put(SUBPOPULATION_GUID, new ConsentStatus().consented(false));

        UserSessionInfo userSessionInfo = mock(UserSessionInfo.class);
        doReturn(consentStatuses).when(userSessionInfo).getConsentStatuses();

        doReturn(userSessionInfo).when(spyAuthenticationManager).getUserSessionInfo();

        boolean result = spyAuthenticationManager.isConsentedInSessionOrLocal(userSessionInfo, SUBPOPULATION_GUID);

        assertFalse(result);

        verify(spyAuthenticationManager).getUserSessionInfo();
        verify(userSessionInfo).getConsentStatuses();
    }

    @Test
    public void isConsentedForSubpopulationInSessionOrLocal_NoConsentedInSession() {
        Map<String, ConsentStatus> consentStatuses = Maps.newHashMap();
        UserSessionInfo userSessionInfo = mock(UserSessionInfo.class);
        doReturn(consentStatuses).when(userSessionInfo).getConsentStatuses();

        doReturn(userSessionInfo).when(spyAuthenticationManager).getUserSessionInfo();

        boolean result = spyAuthenticationManager.isConsentedInSessionOrLocal(userSessionInfo, SUBPOPULATION_GUID);

        assertFalse(result);

        verify(spyAuthenticationManager).getUserSessionInfo();
        verify(userSessionInfo).getConsentStatuses();
    }

    @Test
    public void isConsentedForSubpopulationInSessionOrLocal_LocallyConsented() {
        Map<String, ConsentStatus> consentStatuses = Maps.newHashMap();
        consentStatuses.put(SUBPOPULATION_GUID, new ConsentStatus().consented(false));

        UserSessionInfo userSessionInfo = mock(UserSessionInfo.class);
        doReturn(consentStatuses).when(userSessionInfo).getConsentStatuses();

        doReturn(userSessionInfo).when(spyAuthenticationManager).getUserSessionInfo();
        doReturn(new ConsentSignature()).when(consentDAO).getConsent(SUBPOPULATION_GUID);

        boolean result = spyAuthenticationManager.isConsentedInSessionOrLocal(userSessionInfo, SUBPOPULATION_GUID);

        assertTrue(result);

        verify(spyAuthenticationManager).getUserSessionInfo();
        verify(userSessionInfo).getConsentStatuses();
    }

    @Test
    public void isConsented() throws Exception {
        UserSessionInfo userSessionInfo = mock(UserSessionInfo.class);

        doReturn(userSessionInfo).when(spyAuthenticationManager).getUserSessionInfo();

        doReturn(Sets.newHashSet("A", "B", "C"))
                .when(spyAuthenticationManager)
                .getRequiredConsents(userSessionInfo);

        doReturn(true).when(spyAuthenticationManager).isConsentedInSessionOrLocal(userSessionInfo, "A");
        doReturn(true).when(spyAuthenticationManager).isConsentedInSessionOrLocal(userSessionInfo, "B");
        doReturn(true).when(spyAuthenticationManager).isConsentedInSessionOrLocal(userSessionInfo, "C");

        boolean result = spyAuthenticationManager.isConsented();

        assertTrue(result);

        verify(spyAuthenticationManager).getUserSessionInfo();

        verify(spyAuthenticationManager).getRequiredConsents(userSessionInfo);

        verify(spyAuthenticationManager).isConsentedInSessionOrLocal(userSessionInfo, "A");
        verify(spyAuthenticationManager).isConsentedInSessionOrLocal(userSessionInfo, "B");
        verify(spyAuthenticationManager).isConsentedInSessionOrLocal(userSessionInfo, "C");
    }

    @Test
    public void isConsented_MissingConsent() throws Exception {
        UserSessionInfo userSessionInfo = mock(UserSessionInfo.class);

        doReturn(userSessionInfo).when(spyAuthenticationManager).getUserSessionInfo();

        doReturn(Sets.newHashSet("A", "B", "C"))
                .when(spyAuthenticationManager)
                .getRequiredConsents(userSessionInfo);

        doReturn(true).when(spyAuthenticationManager).isConsentedInSessionOrLocal(userSessionInfo, "A");
        doReturn(false).when(spyAuthenticationManager).isConsentedInSessionOrLocal(userSessionInfo, "B");
        doReturn(true).when(spyAuthenticationManager).isConsentedInSessionOrLocal(userSessionInfo, "C");

        boolean result = spyAuthenticationManager.isConsented();

        assertFalse(result);

        verify(spyAuthenticationManager).getUserSessionInfo();

        verify(spyAuthenticationManager).getRequiredConsents(userSessionInfo);

        verify(spyAuthenticationManager).isConsentedInSessionOrLocal(userSessionInfo, "A");
        verify(spyAuthenticationManager).isConsentedInSessionOrLocal(userSessionInfo, "B");
        verify(spyAuthenticationManager, times(0)).isConsentedInSessionOrLocal(userSessionInfo, "C");
    }

    @Test
    public void getRequiredConsents() {
        Map<String, ConsentStatus> consentStatuses = Maps.newHashMap();
        consentStatuses.put("A", new ConsentStatus().required(true));
        consentStatuses.put("B", new ConsentStatus().required(false));
        consentStatuses.put("C", new ConsentStatus().required(true));

        UserSessionInfo userSessionInfo = mock(UserSessionInfo.class);
        doReturn(consentStatuses).when(userSessionInfo).getConsentStatuses();

        Set<String> result = spyAuthenticationManager.getRequiredConsents(userSessionInfo);

        assertEquals(Sets.newHashSet("A", "C"), result);

        verify(userSessionInfo, atLeastOnce()).getConsentStatuses();
    }

    @Test
    public void giveConsent() throws Exception {
        String name = "NAME";
        LocalDate birthdate = LocalDate.now().minusYears(20);
        String imgString = "base64image";
        String mimeType = "mimeType";
        SharingScope sharingScope = SharingScope.ALL_QUALIFIED_RESEARCHERS;

        ConsentSignature sig = new ConsentSignature()
                .name(name)
                .birthdate(birthdate)
                .imageData(imgString)
                .imageMimeType(mimeType)
                .scope(sharingScope);

        // consent should get stored locally
        doReturn(sig).when(spyAuthenticationManager).giveConsentSync(SUBPOPULATION_GUID,
                name,
                birthdate,
                imgString,
                mimeType,
                sharingScope);

        // mock api.createConsentSignature()
        ForConsentedUsersApi mockApi = mock(ForConsentedUsersApi.class);
        doReturn(mockApi).when(spyAuthenticationManager).getApi();

        UserSessionInfo userSessionInfo = new UserSessionInfo().email(EMAIL);
        Call<UserSessionInfo> mockCall = BridgeApiTestUtils.mockCallWithValue(userSessionInfo);
        when(mockApi.createConsentSignature(SUBPOPULATION_GUID, sig)).thenReturn(mockCall);

        // execute and validate
        Single<UserSessionInfo> sessionSingle = spyAuthenticationManager.giveConsent(SUBPOPULATION_GUID, name,
                birthdate, imgString, mimeType, sharingScope);
        assertSame(userSessionInfo, sessionSingle.toBlocking().value());

        verify(spyAuthenticationManager).giveConsentSync(SUBPOPULATION_GUID,
                name,
                birthdate,
                imgString,
                mimeType,
                sharingScope);

        verify(mockApi).createConsentSignature(SUBPOPULATION_GUID, sig);
    }

    @Test
    public void uploadLocalConsents() throws Exception {
        // make test data
        ConsentSignature consentSignature1 = new ConsentSignature().name("Tester 1");
        ConsentSignature consentSignature2 = new ConsentSignature().name("Tester 2");
        UserSessionInfo session1 = new UserSessionInfo().email("tester1@example.com");
        UserSessionInfo session2 = new UserSessionInfo().email("tester2@example.com");

        // mock consentDAO
        when(consentDAO.listConsents()).thenReturn(ImmutableSet.of("subpop-1", "subpop-2"));
        when(consentDAO.getConsent("subpop-1")).thenReturn(consentSignature1);
        when(consentDAO.getConsent("subpop-2")).thenReturn(consentSignature2);

        // mock api.createConsentSignature
        ForConsentedUsersApi mockApi = mock(ForConsentedUsersApi.class);
        doReturn(mockApi).when(spyAuthenticationManager).getApi();

        Call<UserSessionInfo> mockCall1 = BridgeApiTestUtils.mockCallWithValue(session1);
        when(mockApi.createConsentSignature("subpop-1", consentSignature1)).thenReturn(mockCall1);

        Call<UserSessionInfo> mockCall2 = BridgeApiTestUtils.mockCallWithValue(session2);
        when(mockApi.createConsentSignature("subpop-2", consentSignature2)).thenReturn(mockCall2);

        // execute and validate
        Observable<UserSessionInfo> sessionObservable = spyAuthenticationManager.uploadLocalConsents();
        Set<UserSessionInfo> sessionSet = ImmutableSet.copyOf(sessionObservable.toBlocking().getIterator());
        assertEquals(2, sessionSet.size());
        assertTrue(sessionSet.contains(session1));
        assertTrue(sessionSet.contains(session2));

        verify(mockApi).createConsentSignature("subpop-1", consentSignature1);
        verify(mockApi).createConsentSignature("subpop-2", consentSignature2);
    }

    @Test
    public void getConsentSignature_Success() throws Exception {
        // Make consent signature.
        ConsentSignature sig = new ConsentSignature().name("Eggplant McTester");

        // mock api.getConsentSignature()
        ForConsentedUsersApi mockApi = mock(ForConsentedUsersApi.class);
        doReturn(mockApi).when(spyAuthenticationManager).getApi();

        Call<ConsentSignature> mockCall = BridgeApiTestUtils.mockCallWithValue(sig);
        when(mockApi.getConsentSignature(SUBPOPULATION_GUID)).thenReturn(mockCall);

        // execute and validate
        Single<ConsentSignature> sigSingle = spyAuthenticationManager.getConsentSignature(SUBPOPULATION_GUID);
        assertSame(sig, sigSingle.toBlocking().value());

        verify(mockApi).getConsentSignature(SUBPOPULATION_GUID);
        verify(consentDAO, never()).getConsent(any());
    }

    @Test
    public void getConsentSignature_404NotFound() throws Exception {
        // Make consent signature.
        ConsentSignature sig = new ConsentSignature().name("Eggplant McTester");

        // mock api.getConsentSignature() - This throws a 404. We fall back to the local consent in consentDAO.
        ForConsentedUsersApi mockApi = mock(ForConsentedUsersApi.class);
        doReturn(mockApi).when(spyAuthenticationManager).getApi();

        Call<ConsentSignature> mockCall = BridgeApiTestUtils.mockCallWithException(EntityNotFoundException.class);
        when(mockApi.getConsentSignature(SUBPOPULATION_GUID)).thenReturn(mockCall);

        // mock consentDAO
        when(consentDAO.getConsent(SUBPOPULATION_GUID)).thenReturn(sig);

        // execute and validate
        Single<ConsentSignature> sigSingle = spyAuthenticationManager.getConsentSignature(SUBPOPULATION_GUID);
        assertSame(sig, sigSingle.toBlocking().value());

        verify(mockApi).getConsentSignature(SUBPOPULATION_GUID);
        verify(consentDAO).getConsent(SUBPOPULATION_GUID);
    }

    @Test
    public void getConsentSignature_500InternalServerError() throws Exception {
        // mock api.getConsentSignature()
        ForConsentedUsersApi mockApi = mock(ForConsentedUsersApi.class);
        doReturn(mockApi).when(spyAuthenticationManager).getApi();

        Call<ConsentSignature> mockCall = BridgeApiTestUtils.mockCallWithException(BridgeServiceException.class);
        when(mockApi.getConsentSignature(SUBPOPULATION_GUID)).thenReturn(mockCall);

        // execute and validate
        Single<ConsentSignature> sigSingle = spyAuthenticationManager.getConsentSignature(SUBPOPULATION_GUID);
        sigSingle.test().awaitTerminalEvent().assertError(BridgeServiceException.class);

        verify(mockApi).getConsentSignature(SUBPOPULATION_GUID);
        verify(consentDAO, never()).getConsent(any());
    }

    @Test
    public void withdrawAll() throws Exception {
        // mock api.withdrawAllConsents()
        ForConsentedUsersApi mockApi = mock(ForConsentedUsersApi.class);
        doReturn(mockApi).when(spyAuthenticationManager).getApi();

        // Result from withdraw call is discarded. Just set a null.
        Call<UserSessionInfo> mockCall = BridgeApiTestUtils.mockCallWithValue(null);
        when(mockApi.withdrawAllConsents(any())).thenReturn(mockCall);

        // execute and validate
        String reason = "because";
        Completable completable = spyAuthenticationManager.withdrawAll(reason);
        completable.test().awaitTerminalEvent().assertNoErrors().assertCompleted();

        ArgumentCaptor<Withdrawal> withdrawalCaptor = ArgumentCaptor.forClass(Withdrawal.class);
        verify(mockApi).withdrawAllConsents(withdrawalCaptor.capture());
        assertEquals(reason, withdrawalCaptor.getValue().getReason());
    }

    @Test
    public void withdrawConsent() throws Exception {
        // mock api.withdrawConsentFromSubpopulation()
        ForConsentedUsersApi mockApi = mock(ForConsentedUsersApi.class);
        doReturn(mockApi).when(spyAuthenticationManager).getApi();

        // Result from withdraw call is discarded. Just set a null.
        Call<UserSessionInfo> mockCall = BridgeApiTestUtils.mockCallWithValue(null);
        when(mockApi.withdrawConsentFromSubpopulation(eq(SUBPOPULATION_GUID), any())).thenReturn(mockCall);

        // execute and validate
        String reason = "because";
        Completable completable = spyAuthenticationManager.withdrawConsent(SUBPOPULATION_GUID, reason);
        completable.test().awaitTerminalEvent().assertNoErrors().assertCompleted();

        ArgumentCaptor<Withdrawal> withdrawalCaptor = ArgumentCaptor.forClass(Withdrawal.class);
        verify(mockApi).withdrawConsentFromSubpopulation(eq(SUBPOPULATION_GUID), withdrawalCaptor.capture());
        assertEquals(reason, withdrawalCaptor.getValue().getReason());
    }
    // endregion
}