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
import org.sagebionetworks.bridge.rest.model.SignIn;
import org.sagebionetworks.bridge.rest.model.SignUp;
import org.sagebionetworks.bridge.rest.model.StudyParticipant;
import org.sagebionetworks.bridge.rest.model.UserSessionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Completable;
import rx.Single;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Manages Bridge authentication state for an application.
 */
@AnyThread
public class AuthenticationManager {
    private static final Logger logger = LoggerFactory.getLogger(AuthenticationManager.class);

    @NonNull
    private final DAO dao;
    @NonNull
    private final BridgeConfig config;
    private final AuthenticationApi authenticationApi;
    private ApiClientProvider apiClientProvider;
    private ProxiedForConsentedUsersApi proxiedForConsentedUsersApi;

    public AuthenticationManager(@NonNull BridgeConfig config) {
        this(config, new PlaintextSharedPreferencesDAO(config.getApplicationContext()));
    }

    public AuthenticationManager(@NonNull BridgeConfig config, @NonNull
            DAO dao) {
        checkNotNull(config);
        checkNotNull(dao);

        this.config = config;
        this.dao = dao;
        initApiClientProvider();

        this.authenticationApi = apiClientProvider.getClient(AuthenticationApi.class);
        config.getApplicationContext()
                .registerComponentCallbacks(new AuthManagerComponentCallback());
    }

    private void initApiClientProvider() {
        this.apiClientProvider = new ApiClientProvider(config.getBaseUrl(),
                config.getUserAgent(),
                config.getAcceptLanguage());
    }

    @AnyThread
    public interface DAO {

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
     * @param email    participant's email
     * @param password participant's password
     * @return notifies of completion or error
     * @see #signUp(SignUp)
     */
    @NonNull
    public Completable signUp(@NonNull final String email, @NonNull final String password) {
        checkNotNull(email);
        checkNotNull(password);

        logger.debug("signUp called with email: " + email);

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
     * @return notifies of completion or error
     */
    @NonNull
    public Completable signUp(@NonNull final SignUp signUp) {
        checkNotNull(signUp);
        checkNotNull(signUp.getEmail());
        checkNotNull(signUp.getPassword());

        logger.debug("signUp called with signUp: " + signUp);

        signUp.study(config.getStudyId())
                .consent(false)
                .status(null);

        return RxUtils.toBodySingle(authenticationApi.signUp(signUp))
                .doOnSuccess(message -> {
                    dao.setEmail(signUp.getEmail());
                    dao.setPassword(signUp.getPassword());
                }).doOnError(throwable -> {
                    dao.setEmail(null);
                    dao.setPassword(null);
                }).toCompletable();
    }

    /**
     * @param email participant's email address
     * @return notifies of completion or error
     */
    @NonNull
    public Completable resendEmailVerification(@NonNull final String email) {
        checkNotNull(email);

        logger.debug("resendEmailVerification called with email: " + email);

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

        logger.debug("signIn called with email: " + email);

        SignIn signIn = new SignIn()
                .study(config.getStudyId())
                .email(email)
                .password(password);

        return RxUtils.toBodySingle(
                authenticationApi.signIn(signIn))
                .doOnSuccess(userSessionInfo -> {
                    dao.setEmail(email);
                    dao.setPassword(password);
                    dao.setUserSessionInfo(userSessionInfo);
                }).doOnError(throwable -> {
                    if (throwable instanceof NotAuthenticatedException) {
                        dao.setPassword(null);
                    }
                });
    }

    /**
     * On success, clears the participant's email, password and session from storage
     *
     * @return notifies of completion or error
     */
    @NonNull
    public Completable signOut() {
        logger.debug("signOut called");

        return RxUtils.toBodySingle(authenticationApi.signOut())
                .doOnSuccess(message -> {
                    dao.setEmail(null);
                    dao.setPassword(null);
                    dao.setUserSessionInfo(null);
                }).toCompletable();
    }

    /**
     * An email will be sent if an account is registered with the configured study.
     *
     * @param email participant's email address
     * @return notifies of completion or error
     */
    @NonNull
    public Completable requestPasswordResetForEmail(@NonNull String email) {
        checkNotNull(email);

        logger.debug("requestPasswordResetForEmail called with email: " + email);

        return RxUtils.toBodySingle(authenticationApi.requestResetPassword(new Email().study
                (config.getStudyId()).email(email))).toCompletable();
    }

    @NonNull
    public DAO getDao() {
        return dao;
    }

    /**
     * Get access to bridge API for currently authenticated client.
     *
     * @return API returns access to bridge that always uses the credentials currently held by
     * AuthenticationManager
     */
    @NonNull
    public ForConsentedUsersApi getApi() {
        logger.debug("getApi called");

        if (proxiedForConsentedUsersApi != null) {
            return proxiedForConsentedUsersApi;
        }

        proxiedForConsentedUsersApi = new ProxiedForConsentedUsersApi(this);

        return proxiedForConsentedUsersApi;
    }

    ForConsentedUsersApi getRawApi() {
        return apiClientProvider.getClient(ForConsentedUsersApi.class, getSignInFromStore());
    }

    /**
     * Get latest session local store if needed.
     *
     * @return Bridge session
     */
    @Nullable
    public UserSessionInfo getUserSessionInfo() {
        logger.debug("getUserSessionInfo called");

        // TODO: a way to distinguish if session is null because we haven't signed on, or if it
        // was invalidated
        UserSessionInfo session = apiClientProvider.getUserSessionInfoProvider()
                .retrieveCachedSession(getSignInFromStore());

        if (session != null) {
            dao.setUserSessionInfo(session);
            return session;
        }

        return dao.getUserSessionInfo();
    }

    /**
     * Call Bridge to get session. Cached session is updated as a side-effect.
     *
     * @return Bridge session
     */
    @NonNull
    public Single<UserSessionInfo> getLatestUserSessionInfo() {
        // no-op call to the participant update API, we'll get a recomputed session
        // session interceptor will update itself with the session in the response
        return RxUtils.toBodySingle(
                getApi().updateUsersParticipantRecord(new StudyParticipant()));
    }

    private SignIn getSignInFromStore() {
        String email = dao.getEmail();
        String password = dao.getPassword();

        return new SignIn()
                .study(config.getStudyId())
                .email(email)
                .password(password);
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
