package org.sagebionetworks.bridge.android.manager;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.android.manager.auth.AuthenticationManager;
import org.sagebionetworks.bridge.android.util.retrofit.RxUtils;
import org.sagebionetworks.bridge.rest.api.ForConsentedUsersApi;
import org.sagebionetworks.bridge.rest.model.ConsentSignature;
import org.sagebionetworks.bridge.rest.model.ConsentStatus;
import org.sagebionetworks.bridge.rest.model.SharingScope;
import org.sagebionetworks.bridge.rest.model.UserSessionInfo;
import org.sagebionetworks.bridge.rest.model.Withdrawal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Completable;
import rx.Single;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Manage's participant's state.
 */
public class ConsentManager {
    private static final Logger LOG = LoggerFactory.getLogger(ConsentManager.class);
    private final AuthenticationManager authenticationManager;
    private final ForConsentedUsersApi forConsentedUsersApi;

    public ConsentManager(@NonNull AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
        this.forConsentedUsersApi = authenticationManager.getApi();
    }

    /**
     * @param subpopulationGuid guid for the subpopulation of the consent
     * @return true if consent to participate was made for this consent
     */
    public boolean isConsented(@NonNull String subpopulationGuid) {
        checkNotNull(subpopulationGuid);

        ConsentStatus consentStatus = getConsentStatus(subpopulationGuid);
        if (consentStatus == null) {
            return false;
        }
        return consentStatus.getConsented();
    }

    /**
     * @param subpopulationGuid guid for the subpopulation of the consent
     * @return true if the consent to participate was made against the most recently published version of this consent
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
        return userSessionInfo == null ? null : userSessionInfo.getConsentStatuses().get(subpopulationGuid);
    }

    /**
     * @return true if all required consents have been signed
     */
    public boolean isConsented() {
        UserSessionInfo userSessionInfo = authenticationManager.getUserSessionInfo();
        return userSessionInfo == null ? false : userSessionInfo.getConsented();
    }

    /**
     * @return true if all *required* consents have been signed and the versions signed are the most up-to-date versions of those consents
     */
    public boolean isConsentedMostRecent() {
        UserSessionInfo userSessionInfo = authenticationManager.getUserSessionInfo();
        return userSessionInfo == null ? false : userSessionInfo.getSignedMostRecentConsent();
    }

    /**
     * @param subpopulationGuid guid for the subpopulation of the consent
     * @param name              participant's full name
     * @param birthdate         participant's date of birth
     * @param base64Image       participant's signature, encoded
     * @param imageMimeType     mime type of participant's signature
     * @param sharingScope      participant's sharing scope for the study
     * @return completable
     */
    @NonNull
    public Completable giveConsent(@NonNull String subpopulationGuid, @NonNull String name,
                                   @NonNull LocalDate birthdate,
                                   @NonNull String base64Image, @NonNull String imageMimeType,
                                   @Nullable SharingScope sharingScope) {
        checkNotNull(subpopulationGuid);
        checkNotNull(name);
        checkNotNull(birthdate);
        checkNotNull(base64Image);
        checkNotNull(imageMimeType);

        return RxUtils.toBodySingle(forConsentedUsersApi.createConsentSignature
                (subpopulationGuid, new ConsentSignature()
                        .name(name)
                        .birthdate(birthdate)
                        .imageData(base64Image)
                        .imageMimeType(imageMimeType)
                        .scope(sharingScope)))
                .compose(safeGetNewSessionOnSuccess())
                .toCompletable();
    }

    /**
     * Sends a copy of the participant's signed consent to their email address.
     *
     * @param subpopulationGuid guid for the subpopulation of the consent
     * @return completable
     */
    @NonNull
    public Completable emailConsent(@NonNull String subpopulationGuid) {
        checkNotNull(subpopulationGuid);

        return RxUtils.toBodySingle(forConsentedUsersApi.emailConsentAgreement(subpopulationGuid))
                .toCompletable();
    }

    /**
     * @param subpopulationGuid guid for the subpopulation of the consent
     * @return participant's previously given consent
     */
    @NonNull
    public Single<ConsentSignature> getConsentSignature(@NonNull String subpopulationGuid) {
        checkNotNull(subpopulationGuid);

        return RxUtils.toBodySingle(forConsentedUsersApi.getConsentSignature(subpopulationGuid));
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

        return RxUtils.toBodySingle(
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
    private static <T> Single.Transformer<T, T> safeGetNewSessionOnSuccess() {
        return observable -> observable.doOnSuccess(
                message -> {
                    LOG.info("Calling Bridge for latest session");

                    BridgeManagerProvider.getInstance()
                            .getAuthenticationManager()
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
