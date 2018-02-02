package org.sagebionetworks.bridge.android.manager;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.android.util.retrofit.RxUtils;
import org.sagebionetworks.bridge.rest.api.ForConsentedUsersApi;
import org.sagebionetworks.bridge.rest.model.Survey;

import java.util.concurrent.atomic.AtomicReference;

import rx.Single;

/**
 * Encapsulates Bridge API calls for getting surveys.
 */
public class SurveyManager {
    @NonNull
    private final AtomicReference<AuthenticationManager.AuthStateHolder>
            authStateHolderAtomicReference;

    /**
     * Constructor
     */
    public SurveyManager(AuthenticationManager authenticationManager) {
        this.authStateHolderAtomicReference = authenticationManager.getAuthStateReference();
    }

    /**
     * Gets a survey from Bridge with the given guid and createdOn. If the createdOn is null, we get
     * the latest published version of that survey.
     *
     * @param guid      survey guid, must be non-null
     * @param createdOn survey createdOn, can be null
     * @return the survey identified by the parameters
     */
    @NonNull
    public Single<Survey> getSurvey(@NonNull String guid, @Nullable DateTime createdOn) {
        if (createdOn != null) {
            return RxUtils.toBodySingle(authStateHolderAtomicReference.get().forConsentedUsersApi
                    .getSurvey(guid, createdOn));
        } else {
            return RxUtils.toBodySingle(authStateHolderAtomicReference.get().forConsentedUsersApi
                    .getPublishedSurveyVersion(guid));
        }
    }
}
