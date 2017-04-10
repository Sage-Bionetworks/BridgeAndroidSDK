package org.sagebionetworks.bridge.android.manager;

import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.common.collect.Lists;

import org.sagebionetworks.bridge.android.BridgeConfig;
import org.sagebionetworks.bridge.android.manager.dao.AccountDAO;
import org.sagebionetworks.bridge.android.util.retrofit.RxUtils;
import org.sagebionetworks.bridge.rest.ApiClientProvider;
import org.sagebionetworks.bridge.rest.api.AuthenticationApi;
import org.sagebionetworks.bridge.rest.api.ForConsentedUsersApi;
import org.sagebionetworks.bridge.rest.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.rest.model.Email;
import org.sagebionetworks.bridge.rest.model.SignIn;
import org.sagebionetworks.bridge.rest.model.SignUp;
import org.sagebionetworks.bridge.rest.model.StudyParticipant;
import org.sagebionetworks.bridge.rest.model.UserSessionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

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
    private final AccountDAO accountDAO;
    @NonNull
    private final BridgeConfig config;
    private final AuthenticationApi authenticationApi;
    private final List<AuthenticationEventListener> listeners;


    private ApiClientProvider apiClientProvider;
    private ProxiedForConsentedUsersApi proxiedForConsentedUsersApi;


    public AuthenticationManager(@NonNull BridgeConfig config, @NonNull ApiClientProvider apiClientProvider,
                                 @NonNull AccountDAO accountDAO) {
        checkNotNull(config);
        checkNotNull(accountDAO);

        this.config = config;
        this.accountDAO = accountDAO;
        this.apiClientProvider = apiClientProvider;

        this.authenticationApi = apiClientProvider.getClient(AuthenticationApi.class);
        listeners = Lists.newArrayList();
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
                    accountDAO.setSignIn(
                            new SignIn()
                                    .study(config.getStudyId())
                                    .email(signUp.getEmail())
                                    .password(signUp.getPassword()));

                    StudyParticipant participant = new StudyParticipant();
                    participant.email(signUp.getEmail())
                            .firstName(signUp.getFirstName())
                            .lastName(signUp.getLastName())
                            .externalId(signUp.getExternalId());

                    accountDAO.setStudyParticipant(participant);
                }).doOnError(throwable -> {
                    accountDAO.setSignIn(null);
                    accountDAO.setStudyParticipant(null);
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
     * NotAuthenticatedException is encountered, this could mean the credentials are invalid, or the
     * user has not verified their email.
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
                    accountDAO.setSignIn(signIn);
                    accountDAO.setUserSessionInfo(userSessionInfo);
                    accountDAO.setStudyParticipant(
                            new StudyParticipant()
                                    .email(signIn.getEmail()));
                    for (AuthenticationEventListener listener : listeners) {
                        listener.onSignedIn(email);
                    }
                }).doOnError(throwable -> {
                    // a 412 is a successful signin
                    if (throwable instanceof ConsentRequiredException) {
                        accountDAO.setSignIn(signIn);
                        accountDAO.setUserSessionInfo(
                                ((ConsentRequiredException) throwable).getSession());
                        accountDAO.setStudyParticipant(
                                new StudyParticipant()
                                        .email(signIn.getEmail()));
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

        final String email;

        SignIn signIn = accountDAO.getSignIn();
        if (signIn != null) {
            email = signIn.getEmail();
        } else {
            email = null;
            logger.debug("Did not find saved SignIn credentials prior to calling sign out API");
        }

        return RxUtils.toBodySingle(authenticationApi.signOut())
                .doOnSuccess(message -> {
                    accountDAO.setSignIn(null);
                    accountDAO.setUserSessionInfo(null);
                    accountDAO.setStudyParticipant(null);

                    for (AuthenticationEventListener listener : listeners) {
                        listener.onSignedOut(email);
                    }
                }).toCompletable();
    }

    /**
     * An email will be sent if an account is registered with the configured study.
     *
     * @param email participant's email address
     * @return notifies of completion or error
     */
    @NonNull
    public Completable requestPasswordReset(@NonNull String email) {
        checkNotNull(email);

        logger.debug("requestPasswordReset called with email: " + email);

        return RxUtils.toBodySingle(authenticationApi.requestResetPassword(new Email().study
                (config.getStudyId()).email(email))).toCompletable();
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

    @NonNull
    ForConsentedUsersApi getRawApi() {
        SignIn signIn = accountDAO.getSignIn();
        if (signIn == null) {
            return apiClientProvider.getClient(ForConsentedUsersApi.class);
        }
        return apiClientProvider.getClient(ForConsentedUsersApi.class, accountDAO.getSignIn());
    }

    /**
     * @return the email currently associated with auth manager.
     */
    @Nullable
    public String getEmail() {
        SignIn signIn = accountDAO.getSignIn();
        return signIn == null ? null : signIn.getEmail();
    }

    /**
     * Get latest session from local store. Does not make a service call.
     *
     * @return Bridge session
     */
    @Nullable
    public UserSessionInfo getUserSessionInfo() {
        logger.debug("getUserSessionInfo called");

        // TODO: a way to distinguish if session is null because we haven't signed on, or if it
        // was invalidated
        UserSessionInfo session = apiClientProvider.getUserSessionInfoProvider()
                .retrieveCachedSession(accountDAO.getSignIn());

        if (session != null) {
            accountDAO.setUserSessionInfo(session);
            return session;
        }

        return accountDAO.getUserSessionInfo();
    }

    /**
     * Call Bridge to getConsent session. Cached session is updated as a side-effect.
     *
     * @return Bridge session
     */
    @NonNull
    public Single<UserSessionInfo> getLatestUserSessionInfo() {
        // no-op call to the participant update API, we'll getConsent a recomputed session
        // session interceptor will update itself with the session in the response
        return RxUtils.toBodySingle(
                getApi().updateUsersParticipantRecord(new StudyParticipant()));
    }

    public void addEventListener(AuthenticationEventListener listener) {
        listeners.add(listener);
    }

    public void removeEventListener(AuthenticationEventListener listener) {
        listeners.remove(listener);
    }

    public interface AuthenticationEventListener {
        /**
         * Notification of successful sign out. Called on a worker thread.
         *
         * @param email signed out user
         */
        void onSignedOut(String email);

        /**
         * Notification of successful sign in. Called on a worker thread.
         *
         * @param email signed in user
         */
        void onSignedIn(String email);
    }
}
