package org.sagebionetworks.bridge.android.manager;

import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;

import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.android.manager.auth.AuthenticationManager;
import org.sagebionetworks.bridge.rest.api.ForConsentedUsersApi;
import org.sagebionetworks.bridge.rest.model.StudyParticipant;
import org.sagebionetworks.bridge.rest.model.UserSessionInfo;

import rx.Completable;
import rx.Single;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.android.util.retrofit.RxUtils.toBodySingle;

/**
 * Any authenticated user may use this class's methods. The user does not need to have consented to
 * the study in order to manage their participant record.
 */
@AnyThread
public class StudyParticipantManager {

    @NonNull
    private final ForConsentedUsersApi api;

    public StudyParticipantManager(@NonNull AuthenticationManager authenticationManager) {
        checkNotNull(authenticationManager);

        this.api = authenticationManager.getApi();
    }


    /**
     * Update the current user's participant record.
     * <p>
     * Unlike most other calls in this API, you can send partially complete JSON to this endpoint
     * and it will selectively update the participant's record, rather than treating missing
     * properties as an instruction to delete those fields in the record.
     * <p>
     * This means that many existing APIs that sent a single update value, can direct those payloads
     * to this endpoint and they will still work fine.
     *
     * @param studyParticipant Study participant (required)
     * @return session
     */
    @NonNull
    public Single<UserSessionInfo> updateParticipant(@NonNull StudyParticipant studyParticipant) {
        checkNotNull(studyParticipant);

        return toBodySingle(api.updateUsersParticipantRecord(studyParticipant));
    }

    /**
     * @return Current user's participant record
     */
    @NonNull
    public Single<StudyParticipant> getParticipant() {
        return toBodySingle(api.getUsersParticipantRecord());
    }

    /**
     * Make participant data available for download.
     * <p>
     * Request the uploaded data for this user, in a given time range (inclusive). Bridge will
     * asynchronously gather the user's data for the given time range and email a secure link to the
     * participant's registered email address.
     *
     * @param startDate The first day to include in reports that are returned (required)
     * @param endDate   The last day to include in reports that are returned (required)
     * @return completable
     */
    @NonNull
    public Completable emailDataToParticipant(@NonNull LocalDate startDate,
                                              @NonNull LocalDate endDate) {
        checkNotNull(startDate);
        checkNotNull(endDate);

        return toBodySingle(api.emailDataToUser(startDate, endDate)).toCompletable();
    }
}
