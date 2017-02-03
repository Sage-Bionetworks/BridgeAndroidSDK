package org.sagebionetworks.bridge.android.manager;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.android.util.retrofit.RxUtils;
import org.sagebionetworks.bridge.rest.api.ForConsentedUsersApi;
import org.sagebionetworks.bridge.rest.model.ConsentSignature;
import org.sagebionetworks.bridge.rest.model.SharingScope;
import org.sagebionetworks.bridge.rest.model.Withdrawal;

import rx.Completable;
import rx.Single;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by jyliu on 1/27/2017.
 */
public class ConsentManager {

    private final ForConsentedUsersApi forConsentedUsersApi;

    public ConsentManager(@NonNull ForConsentedUsersApi forConsentedUsersApi) {
        this.forConsentedUsersApi = forConsentedUsersApi;
    }

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
                        .scope(sharingScope))).toCompletable();
    }

    @NonNull
    public Completable emailConsent(@NonNull String subpopulationGuid) {
        checkNotNull(subpopulationGuid);

        return RxUtils.toBodySingle(forConsentedUsersApi.emailConsentAgreement(subpopulationGuid))
                .toCompletable();
    }

    @NonNull
    public Single<ConsentSignature> getConsentSignature(@NonNull String subpopulationGuid) {
        checkNotNull(subpopulationGuid);

        return RxUtils.toBodySingle(forConsentedUsersApi.getConsentSignature(subpopulationGuid));
    }

    @NonNull
    public Completable withdrawAll(@Nullable String reason) {

        return RxUtils.toBodySingle(
                forConsentedUsersApi.withdrawAllConsents(
                        new Withdrawal().reason(reason)
                )).toCompletable();

    }

    @NonNull
    public Completable withdrawConsent(@NonNull String subpopulationGuid, @Nullable String reason) {
        checkNotNull(subpopulationGuid);

        return RxUtils.toBodySingle(forConsentedUsersApi.withdrawConsentFromSubpopulation
                (subpopulationGuid, new Withdrawal()
                        .reason(reason))).toCompletable();
    }
}
