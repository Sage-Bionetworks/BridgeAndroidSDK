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
 * Created by jyliu on 1/20/2017.
 */
@AnyThread
public class StudyParticipantManager {

    @NonNull
    private final ForConsentedUsersApi api;

    public StudyParticipantManager(@NonNull AuthenticationManager authenticationManager) {
        checkNotNull(authenticationManager);

        this.api = authenticationManager.getApi();
    }

    @NonNull
    public Single<UserSessionInfo> updateParticipant(@NonNull StudyParticipant studyParticipant) {
        checkNotNull(studyParticipant);

        return toBodySingle(api.updateUsersParticipantRecord(studyParticipant));
    }

    @NonNull
    public Single<StudyParticipant> getParticipant() {
        return toBodySingle(api.getUsersParticipantRecord());
    }

    @NonNull
    public Completable emailDataToParticipant(LocalDate startDate, LocalDate endDate) {
        return toBodySingle(api.emailDataToUser(startDate, endDate)).toCompletable();
    }
}
