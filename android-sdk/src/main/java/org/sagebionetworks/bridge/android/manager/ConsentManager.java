package org.sagebionetworks.bridge.android.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.common.collect.Maps;
import com.google.gson.reflect.TypeToken;

import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.android.manager.auth.AuthenticationManager;
import org.sagebionetworks.bridge.android.util.retrofit.RxUtils;
import org.sagebionetworks.bridge.rest.RestUtils;
import org.sagebionetworks.bridge.rest.api.ForConsentedUsersApi;
import org.sagebionetworks.bridge.rest.model.ConsentSignature;
import org.sagebionetworks.bridge.rest.model.ConsentStatus;
import org.sagebionetworks.bridge.rest.model.SharingScope;
import org.sagebionetworks.bridge.rest.model.UserSessionInfo;
import org.sagebionetworks.bridge.rest.model.Withdrawal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

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
 */
public class ConsentManager {
    private static final Logger LOG = LoggerFactory.getLogger(ConsentManager.class);
    private final AuthenticationManager authenticationManager;
    private final ForConsentedUsersApi forConsentedUsersApi;
    private final DAO dao;

    public ConsentManager(@NonNull AuthenticationManager authenticationManager) {
        // TODO: it shouldn't be simpler to get the context
        this(authenticationManager, new DAO(BridgeManagerProvider.getInstance().getBridgeConfig().getApplicationContext()));
    }

    public ConsentManager(@NonNull AuthenticationManager authenticationManager, DAO dao) {
        this.authenticationManager = authenticationManager;
        this.forConsentedUsersApi = authenticationManager.getApi();
        this.dao = dao;
    }

    public static class DAO {
        public static final String PREFERENCES_FILE = "consents";

        private static final String KEY_CONSENT_MAP = "consent-map";
        private final SharedPreferences sharedPreferences;
        private final ConcurrentMap<String, ConsentSignature> consents;

        public DAO(Context applicationContext) {
            this.sharedPreferences = applicationContext
                    .getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE);
            this.consents = Maps.newConcurrentMap();

            String consentMapJson = sharedPreferences.getString(KEY_CONSENT_MAP, null);
            TypeToken<Map<String, ConsentSignature>> type =
                    new TypeToken<Map<String, ConsentSignature>>() {
                    };
            Map<String, ConsentSignature> consents =
                    RestUtils.GSON.fromJson(consentMapJson, type.getType());
            consents.putAll(consents);
        }

        synchronized void put(String subpopulationGuid, ConsentSignature consentSignature) {
            consents.put(subpopulationGuid, consentSignature);
            persist();
        }

        synchronized void remove(String subpopulationGuid) {
            consents.remove(subpopulationGuid);
            persist();
        }

        private synchronized void persist() {
            String consentMapJson = RestUtils.GSON.toJson(consents);
            sharedPreferences.edit().putString(KEY_CONSENT_MAP, consentMapJson).apply();
        }

        ConsentSignature get(String subpopulationGuid) {
            return consents.get(subpopulationGuid);
        }

        Set<String> list() {
            return consents.keySet();
        }
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

    /**
     * @return true if all required consents have been signed
     */
    public boolean isConsented() {
        UserSessionInfo userSessionInfo = authenticationManager.getUserSessionInfo();
        return userSessionInfo == null ? false : userSessionInfo.getConsented();
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

        final ConsentSignature consentSignature = new ConsentSignature()
                .name(name)
                .birthdate(birthdate)
                .imageData(base64Image)
                .imageMimeType(imageMimeType)
                .scope(sharingScope);

        return storeConsentSignatureLocally(subpopulationGuid, consentSignature)
                .andThen(
                        RxUtils.toBodySingle(forConsentedUsersApi.
                                createConsentSignature(subpopulationGuid, consentSignature))
                                .compose(safeGetNewSessionOnSuccess())
                                .toCompletable()
                                .onErrorComplete(e -> {
                                    LOG.info("Couldn't upload consent to Bridge, " +
                                            "subpopulationGuid: " + subpopulationGuid, e);
                                    return true;
                                }));
    }

    private Completable storeConsentSignatureLocally(@NonNull String subpopulationGuid,
                                                     @NonNull ConsentSignature consentSignature) {
        LOG.debug("Saving consent locally, subpopulationGuid: " + subpopulationGuid);

        return Completable.fromAction(() -> dao.put(subpopulationGuid, consentSignature));
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
