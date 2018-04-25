package org.sagebionetworks.bridge.android.manager;

import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.android.BridgeConfig;
import org.sagebionetworks.bridge.android.manager.dao.AccountDAO;
import org.sagebionetworks.bridge.android.manager.dao.ConsentDAO;
import org.sagebionetworks.bridge.android.util.retrofit.RxUtils;
import org.sagebionetworks.bridge.rest.ApiClientProvider;
import org.sagebionetworks.bridge.rest.UserSessionInfoProvider;
import org.sagebionetworks.bridge.rest.api.AuthenticationApi;
import org.sagebionetworks.bridge.rest.api.ForConsentedUsersApi;
import org.sagebionetworks.bridge.rest.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.rest.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.rest.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Completable;
import rx.Observable;
import rx.Single;
import rx.functions.Func1;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Authentication and authorization for the study participant using the app.
 */
@AnyThread
@Singleton
public class AuthenticationManager implements UserSessionInfoProvider.UserSessionInfoChangeListener {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationManager.class);

    @NonNull
    private final AccountDAO accountDAO;

    @NonNull
    private final ConsentDAO consentDAO;

    @NonNull
    private final BridgeConfig config;

    @NonNull
    private final AuthenticationApi authenticationApi;

    @NonNull
    private final List<AuthenticationEventListener> listeners;

    @NonNull
    private final ApiClientProvider apiClientProvider;

    @NonNull
    private final AtomicReference<AuthStateHolder> authStateHolderAtomicReference;

    /**
     * Immutable wrapper used with AtomicReference.
     */
    public static final class AuthStateHolder {

        @NonNull
        public final ForConsentedUsersApi forConsentedUsersApi;

        @Nullable
        final UserSessionInfoProvider userSessionInfoProvider;

        public AuthStateHolder(@NonNull ForConsentedUsersApi forConsentedUsersApi,
                               @Nullable UserSessionInfoProvider userSessionInfoProvider) {
            this.forConsentedUsersApi = forConsentedUsersApi;
            this.userSessionInfoProvider = userSessionInfoProvider;
        }
    }

    @Inject
    public AuthenticationManager(@NonNull BridgeConfig config,
                                 @NonNull ApiClientProvider apiClientProvider,
                                 @NonNull AccountDAO accountDAO, @NonNull ConsentDAO consentDAO) {
        checkNotNull(config);
        checkNotNull(accountDAO);
        checkNotNull(consentDAO);

        this.config = config;
        this.accountDAO = accountDAO;
        this.consentDAO = consentDAO;

        this.apiClientProvider = apiClientProvider;

        this.authenticationApi = apiClientProvider.getAuthenticationApi();

        this.authStateHolderAtomicReference = new AtomicReference<>
                (createAuthStateFromStoredCredentials());
        listeners = Lists.newArrayList();
    }

    @VisibleForTesting
    @NonNull
    AuthStateHolder createAuthStateFromStoredCredentials() {
        ForConsentedUsersApi forConsentedUsersApi;
        UserSessionInfoProvider userSessionInfoProvider = null;

        ApiClientProvider.AuthenticatedClientProvider provider =
                createAuthenticatedClientProviderFromStoredCredentials();
        if (provider != null) {
            forConsentedUsersApi = provider.getClient(ForConsentedUsersApi.class);
            userSessionInfoProvider = provider.getUserSessionInfoProvider();
        } else {
            //unauthenticated client
            forConsentedUsersApi = apiClientProvider.getClient(ForConsentedUsersApi.class);
        }

        return new AuthStateHolder(forConsentedUsersApi, userSessionInfoProvider);
    }

    @VisibleForTesting
    @Nullable
    ApiClientProvider.AuthenticatedClientProvider
    createAuthenticatedClientProviderFromStoredCredentials() {
        String email = accountDAO.getEmail();

        String phoneRegion = accountDAO.getPhoneRegion();
        String phoneNumber = accountDAO.getPhoneNumber();
        Phone phone = null;
        if (!Strings.isNullOrEmpty(phoneNumber)) {
            phone = new Phone().number(phoneNumber).regionCode(phoneRegion);
        }

        // need either email or phone to identify user
        if (Strings.isNullOrEmpty(email) && phone == null) {
            return null;
        }

        String password = accountDAO.getPassword();
        UserSessionInfo session = accountDAO.getUserSessionInfo();

        // need either password or session to authenticate
        if (Strings.isNullOrEmpty(password) && session == null) {
            return null;
        }

        ApiClientProvider.AuthenticatedClientProviderBuilder builder =
                apiClientProvider
                        .getAuthenticatedClientProviderBuilder()
                        .addUserSessionInfoChangeListener(this)
                        .withPassword(password)
                        .withSession(session)
                        .withEmail(email)
                        .withPhone(phone);

        return builder.build();
    }

    @Override
    public void onChange(UserSessionInfo userSessionInfo) {
        accountDAO.setUserSessionInfo(userSessionInfo);
    }

    /**
     * Basic sign up that fills in the minimal requirements of email and password fields; in
     * general, you would also want to fill in any of the following information available at
     * sign-up time: firstName, lastName, sharingScope, externalId (if used), dataGroups,
     * notifyByEmail, and any custom attributes you've defined for the attributes field.
     *
     * @param email    participant's email
     * @param password optional participant's password. If not provided, participant will receive
     *                 an email for sign in
     * @return notifies of completion or error
     * @see #signUp(SignUp)
     */
    @NonNull
    public Completable signUp(@NonNull final String email, @Nullable final String password) {
        checkNotNull(email);

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

        logger.debug("signUp called with signUp: " + signUp);

        signUp.study(config.getStudyId())
                .consent(false)
                .status(null);

        return RxUtils.toBodySingle(authenticationApi.signUp(signUp))
                .doOnSuccess(message -> {
                    SignIn signIn = new SignIn()
                            .study(config.getStudyId())
                            .email(signUp.getEmail())
                            .password(signUp.getPassword());

                    accountDAO.setEmail(signUp.getEmail());
                    accountDAO.setPassword(signUp.getPassword());
                    Phone phone = signUp.getPhone();
                    if (phone != null) {
                        accountDAO.setPhoneRegion(phone.getRegionCode());
                        accountDAO.setPhoneNumber(phone.getNumber());
                    }

                    authStateHolderAtomicReference.set(
                            createAuthStateFromStoredCredentials()
                    );

                    StudyParticipant participant = new StudyParticipant();
                    participant.email(signUp.getEmail())
                            .firstName(signUp.getFirstName())
                            .lastName(signUp.getLastName())
                            .externalId(signUp.getExternalId());

                    accountDAO.setStudyParticipant(participant);

                })
                .flatMap(message -> {
                    // if this is a password-less sign-up, request sign-in link
                    if (Strings.isNullOrEmpty(signUp.getPassword())) {
                        return RxUtils.toBodySingle(
                                authenticationApi.requestEmailSignIn(
                                        new EmailSignInRequest()
                                                .study(config.getStudyId())
                                                .email(signUp.getEmail())))
                                .doOnSuccess(m ->
                                        logger.debug("Request for email sign-in link succeeded"))
                                .doOnError(t ->
                                        logger.warn("Request for email sign-in link failed: ", t));
                    }
                    return Single.just(message);
                })
                .doOnError(throwable -> {
                    accountDAO.setStudyParticipant(null);
                }).toCompletable();
    }

    /**
     * Signs up just using a phone number. No username/password will be stored at this time.
     * <p>
     *
     * @param phone  participant's phone number.
     *               To prevent accidental improper settings, study will be set to the studyId
     *               provided in the Bridge, consent will be set to false, and account status
     *               will be cleared.
     * @return notifies of completion or error
     */
    @NonNull
    public Completable signUp(@NonNull final Phone phone) {
        checkNotNull(phone);

        logger.debug("signUp called with phone: " + phone);

        SignUp signUp = new SignUp().phone(phone);
        signUp.study(config.getStudyId())
                .consent(false)
                .status(null);

        return RxUtils.toBodySingle(authenticationApi.signUp(signUp))
                .doOnSuccess(message -> {
                    SignIn signIn = new SignIn()
                            .study(config.getStudyId())
                            .email(signUp.getEmail())
                            .password(signUp.getPassword());

                    if (signUp.getEmail() != null) {
                        accountDAO.setEmail(signUp.getEmail());
                    }
                    if (signUp.getPassword() != null) {
                        accountDAO.setPassword(signUp.getPassword());
                    }
                    Phone signUpPhone = signUp.getPhone();
                    if (signUpPhone != null) {
                        accountDAO.setPhoneRegion(signUpPhone.getRegionCode());
                        accountDAO.setPhoneNumber(signUpPhone.getNumber());
                    }

                    authStateHolderAtomicReference.set(
                            createAuthStateFromStoredCredentials()
                    );

                    StudyParticipant participant = new StudyParticipant();
                    participant.email(signUp.getEmail())
                            .firstName(signUp.getFirstName())
                            .lastName(signUp.getLastName())
                            .externalId(signUp.getExternalId());

                    accountDAO.setStudyParticipant(participant);

                })
//                .flatMap(message -> {
//                    // if this is a password-less sign-up, request sign-in link
//                    if (Strings.isNullOrEmpty(signUp.getPassword())) {
//                        return RxUtils.toBodySingle(
//                                authenticationApi.requestEmailSignIn(
//                                        new EmailSignInRequest()
//                                                .study(config.getStudyId())
//                                                .email(signUp.getEmail())))
//                                .doOnSuccess(m ->
//                                        logger.debug("Request for email sign-in link succeeded"))
//                                .doOnError(t ->
//                                        logger.warn("Request for email sign-in link failed: ", t));
//                    }
//                    return Single.just(message);
//                })
                .doOnError(throwable -> {
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
     * Request an email from Bridge server containing a link which will authenticate the
     * participant. The link is used with {@link #signInViaEmailLink(String, String)}
     *
     * @param email participant's email address
     * @return completable which requests sign in via email from Bridge
     */
    @NonNull
    public Completable requestEmailSignIn(@NonNull final String email) {
        checkNotNull(email);

        return RxUtils.toBodySingle(
                authenticationApi.requestEmailSignIn(
                        new EmailSignInRequest()
                                .email(email)
                                .study(config.getStudyId())))
                .doOnSuccess(m -> logger.debug("Email sign in request success: " + m.getMessage()))
                .doOnError(t -> logger.debug("Email sign in request failure", t))
                .toCompletable();
    }

    /**
     * Request an SMS from Bridge server containing a sign in link which will authenticate the participant.
     *
     * @param regionCode  CLDR two-letter region code describing the region in which the phone number was issued.
     * @param phoneNumber the phone number (can be formatted in any way that's useful for end users).
     * @return completable which requests a phone sign in from Bridge
     */
    @NonNull
    public Completable requestPhoneSignIn(@NonNull String regionCode, @NonNull String phoneNumber) {
        checkArgument(!Strings.isNullOrEmpty(regionCode));
        checkArgument(!Strings.isNullOrEmpty(phoneNumber));

        Phone phone = new Phone().regionCode(regionCode).number(phoneNumber);
        return RxUtils.toBodySingle(
                authenticationApi.requestPhoneSignIn(
                        new PhoneSignInRequest()
                                .phone(phone)
                                .study(config.getStudyId())))
                .doOnSuccess(m -> logger.debug("Phone sign in request success: " + m.getMessage()))
                .doOnError(t -> logger.debug("Phone sign in request failure", t))
                .toCompletable();
    }

    /**
     * @param regionCode  CLDR two-letter region code describing the region in which the phone number was issued.
     * @param phoneNumber the phone number (can be formatted in any way that's useful for end users).
     * @param token       phone authentication token
     * @return single which contains an authenticated session from Bridge
     */
    @NonNull
    public Single<UserSessionInfo> signInViaPhoneLink(@NonNull String regionCode, @NonNull String phoneNumber,
                                                      @NonNull String token) {
        checkArgument(!Strings.isNullOrEmpty(regionCode));
        checkArgument(!Strings.isNullOrEmpty(phoneNumber));
        checkArgument(!Strings.isNullOrEmpty(token));

        Phone phone = new Phone().regionCode(regionCode).number(phoneNumber);

        PhoneSignIn phoneSignIn = new PhoneSignIn()
                .study(config.getStudyId())
                .phone(phone)
                .token(token);

        SignIn signIn = new SignIn().phone(phone).study(config.getStudyId());

        return RxUtils.toBodySingle(
                authenticationApi.signInViaPhone(phoneSignIn))
                .compose(signInHelper(signIn))
                .doOnSuccess(session -> logger.debug("Successfully signed in via phone"))
                .doOnError(t -> {
                    logger.debug("Failed to sign in via phone", t);
                });
    }

    @NonNull
    public Single<UserSessionInfo> signInViaEmailLink(@NonNull String email, @NonNull String
            token) {
        checkNotNull(email);
        checkNotNull(token);

        SignIn signIn = new SignIn().email(email).study(config.getStudyId());

        return RxUtils.toBodySingle(
                authenticationApi.signInViaEmail(
                        new EmailSignIn()
                                .email(email)
                                .study(config.getStudyId())
                                .token(token)))
                .compose(signInHelper(signIn))
                .doOnSuccess(session -> logger.debug("Successfully signed in via email"))
                .doOnError(t -> {
                    logger.debug("Failed to sign in via email", t);
                });
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

        String studyId = config.getStudyId();

        SignIn signIn = new SignIn()
                .study(studyId)
                .email(email)
                .password(password);

        return RxUtils.toBodySingle(
                authenticationApi.signIn(signIn))
                .compose(signInHelper(signIn));
    }

    /**
     * Used to transform a raw Bridge signIn single by retrying upload of required consent if it
     * is present locally and setting state on success/failure
     * <p>
     * This will essentially couple sign in and local consent upload if one need uploaded
     * We decided to do this because the app cannot get scheduled activities if the user
     * is not consented.  This puts most apps into a useless state, so BOTH must succeed
     * for the user to be considered successfully signed in.
     *
     * @param signIn signIn credentials
     */
    Single.Transformer<UserSessionInfo, UserSessionInfo> signInHelper(SignIn signIn) {
        final String email = signIn.getEmail();

        return userSessionInfoSingle -> userSessionInfoSingle
                .doOnSubscribe(accountDAO::clear)
                .onErrorResumeNext(t -> {
                    if (t instanceof ConsentRequiredException) {
                        logger.debug("Received ConsentRequiredException, treating as success");
                        return Single.just(((ConsentRequiredException) t).getSession());
                    }
                    return Single.error(t);
                })
                .doOnSuccess(session -> {
                    accountDAO.setEmail(email);
                    accountDAO.setPassword(signIn.getPassword());

                    Phone phone = signIn.getPhone();
                    if (phone != null) {
                        accountDAO.setPhoneRegion(phone.getRegionCode());
                        accountDAO.setPhoneNumber(phone.getNumber());
                    }

                    // we must set here, since we're not receiving session change callbacks until we create an
                    // authenticated retrofit/okhttp client
                    accountDAO.setUserSessionInfo(session);

                    authStateHolderAtomicReference.set(
                            createAuthStateFromStoredCredentials()
                    );

                    accountDAO.setStudyParticipant(
                            new StudyParticipant()
                                    .email(signIn.getEmail()));
                })
                .flatMap(session -> {
                    if (!session.getConsented()) {
                        // look for a missing required consent which we have locally
                        for (Map.Entry<String, ConsentStatus> consentStatusEntry : session
                                .getConsentStatuses().entrySet()) {
                            String subpopulationGuid = consentStatusEntry.getKey();
                            ConsentStatus consentStatus = consentStatusEntry.getValue();

                            // required consent missing on Bridge and present locally
                            if (consentStatus.getRequired()
                                    && !consentStatus.getConsented()
                                    && isConsentedInLocal(subpopulationGuid)) {

                                // upload local consents, fail this sign in if consent upload fails
                                return uploadLocalConsents()
                                        .toSingle();
                            }
                        }
                    }
                    // nothing to do, return the same session
                    return Single.just(session);
                })
                .doOnError(t -> {
                    accountDAO.clear();
                });
    }

    /**
     * Function that transforms a Throwable Observable to an Observable for use in
     * Observable#retryWhen
     * <p>
     * The resulting behavior is that if the first Throwable is a ConsentRequiredException, and
     * locally, the participant is consented, the consent will be uploaded, and upon success, the
     * calling Observable will be resubscribed to (and retried).
     *
     * @return a retry function that will attempt a to upload Consent one time
     */
    Func1<Observable<? extends Throwable>, ? extends Observable<UserSessionInfo>>
    retrySignInForConsentOnce(String subpopulationGuid) {
        return new Func1<Observable<? extends Throwable>, Observable<UserSessionInfo>>() {
            private int retryAttempt = 0;

            @Override
            public Observable<UserSessionInfo> call(Observable<? extends Throwable>
                                                            throwableObservable) {
                return throwableObservable.flatMap(throwable -> {
                    retryAttempt++;

                    if (!(throwable instanceof ConsentRequiredException)
                            || retryAttempt > 1
                            || (!isConsented() && !isConsentedInLocal(subpopulationGuid))) {

                        Observable<UserSessionInfo> obs = Observable.error(throwable);
                        return obs;
                    }
                    return uploadLocalConsents();
                });
            }
        };
    }

    /**
     * On success, clears the participant's email, password and session from storage
     *
     * @return notifies of completion or error
     */
    @NonNull
    public Completable signOut() {
        logger.debug("signOut called");

        String email = accountDAO.getEmail();

        for (AuthenticationEventListener listener : listeners) {
            listener.onSignedOut(email);
        }

        Completable completable = RxUtils.toBodySingle(authenticationApi.signOut()).toCompletable();

        // Clear relevant account information whether call was successful or not
        accountDAO.clear();
        consentDAO.clear();

        // once signOut method is called, prevent usage of API, regardless of success of bridge call
        authStateHolderAtomicReference.set(
                createAuthStateFromStoredCredentials()
        );

        return completable;
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
     * <p>
     * ForConsentedUserApi instances are bound to a specific set of credentials, and changes due to
     * signIn or signOut result in a new ForConsentedUserApi instance.
     * <p>
     * Callers should retain the AtomicReference and retrieve the API for use and should not cache
     * or store an instance of the API itself.
     *
     * @return returns an AtomicReference that returns API instance which updates automatically to
     * use the current credentials bound to this class
     */
    @NonNull
    public AtomicReference<AuthStateHolder> getAuthStateReference() {
        logger.debug("getAuthStateReference called");

        return authStateHolderAtomicReference;
    }

    /**
     * @return the email currently associated with logged in participant
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

        return accountDAO.getUserSessionInfo();
    }

    /**
     * Call Bridge to getConsent session. Cached session is updated as a side-effect.
     *
     * @return Bridge session
     * @deprecated getUserSessionInfo should suffice for most cases
     */
    @NonNull
    @Deprecated
    public Single<UserSessionInfo> getLatestUserSessionInfo() {
        // no-op call to the participant update API, we'll getConsent a recomputed session
        // session interceptor will update itself with the session in the response
        return RxUtils.toBodySingle(
                getAuthStateReference().get().forConsentedUsersApi
                        .updateUsersParticipantRecord(new StudyParticipant()));
    }

    public void addEventListener(AuthenticationEventListener listener) {
        listeners.add(listener);
    }

    public void removeEventListener(AuthenticationEventListener listener) {
        listeners.remove(listener);
    }

    // region Consent

    /**
     * @param subpopulationGuid guid for the subpopulation of the consent
     * @return true if consent to participate was made for this consent
     */
    public boolean isConsented(@NonNull String subpopulationGuid) {
        checkNotNull(subpopulationGuid);

        return isConsentedInSessionOrLocal(getUserSessionInfo(), subpopulationGuid);
    }

    /**
     * @param subpopulationGuid guid for the subpopulation of the consent
     * @return true if the consent to participate was made against the most recently published
     * version of this consent
     */
    public boolean isConsentedMostRecent(@NonNull String subpopulationGuid) {
        checkNotNull(subpopulationGuid);

        ConsentStatus consentStatus = getConsentStatusFromSession(subpopulationGuid);
        if (consentStatus == null) {
            return false;
        }
        return consentStatus.getSignedMostRecentConsent();
    }

    @Nullable
    private ConsentStatus getConsentStatusFromSession(@NonNull String subpopulationGuid) {
        checkNotNull(subpopulationGuid);

        UserSessionInfo userSessionInfo = getUserSessionInfo();
        return userSessionInfo == null ? null : userSessionInfo.getConsentStatuses()
                .get(subpopulationGuid);
    }

    // if the participant's session indicates consent to this subpopulation, use that. otherwise,
    // treat presense of consent in DAO as having consented
    boolean isConsentedInSessionOrLocal(UserSessionInfo session, String subpopulationGuid) {
        ConsentStatus subpopulationStatus = getConsentStatusFromSession(subpopulationGuid);
        if (subpopulationStatus != null && subpopulationStatus.getConsented()) {
            return true;
        }
        return isConsentedInLocal(subpopulationGuid);
    }

    boolean isConsentedInLocal(String subpopulationGuid) {
        return consentDAO.getConsent(subpopulationGuid) != null;
    }

    /**
     * @return true if all required consents have been signed
     */
    public boolean isConsented() {
        UserSessionInfo userSessionInfo = getUserSessionInfo();
        if (userSessionInfo != null) {
            if (userSessionInfo.getConsented()) {
                return true;
            }

            for (String subpopulation : getRequiredConsents(userSessionInfo)) {
                if (!isConsentedInSessionOrLocal(userSessionInfo, subpopulation)) {
                    return false;
                }
            }
            return true;
        }
        // without a user session, we can't determine whether they're consented. this shouldn't
        // happen unless they're logged out of the application
        return false;
    }

    /**
     * @param userSessionInfo the user's session
     * @return set of all subpopulationGuids for which the user's consent is required
     */
    @NonNull
    Set<String> getRequiredConsents(@NonNull UserSessionInfo userSessionInfo) {
        checkNotNull(userSessionInfo);

        Set<String> subpopulations = Sets.newHashSet();

        if (userSessionInfo.getConsentStatuses() == null) {
            return subpopulations;
        }

        for (Map.Entry<String, ConsentStatus> consentStatus
                : userSessionInfo.getConsentStatuses().entrySet()) {
            if (consentStatus.getValue().getRequired()) {
                subpopulations.add(consentStatus.getKey());
            }
        }

        return subpopulations;
    }

    /**
     * @return true if all *required* consents have been signed and the versions signed are the most
     * up-to-date versions of those consents
     */
    public boolean isConsentedMostRecent() {
        UserSessionInfo userSessionInfo = getUserSessionInfo();
        return userSessionInfo == null ? false : userSessionInfo.getSignedMostRecentConsent();
    }

    /**
     * @param subpopulationGuid guid for the subpopulation of the consent (required)
     * @param name              participant's full name (required)
     * @param birthdate         participant's date of birth (required)
     * @param base64Image       participant's signature, encoded
     * @param imageMimeType     mime type of participant's signature
     * @param sharingScope      participant's sharing scope for the study (required)
     * @return completable
     */
    @NonNull
    public Single<UserSessionInfo> giveConsent(@NonNull String subpopulationGuid, @NonNull String
            name,
                                               @NonNull LocalDate birthdate,
                                               @Nullable String base64Image, @Nullable String
                                                       imageMimeType,
                                               @NonNull SharingScope sharingScope) {
        ConsentSignature consent = storeLocalConsent(
                subpopulationGuid,
                name,
                birthdate,
                base64Image,
                imageMimeType,
                sharingScope);

        return uploadConsent(subpopulationGuid, consent);
    }

    private Single<UserSessionInfo> uploadConsent(@NonNull String subpopulationGuid, @NonNull
            ConsentSignature consent) {
        return Single.just(consent)
                .flatMap(consentSignature -> RxUtils.toBodySingle(
                        getAuthStateReference().get().forConsentedUsersApi
                                .createConsentSignature(
                                        subpopulationGuid,
                                        consentSignature))
                        // Make sure the consent info from the user session is updated
                        .doOnError(e ->
                                logger.info("Couldn't upload consent to Bridge, " +
                                        "subpopulationGuid: " + subpopulationGuid, e)
                        )
                );
    }

    public Observable<UserSessionInfo> uploadLocalConsents() {
        Observable<String> subpopulations = Observable.from(consentDAO.listConsents()).cache();
        return subpopulations
                .map(consentDAO::getConsent)
                .zipWith(subpopulations, (consentSignature, subpopulation) ->
                        uploadConsent(subpopulation, consentSignature))
                .flatMap(Single::toObservable);
    }

    /**
     * Stores a consent signature locally.
     *
     * @param subpopulationGuid guid for the subpopulation of the consent (required)
     * @param name              participant's full name (required)
     * @param birthdate         participant's date of birth (required)
     * @param base64Image       participant's signature, encoded
     * @param imageMimeType     mime type of participant's signature
     * @param sharingScope      participant's sharing scope for the study (required)
     * @return the resulting consentSignature
     */
    public ConsentSignature storeLocalConsent(@NonNull String subpopulationGuid, @NonNull String
            name,
                                              @NonNull LocalDate birthdate,
                                              @Nullable String base64Image,
                                              @Nullable String imageMimeType,
                                              @NonNull SharingScope sharingScope) {
        checkNotNull(subpopulationGuid);
        checkNotNull(name);
        checkNotNull(birthdate);
        checkNotNull(sharingScope);

        final ConsentSignature consentSignature = new ConsentSignature()
                .name(name)
                .birthdate(birthdate)
                .imageData(base64Image)
                .imageMimeType(imageMimeType)
                .scope(sharingScope);

        storeLocalConsent(subpopulationGuid, consentSignature);

        return consentSignature;
    }

    /**
     * Stores a consent signature locally. This method allows for saving a partially complete
     * ConsentSignature, i.e. allows null fields.
     *
     * @param subpopulationGuid guid for the subpopulation of the consent (required)
     * @param consentSignature  consent signature object
     */
    public void storeLocalConsent(@NonNull String subpopulationGuid,
                                  @NonNull ConsentSignature consentSignature) {
        checkNotNull(subpopulationGuid);
        checkNotNull(consentSignature);

        logger.debug("Saving consent locally, subpopulationGuid: " + subpopulationGuid);

        consentDAO.putConsent(subpopulationGuid, consentSignature);
    }

    /**
     * @param subpopulationGuid guid for the subpopulation of the consent
     * @return participant's previously given consent
     */
    @NonNull
    public Single<ConsentSignature> getConsent(@NonNull String subpopulationGuid) {
        checkNotNull(subpopulationGuid);

        return RxUtils.toBodySingle(getAuthStateReference().get().forConsentedUsersApi
                .getConsentSignature
                        (subpopulationGuid))
                .onErrorResumeNext(throwable -> {
                    if (throwable instanceof EntityNotFoundException) {
                        return Single.just(consentDAO.getConsent(subpopulationGuid));
                    }
                    return Single.error(throwable);
                });
    }

    /**
     * @param subpopulationGuid guid for the subpopulation of the consent
     * @return participant's previously given consent from local cache
     */
    @Nullable
    public ConsentSignature retrieveLocalConsent(@NonNull String subpopulationGuid) {
        checkNotNull(subpopulationGuid);

        return consentDAO.getConsent(subpopulationGuid);
    }

    /**
     * Withdraws all previous consents.
     *
     * @param reason reason for withdrawal
     * @return completable
     */
    @NonNull
    public Completable withdrawAll(@Nullable String reason) {

        return RxUtils.toBodySingle(
                getAuthStateReference().get().forConsentedUsersApi
                        .withdrawAllConsents(
                                new Withdrawal().reason(reason)
                        ))
                .toCompletable();

    }

    /**
     * Withdraws specified consent
     *
     * @param subpopulationGuid guid for the subpopulation of the consent
     * @param reason            reason for withdrawal
     * @return completable
     */
    @NonNull
    public Completable withdrawConsent(@NonNull String subpopulationGuid, @Nullable String reason) {
        checkNotNull(subpopulationGuid);

        return RxUtils.toBodySingle(
                getAuthStateReference().get().forConsentedUsersApi
                        .withdrawConsentFromSubpopulation
                                (subpopulationGuid, new Withdrawal().reason(reason)))
                .toCompletable();
    }

    public interface AuthenticationEventListener {

        void onSignedOut(String email);

        void onSignedIn(String email);
    }

    //endregion
}
