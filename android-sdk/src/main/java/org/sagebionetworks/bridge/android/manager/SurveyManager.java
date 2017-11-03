package org.sagebionetworks.bridge.android.manager;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import org.joda.time.DateTime;
import org.sagebionetworks.bridge.android.util.retrofit.RxUtils;
import org.sagebionetworks.bridge.rest.api.ForConsentedUsersApi;
import org.sagebionetworks.bridge.rest.model.Survey;
import rx.Single;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Encapsulates Bridge API calls for getting surveys.
 */
public class SurveyManager {
    @NonNull
    private final AtomicReference<ForConsentedUsersApi> apiAtomicReference;

    /**
     * Constructor
     */
    public SurveyManager(AuthenticationManager authenticationManager) {
        this.apiAtomicReference = authenticationManager.getApiReference();
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
            return RxUtils.toBodySingle(apiAtomicReference.get().getSurvey(guid, createdOn));
        } else {
            return RxUtils.toBodySingle(apiAtomicReference.get().getPublishedSurveyVersion(guid));
        }
    }
}
