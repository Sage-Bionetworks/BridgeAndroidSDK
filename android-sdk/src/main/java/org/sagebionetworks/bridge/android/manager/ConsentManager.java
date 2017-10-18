package org.sagebionetworks.bridge.android.manager;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.XmlRes;

import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.android.manager.dao.ConsentDAO;
import org.sagebionetworks.bridge.android.rx.RxHelper;
import org.sagebionetworks.bridge.rest.api.ForConsentedUsersApi;
import org.sagebionetworks.bridge.rest.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.rest.model.ConsentSignature;
import org.sagebionetworks.bridge.rest.model.ConsentStatus;
import org.sagebionetworks.bridge.rest.model.SharingScope;
import org.sagebionetworks.bridge.rest.model.UserSessionInfo;
import org.sagebionetworks.bridge.rest.model.Withdrawal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import rx.Completable;
import rx.Single;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Manage's participant's consent to research.
 * <p>
 * If the participant has not given their consent for all the consents required by the study, Bridge
 * functionality will be limited. Many APIs will return a
 * {@link org.sagebionetworks.bridge.rest.exceptions.ConsentRequiredException} if any required
 * consent has not been granted by the participant.
 * <p>
 * FIXME: handle async upload of consents which are being stored locally
 */
public class ConsentManager {
    private static final Logger LOG = LoggerFactory.getLogger(ConsentManager.class);

@NonNull
    private final AuthenticationManager authenticationManager;
    @NonNull
    private final ForConsentedUsersApi forConsentedUsersApi;
    @NonNull
    private final ConsentDAO consentDAO;
    @NonNull
    private final RxHelper rxHelper;


    public ConsentManager(@NonNull AuthenticationManager authenticationManager, @NonNull ConsentDAO consentDAO,
                          @NonNull RxHelper rxHelper) {
        this.authenticationManager = authenticationManager;
        this.forConsentedUsersApi = authenticationManager.getApi();
        this.consentDAO = consentDAO;
        this.rxHelper = rxHelper;
    }

    /**
     * @param subpopulationGuid guid for the subpopulation of the consent
     * @return true if consent to participate was made for this consent
     */
    public boolean isConsented(@NonNull String subpopulationGuid) {
        checkNotNull(subpopulationGuid);

        return isConsentedInSessionOrLocal(authenticationManager.getUserSessionInfo(), subpopulationGuid);
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

        UserSessionInfo userSessionInfo = authenticationManager.getUserSessionInfo();
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
        UserSessionInfo userSessionInfo = authenticationManager.getUserSessionInfo();
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
        UserSessionInfo userSessionInfo = authenticationManager.getUserSessionInfo();
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
    public Completable giveConsent(@NonNull String subpopulationGuid, @NonNull String name,
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

        return Single.just(consent)
                .flatMapCompletable(consentSignature -> rxHelper.toBodySingle(
                        forConsentedUsersApi
                                .createConsentSignature(
                                        subpopulationGuid,
                                        consentSignature))
                        .doOnError(e ->
                                LOG.info("Couldn't upload consent to Bridge, " +
                                        "subpopulationGuid: " + subpopulationGuid, e))
                        .compose(safeGetNewSessionOnSuccess())
                        .toCompletable()
                );
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

        LOG.debug("Saving consent locally, subpopulationGuid: " + subpopulationGuid);

        consentDAO.putConsent(subpopulationGuid, consentSignature);
    }

    /**
     * @param subpopulationGuid guid for the subpopulation of the consent
     * @return participant's previously given consent
     */
    @NonNull
    public Single<ConsentSignature> getConsentSignature(@NonNull String subpopulationGuid) {
        checkNotNull(subpopulationGuid);

        return rxHelper.toBodySingle(forConsentedUsersApi.getConsentSignature(subpopulationGuid))
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

        return rxHelper.toBodySingle(
                forConsentedUsersApi.withdrawAllConsents(
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

        return rxHelper.toBodySingle(
                forConsentedUsersApi.withdrawConsentFromSubpopulation
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
                    LOG.info("Calling Bridge for latest session");

                    authenticationManager
                            .getLatestUserSessionInfo()
                            .toCompletable()
                            .onErrorComplete(e -> {
                                        LOG.info("Couldn't update session from Bridge", e);
                                        return true;
                                    }
                            ).await();
                });
    }
}
