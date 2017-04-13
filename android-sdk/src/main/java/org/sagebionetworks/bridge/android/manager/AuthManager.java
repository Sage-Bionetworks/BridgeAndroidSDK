package org.sagebionetworks.bridge.android.manager;

import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.common.collect.Lists;

import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.android.BridgeConfig;
import org.sagebionetworks.bridge.android.manager.dao.AccountDAO;
import org.sagebionetworks.bridge.android.manager.dao.ConsentDAO;
import org.sagebionetworks.bridge.android.util.retrofit.RxUtils;
import org.sagebionetworks.bridge.rest.ApiClientProvider;
import org.sagebionetworks.bridge.rest.api.AuthenticationApi;
import org.sagebionetworks.bridge.rest.api.ForConsentedUsersApi;
import org.sagebionetworks.bridge.rest.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.rest.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.rest.model.ConsentSignature;
import org.sagebionetworks.bridge.rest.model.ConsentStatus;
import org.sagebionetworks.bridge.rest.model.Email;
import org.sagebionetworks.bridge.rest.model.SharingScope;
import org.sagebionetworks.bridge.rest.model.SignIn;
import org.sagebionetworks.bridge.rest.model.SignUp;
import org.sagebionetworks.bridge.rest.model.StudyParticipant;
import org.sagebionetworks.bridge.rest.model.UserSessionInfo;
import org.sagebionetworks.bridge.rest.model.Withdrawal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import rx.Completable;
import rx.Observable;
import rx.Single;
import rx.functions.Func1;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Authentication and authorization for the study participant using the app.
 */
@AnyThread
public class AuthManager {
    private static final Logger logger = LoggerFactory.getLogger(AuthManager.class);

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
    private final ProxiedForConsentedUsersApi proxiedForConsentedUsersApi;

    public AuthManager(@NonNull BridgeConfig config, @NonNull ApiClientProvider apiClientProvider,
                       @NonNull AccountDAO accountDAO, @NonNull ConsentDAO consentDAO) {
        checkNotNull(config);
        checkNotNull(accountDAO);

        this.config = config;
        this.accountDAO = accountDAO;
        this.consentDAO = consentDAO;
        this.apiClientProvider = apiClientProvider;
        this.authenticationApi = apiClientProvider.getClient(AuthenticationApi.class);
        this.proxiedForConsentedUsersApi = new ProxiedForConsentedUsersApi(this);
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

        // store sign in, so we have signIn as a key to retrieve session in case of 412
        accountDAO.setSignIn(signIn);

        return RxUtils.toBodySingle(
                authenticationApi.signIn(signIn))
                .toObservable()
                .retryWhen(retrySignInForCOnsentOnce())
                .toSingle()
                .doOnSuccess(userSessionInfo -> {
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
                        accountDAO.setUserSessionInfo(
                                ((ConsentRequiredException) throwable).getSession());
                        accountDAO.setStudyParticipant(
                                new StudyParticipant()
                                        .email(signIn.getEmail()));
                    } else {
                        accountDAO.setSignIn(null);
                    }
                });
    }

    Func1<? super Observable<? extends Throwable>, ? extends Observable<UserSessionInfo>> retrySignInForCOnsentOnce() {
        return new Func1<Observable<? extends Throwable>, Observable<UserSessionInfo>>() {
            private int retryAttempt = 0;

            @Override
            public Observable<UserSessionInfo> call(Observable<? extends Throwable> throwableObservable) {
                return throwableObservable.flatMap(throwable -> {
                    retryAttempt++;

                    if (!(throwable instanceof ConsentRequiredException)
                            || retryAttempt > 1
                            || !isConsented()) {
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
     * AuthManager
     */
    @NonNull
    public ForConsentedUsersApi getApi() {
        logger.debug("getApi called");

        return proxiedForConsentedUsersApi;
    }

    /**
     * @return api access for currently logged in participant
     */
    @NonNull
    ForConsentedUsersApi getRawApi() {
        SignIn signIn = accountDAO.getSignIn();
        if (signIn == null) {
            return apiClientProvider.getClient(ForConsentedUsersApi.class);
        }
        return apiClientProvider.getClient(ForConsentedUsersApi.class, accountDAO.getSignIn());
    }

    /**
     * @return the email currently associated with logged in participant
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

        ConsentStatus consentStatus = getConsentStatus(subpopulationGuid);
        if (consentStatus == null) {
            return false;
        }
        return consentStatus.getSignedMostRecentConsent();
    }

    @Nullable
    private ConsentStatus getConsentStatus(@NonNull String subpopulationGuid) {
        checkNotNull(subpopulationGuid);

        UserSessionInfo userSessionInfo = getUserSessionInfo();
        return userSessionInfo == null ? null : userSessionInfo.getConsentStatuses()
                .get(subpopulationGuid);
    }

    // if the participant's session indicates consent to this subpopulation, use that. otherwise,
    // treat presense of consent in DAO as having consented
    private boolean isConsentedInSessionOrLocal(UserSessionInfo session, String subpopulationGuid) {
        ConsentStatus subpopulationStatus = getConsentStatus(subpopulationGuid);
        if (subpopulationStatus != null && subpopulationStatus.getConsented()) {
            return true;
        }
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

            // Bridge session doesn't specify any consents
            if (userSessionInfo.getConsentStatuses() == null) {
                return true;
            }

            for (Map.Entry<String, ConsentStatus> consentStatus
                    : userSessionInfo.getConsentStatuses().entrySet()) {
                if (consentStatus.getValue().getRequired()
                        && !isConsentedInSessionOrLocal(userSessionInfo, consentStatus.getKey())) {
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
    public Single<UserSessionInfo> giveConsent(@NonNull String subpopulationGuid, @NonNull String name,
                                               @NonNull LocalDate birthdate,
                                               @Nullable String base64Image, @Nullable String imageMimeType,
                                               @NonNull SharingScope sharingScope) {
        ConsentSignature consent = giveConsentSync(
                subpopulationGuid,
                name,
                birthdate,
                base64Image,
                imageMimeType,
                sharingScope);

        return uploadConsent(subpopulationGuid, consent);
    }

    private Single<UserSessionInfo> uploadConsent(@NonNull String subpopulationGuid, @NonNull ConsentSignature consent) {
        return Single.just(consent)
                .flatMap(consentSignature -> RxUtils.toBodySingle(
                        proxiedForConsentedUsersApi
                                .createConsentSignature(
                                        subpopulationGuid,
                                        consentSignature))
                        .doOnError(e ->
                                logger.info("Couldn't upload consent to Bridge, " +
                                        "subpopulationGuid: " + subpopulationGuid, e)
                        )
                );
    }

    public Observable<UserSessionInfo> uploadLocalConsents() {
        Observable<String> subpopulations = Observable.from(consentDAO.listConsents()).cache();
        return subpopulations
                .map(subpop -> consentDAO.getConsent(subpop))
                .zipWith(subpopulations, (consentSignature, subpopulation) -> {
                    return uploadConsent(subpopulation, consentSignature);
                })
                .flatMap(i -> i.toObservable());
    }

    /**
     * Gives consent synchronously without making network call on current thread. Upload to Bridge
     * will happen in the background, eventually.
     *
     * @param subpopulationGuid guid for the subpopulation of the consent (required)
     * @param name              participant's full name (required)
     * @param birthdate         participant's date of birth (required)
     * @param base64Image       participant's signature, encoded
     * @param imageMimeType     mime type of participant's signature
     * @param sharingScope      participant's sharing scope for the study (required)
     * @return the resulting consentSignature
     */
    public ConsentSignature giveConsentSync(@NonNull String subpopulationGuid, @NonNull String name,
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

        storeConsentSignatureLocally(subpopulationGuid, consentSignature);

        return consentSignature;
    }

    private void storeConsentSignatureLocally(@NonNull String subpopulationGuid,
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
    public Single<ConsentSignature> getConsentSignature(@NonNull String subpopulationGuid) {
        checkNotNull(subpopulationGuid);

        return RxUtils.toBodySingle(proxiedForConsentedUsersApi.getConsentSignature(subpopulationGuid))
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
    public ConsentSignature getConsentSync(@NonNull String subpopulationGuid) {
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
                proxiedForConsentedUsersApi.withdrawAllConsents(
                        new Withdrawal().reason(reason)
                )).compose(safeGetNewSessionOnSuccess())
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
                proxiedForConsentedUsersApi.withdrawConsentFromSubpopulation
                        (subpopulationGuid, new Withdrawal().reason(reason)))
                .compose(safeGetNewSessionOnSuccess())
                .toCompletable();
    }

    /**
     * Used to retrieve recomputed UserSessionInfo from Bridge after completing calls which modify
     * the participant's consent.
     *
     * @param <T> value type
     * @return modified observable
     */
    private <T> Single.Transformer<T, T> safeGetNewSessionOnSuccess() {
        return observable -> observable.doOnSuccess(
                message -> {
                    logger.info("Calling Bridge for latest session");

                    getLatestUserSessionInfo()
                            .toCompletable()
                            .onErrorComplete(e -> {
                                        logger.info("Couldn't update session from Bridge", e);
                                        return true;
                                    }
                            ).await();
                });
    }

    public interface AuthenticationEventListener {
        void onSignedOut(String email);

        void onSignedIn(String email);
    }

    //endregion
}
