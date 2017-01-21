package org.sagebionetworks.bridge.android.manager.auth;

import android.content.ComponentCallbacks;
import android.content.res.Configuration;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.sagebionetworks.bridge.android.BridgeConfig;
import org.sagebionetworks.bridge.android.util.retrofit.RxUtils;
import org.sagebionetworks.bridge.rest.ApiClientProvider;
import org.sagebionetworks.bridge.rest.api.AuthenticationApi;
import org.sagebionetworks.bridge.rest.api.ForConsentedUsersApi;
import org.sagebionetworks.bridge.rest.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.rest.model.Email;
import org.sagebionetworks.bridge.rest.model.Message;
import org.sagebionetworks.bridge.rest.model.PasswordReset;
import org.sagebionetworks.bridge.rest.model.SignIn;
import org.sagebionetworks.bridge.rest.model.SignUp;
import org.sagebionetworks.bridge.rest.model.UserSessionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import rx.Completable;
import rx.Single;
import rx.functions.Action1;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by jyliu on 1/19/2017.
 */
@AnyThread
public class AuthManager {
    private static final Logger logger = LoggerFactory.getLogger(AuthManager.class);

    private final AuthManagerDelegateProtocol authManagerDelegateProtocol;
    private final BridgeConfig config;
    private final AuthenticationApi authenticationApi;
    private ApiClientProvider apiClientProvider;
    private ProxiedForConsentedUsersApi proxiedForConsentedUsersApi;

    public AuthManager(@NonNull BridgeConfig config) {
        this(config, new DefaultAuthManagerDelegateProtocol(config.getApplicationContext()));
    }

    public AuthManager(@NonNull BridgeConfig config, @NonNull
            AuthManagerDelegateProtocol authManagerDelegateProtocol) {
        checkNotNull(config);
        checkNotNull(authManagerDelegateProtocol);

        this.config = config;
        this.authManagerDelegateProtocol = authManagerDelegateProtocol;
        this.authenticationApi = apiClientProvider.getClient(AuthenticationApi.class);

        initApiClientProvider();
        config.getApplicationContext()
                .registerComponentCallbacks(new AuthManagerComponentCallback());
    }

    private void initApiClientProvider() {
        this.apiClientProvider = new ApiClientProvider(config.getBaseUrl(),
                                                       config.getUserAgent(),
                                                       config.getAcceptLanguage());
    }

    @AnyThread
    public interface AuthManagerDelegateProtocol {

        @Nullable
        UserSessionInfo getUserSessionInfo();

        void setUserSessionInfo(@Nullable UserSessionInfo userSessionInfo);

        @Nullable
        String getEmail();

        void setEmail(@Nullable String email);

        @Nullable
        String getPassword();

        void setPassword(@Nullable String password);
    }

    /**
     * Basic sign up that fills in the minimal requirements of email and password fields; in
     * general, you would also want to fill in any of the following information available at
     * sign-up time: firstName, lastName, sharingScope, externalId (if used), dataGroups,
     * notifyByEmail, and any custom attributes you've defined for the attributes field.
     *
     * @see #signUp(SignUp)
     */
    @NonNull
    public Completable signUp(@NonNull final String email, @NonNull final String password) {
        checkNotNull(email);
        checkNotNull(password);

        SignUp participantSignUp = new SignUp()
                .study(config.getStudyId())
                .email(email)
                .password(password);

        return signUp(participantSignUp);
    }

    /**
     * On successful sign up, stores email and password. On unsuccessful sign up, email and
     * password are cleared.
     * <p>
     * If the participant already exists, a 200 response will be sent as well as a password reset
     * email.
     *
     * @param signUp participant's information.
     *               To prevent accidental improper settings, study will be set to the studyId
     *               provided in the Bridge, consent will be set to false, and account status
     *               will be cleared.
     */
    @NonNull
    public Completable signUp(@NonNull final SignUp signUp) {
        checkNotNull(signUp);
        checkNotNull(signUp.getEmail());
        checkNotNull(signUp.getPassword());

        signUp.study(config.getStudyId())
                .consent(false)
                .status(null);

        return RxUtils.toBodySingle(authenticationApi.signUp(signUp))
                .doOnSuccess(new Action1<Message>() {
                    @Override
                    public void call(Message message) {
                        authManagerDelegateProtocol.setEmail(signUp.getEmail());
                        authManagerDelegateProtocol.setPassword(signUp.getPassword());
                    }
                }).doOnError(new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        authManagerDelegateProtocol.setEmail(null);
                        authManagerDelegateProtocol.setPassword(null);
                    }
                }).toCompletable();
    }

    /**
     * @param email participant's email address
     */
    @NonNull
    public Completable resendEmailVerification(@NonNull final String email) {
        checkNotNull(email);

        return RxUtils.toBodySingle(
                authenticationApi.resendEmailVerification(
                        new Email()
                                .study(config.getStudyId())
                                .email(email)))
                .toCompletable();
    }

    /**
     * On success, stores participant's email, password and session. If a
     * NotAuthenticatedException is encountered, the participant's stored password and session
     * are cleared.
     *
     * @param email    participant's email address
     * @param password participant's password
     * @return bridge response
     */
    @NonNull
    public Single<UserSessionInfo> signIn(@NonNull final String email, @NonNull final String
            password) {
        checkNotNull(email);
        checkNotNull(password);

        SignIn signIn = new SignIn()
                .study(config.getStudyId())
                .email(email)
                .password(password);

        return RxUtils.toBodySingle(
                authenticationApi.signIn(signIn))
                .doOnSuccess(new Action1<UserSessionInfo>() {
                    @Override
                    public void call(UserSessionInfo userSessionInfo) {
                        authManagerDelegateProtocol.setEmail(email);
                        authManagerDelegateProtocol.setPassword(password);
                        authManagerDelegateProtocol.setUserSessionInfo(userSessionInfo);
                    }
                }).doOnError(new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        if (throwable instanceof NotAuthenticatedException) {
                            authManagerDelegateProtocol.setPassword(null);
                        }
                    }
                });
    }

    /**
     * On success, clears the participant's email, password and session from storage
     */
    @NonNull
    public Completable signOut() {
        return RxUtils.toBodySingle(authenticationApi.signOut())
                .doOnSuccess(new Action1<Message>() {
                    @Override
                    public void call(Message message) {
                        authManagerDelegateProtocol.setEmail(null);
                        authManagerDelegateProtocol.setPassword(null);
                        authManagerDelegateProtocol.setUserSessionInfo(null);
                    }
                }).toCompletable();
    }

    /**
     * An email will be sent if an account is registered with the configured study.
     *
     * @param email participant's email address
     */
    @NonNull
    public Completable requestPasswordResetForEmail(@NonNull String email) {
        checkNotNull(email);

        return RxUtils.toBodySingle(authenticationApi.requestResetPassword(new Email().study
                (config.getStudyId()).email(email))).toCompletable();
    }

    /**
     * On success, the participant's new password will be stored.
     *
     * @param password   new password to use
     * @param resetToken one-time use password reset token, normally emailed to a user's account
     */
    @NonNull
    public Completable resetPasswordToNewPassword(@NonNull final String password, @NonNull
            String resetToken) {
        checkNotNull(password);
        checkNotNull(resetToken);

        return RxUtils.toBodySingle(
                authenticationApi.resetPassword(
                        new PasswordReset()
                                .password(password)
                                .sptoken(resetToken)
                ))
                .doOnSuccess(new Action1<Message>() {
                    @Override
                    public void call(Message message) {
                        authManagerDelegateProtocol.setPassword(password);
                    }
                }).toCompletable();
    }

    @NonNull
    public AuthManagerDelegateProtocol getAuthManagerDelegateProtocol() {
        return authManagerDelegateProtocol;
    }

    /**
     * Get access to bridge API for currently authenticated client.
     *
     * @return API returns access to bridge that always uses the credentials currently held by
     * AuthManager
     */
    @NonNull
    public ForConsentedUsersApi getApi() {
        if (proxiedForConsentedUsersApi != null) {
            return proxiedForConsentedUsersApi;
        }
        ForConsentedUsersApi api = apiClientProvider.getClient(ForConsentedUsersApi.class,
                                                               getSignInFromStore());
        proxiedForConsentedUsersApi = new ProxiedForConsentedUsersApi(this);

        return proxiedForConsentedUsersApi;
    }

    /**
     * Get latest session, may cause a sign in or retrieve from local store if needed.
     *
     * @return session bridge session
     */
    @Nullable
    public UserSessionInfo getUserSessionInfo() {

        try {
            UserSessionInfo session = apiClientProvider.getUserSessionInfo(getSignInFromStore());
            if (session != null) {
                authManagerDelegateProtocol.setUserSessionInfo(session);
                return session;
            }
        } catch (IOException e) {
            logger.warn("Error getting fresh session", e);
            // don't need to remove session from local store here, only on logout type actions
        }
        return authManagerDelegateProtocol.getUserSessionInfo();
    }

    private SignIn getSignInFromStore() {
        return new SignIn()
                .study(config.getStudyId())
                .email(authManagerDelegateProtocol.getEmail())
                .password(authManagerDelegateProtocol.getPassword());
    }

    class AuthManagerComponentCallback implements ComponentCallbacks {
        @Override
        public void onConfigurationChanged(Configuration newConfig) {
            initApiClientProvider();
        }

        @Override
        public void onLowMemory() {

        }
    }
}
