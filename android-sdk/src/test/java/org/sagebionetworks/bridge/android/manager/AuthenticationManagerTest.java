package org.sagebionetworks.bridge.android.manager;

import android.accounts.AccountManager;

import com.google.common.base.Objects;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sagebionetworks.bridge.android.BridgeConfig;
import org.sagebionetworks.bridge.android.manager.dao.AccountDAO;
import org.sagebionetworks.bridge.android.rx.MockRxHelper;
import org.sagebionetworks.bridge.rest.ApiClientProvider;
import org.sagebionetworks.bridge.rest.api.AuthenticationApi;
import org.sagebionetworks.bridge.rest.api.ForConsentedUsersApi;
import org.sagebionetworks.bridge.rest.exceptions.BridgeSDKException;
import org.sagebionetworks.bridge.rest.model.Email;
import org.sagebionetworks.bridge.rest.model.Message;
import org.sagebionetworks.bridge.rest.model.SignIn;
import org.sagebionetworks.bridge.rest.model.SignUp;
import org.sagebionetworks.bridge.rest.model.StudyParticipant;
import org.sagebionetworks.bridge.rest.model.UserSessionInfo;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Response;
import rx.Completable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Created by jyliu on 2/8/2017.
 */
public class AuthenticationManagerTest {

    private static final String STUDY_ID = "study-id";
    private static final String ACCOUNT_TYPE = "accountType";
    private static final String EMAIL = "email@test.com";
    private static final String PASSWORD = "P4ssw0rd";


    @Mock
    private BridgeConfig config;
    @Mock
    private ApiClientProvider apiClientProvider;
    @Mock
    private AuthenticationApi authenticationApi;
    @Mock
    private AccountDAO accountDAO;
    @Mock
    private AccountManager accountManager;

    private AuthenticationManager authenticationManager;

    @Before
    public void beforeTest() {
        MockitoAnnotations.initMocks(this);

        when(config.getStudyId()).thenReturn(STUDY_ID);
        when(config.getAccountType()).thenReturn(ACCOUNT_TYPE);
        when(apiClientProvider.getClient(AuthenticationApi.class)).thenReturn(authenticationApi);

        authenticationManager = new AuthenticationManager(config, apiClientProvider, accountDAO,
                new MockRxHelper(), accountManager);
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

        Completable completable = authenticationManager.signUp(signUp);

        completable.test().awaitTerminalEvent().assertCompleted();

        verify(authenticationApi).signUp(signUp);
        verify(accountDAO).setEmail(null);
        verify(accountDAO, atLeastOnce()).setUserSessionInfo(null);
        verify(accountDAO).setStudyParticipant(null);

        verify(accountDAO).setEmail(signUp.getEmail());
        verify(accountDAO).setStudyParticipant(argThat(participant -> matches(signUp, participant)));
    }

    @Test
    public void signUp_error() throws IOException {
        SignUp signUp = new SignUp().study(STUDY_ID).email(EMAIL).password(PASSWORD);

        Call messageCall = errorCall(new BridgeSDKException("Failed", 500));
        when(authenticationApi.signUp(signUp)).thenReturn(messageCall);

        authenticationManager.signUp(signUp).test().awaitTerminalEvent().assertError(BridgeSDKException.class);

        verify(authenticationApi).signUp(signUp);
        verify(accountDAO).setEmail(null);
        verify(accountDAO).setStudyParticipant(null);
    }

    @Test
    public void getRawApi_NullSignIn() throws Exception {
        ForConsentedUsersApi forConsentedUsersApi = mock(ForConsentedUsersApi.class);

//        when(accountDAO.getSignIn()).thenReturn(null);
        when(apiClientProvider.getClient(ForConsentedUsersApi.class))
                .thenReturn(forConsentedUsersApi);

        ForConsentedUsersApi result = authenticationManager.getRawApi();
        assertNotNull(forConsentedUsersApi);

        verify(apiClientProvider).getClient(ForConsentedUsersApi.class);
//        verify(accountDAO).getSignIn();
    }

    private boolean matches(SignUp signUp, SignIn signIn) {
        return Objects.equal(signIn.getEmail(), signUp.getEmail())
                && Objects.equal(signIn.getPassword(), signUp.getPassword())
                && Objects.equal(signIn.getStudy(), signUp.getStudy());
    }

    private boolean matches(SignUp signUp, StudyParticipant studyParticipant) {
        return (signUp != null) && (studyParticipant != null)
                && Objects.equal(signUp.getEmail(), studyParticipant.getEmail())
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

        authenticationManager.resendEmailVerification(EMAIL).test().awaitTerminalEvent().assertCompleted();

        verify(authenticationApi).resendEmailVerification(email);
    }

    @Test
    public void signIn() throws Exception {
        UserSessionInfo userSessionInfo = mock(UserSessionInfo.class);
        Call<UserSessionInfo> userSessionInfoCall = successCall(userSessionInfo);

        SignIn signIn = new SignIn().study(STUDY_ID).email(EMAIL).password(PASSWORD);

        when(authenticationApi.signIn(signIn)).thenReturn(userSessionInfoCall);

        authenticationManager.signIn(EMAIL, PASSWORD).test().awaitTerminalEvent()
                .assertValue(userSessionInfo).assertCompleted();

        verify(accountDAO).setEmail(null);
        verify(accountDAO).setUserSessionInfo(null);
        verify(accountDAO).setStudyParticipant(null);

        verify(accountDAO).setEmail(EMAIL);
        verify(accountDAO).setUserSessionInfo(userSessionInfo);
        verify(accountDAO).setStudyParticipant(
                argThat(participant -> participant != null && EMAIL.equals(participant.getEmail())));

        verify(authenticationApi).signIn(signIn);
    }

    @Test
    public void signOut() throws Exception {
        Message message = mock(Message.class);
        Call<Message> messageCall = successCall(message);

        when(authenticationApi.signOut()).thenReturn(messageCall);

        authenticationManager.signOut().test().awaitTerminalEvent()
                .assertCompleted();

        verify(accountDAO).setEmail(null);
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

        authenticationManager.requestPasswordReset(EMAIL).test().awaitTerminalEvent().assertCompleted();

        verify(authenticationApi).requestResetPassword(email);
    }

    @Test
    public void getApi() throws Exception {
        // TODO: fix test once there is no more Proxy
        ForConsentedUsersApi api = authenticationManager.getApi();

        assertTrue(api instanceof ProxiedForConsentedUsersApi);
    }

    @Test
    public void getEmail() throws Exception {
        when(accountDAO.getEmail()).thenReturn(EMAIL);

        String email = authenticationManager.getEmail();

        assertEquals(EMAIL, email);
        verify(accountDAO).getEmail();
    }
}