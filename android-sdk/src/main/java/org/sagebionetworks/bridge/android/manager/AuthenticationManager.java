package org.sagebionetworks.bridge.android.manager;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.os.Build;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;

import org.sagebionetworks.bridge.android.BridgeConfig;
import org.sagebionetworks.bridge.android.manager.dao.AccountDAO;
import org.sagebionetworks.bridge.android.rx.RxHelper;
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
    @NonNull
    private final AuthenticationApi authenticationApi;
    @NonNull
    private final List<AuthenticationEventListener> listeners;
    @NonNull
    private final RxHelper rxHelper;
    @NonNull
    private final AccountManager accountManager;

    private ApiClientProvider apiClientProvider;
    private ProxiedForConsentedUsersApi proxiedForConsentedUsersApi;


    public AuthenticationManager(@NonNull BridgeConfig config, @NonNull ApiClientProvider apiClientProvider,
                                 @NonNull AccountDAO accountDAO, @NonNull RxHelper rxHelper, @NonNull AccountManager accountManager) {
        checkNotNull(config);
        checkNotNull(apiClientProvider);
        checkNotNull(accountDAO);
        checkNotNull(rxHelper);
        checkNotNull(accountManager);

        this.config = config;
        this.accountDAO = accountDAO;
        this.apiClientProvider = apiClientProvider;
        this.rxHelper = rxHelper;
        this.accountManager = accountManager;

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

        disassociateUser();

        return rxHelper.toBodySingle(authenticationApi.signUp(signUp))
                .doOnSuccess(message -> {
                    SignIn signIn = new SignIn()
                            .study(config.getStudyId())
                            .email(signUp.getEmail())
                            .password(signUp.getPassword());

                    StudyParticipant participant = new StudyParticipant();
                    participant.email(signUp.getEmail())
                            .firstName(signUp.getFirstName())
                            .lastName(signUp.getLastName())
                            .externalId(signUp.getExternalId());

                    associateUser(signIn, null, participant);
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

        return rxHelper.toBodySingle(
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

        disassociateUser();

        SignIn signIn = new SignIn()
                .study(config.getStudyId())
                .email(email)
                .password(password);

        return rxHelper.toBodySingle(
                authenticationApi.signIn(signIn))
                .doOnSuccess(userSessionInfo -> {
                    associateUser(
                            signIn,
                            userSessionInfo,
                            // TODO: this should be unnecessary. verify and remove
                            new StudyParticipant()
                                    .email(signIn.getEmail()));
                    for (AuthenticationEventListener listener : listeners) {
                        listener.onSignedIn(email);
                    }
                }).doOnError(throwable -> {
                    // a 412 is a successful signin
                    if (throwable instanceof ConsentRequiredException) {
                        associateUser(
                                signIn,
                                ((ConsentRequiredException) throwable).getSession(),
                                // TODO: this should be unnecessary. verify and remove
                                new StudyParticipant()
                                        .email(signIn.getEmail())
                        );
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

        final String email = accountDAO.getEmail();
        if (email == null) {
            logger.debug("Did not find saved SignIn credentials prior to calling sign out API");
        }

        return rxHelper.toBodySingle(authenticationApi.signOut())
                .doOnSuccess(message -> {
                    disassociateUser();

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

        return rxHelper.toBodySingle(authenticationApi.requestResetPassword(new Email().study
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
        SignIn signIn = getAssociatedUserSignIn();
        if (signIn == null) {
            return apiClientProvider.getClient(ForConsentedUsersApi.class);
        }
        return apiClientProvider.getClient(ForConsentedUsersApi.class, signIn);
    }

    /**
     * @return the email currently associated with auth manager.
     */
    @Nullable
    public String getEmail() {
        return accountDAO.getEmail();
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
                .retrieveCachedSession(getAssociatedUserSignIn());

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
        return rxHelper.toBodySingle(
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
    
    private void associateUser(SignIn signIn, UserSessionInfo userSessionInfo, StudyParticipant studyParticipant) {
        logger.debug("Associating user with email=[{}]", signIn.getEmail());

        Account account = new Account(signIn.getEmail(), config.getAccountType());
        accountManager.addAccountExplicitly(account, signIn.getPassword(), null);

        accountDAO.setEmail(signIn.getEmail());
        accountDAO.setUserSessionInfo(userSessionInfo);
        accountDAO.setStudyParticipant(studyParticipant);
    }

    @Nullable
    private SignIn getAssociatedUserSignIn() {
        String email = getEmail();
        if (Strings.isNullOrEmpty(email)) {
            return null;
        }

        Account account = new Account(email, config.getAccountType());

        String password = accountManager.getPassword(account);

        return new SignIn()
                .study(config.getStudyId())
                .email(email)
                .password(password);
    }

    private void disassociateUser() {
        logger.debug("Disassociating user");

        String email = getEmail();

        if (!Strings.isNullOrEmpty(email)) {

            Account account = new Account(getEmail(), config.getAccountType());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                accountManager.removeAccountExplicitly(account);
            } else {
                accountManager.removeAccount(account, null, null);
            }
        }

        accountDAO.setEmail(null);
        accountDAO.setUserSessionInfo(null);
        accountDAO.setStudyParticipant(null);
    }
}
