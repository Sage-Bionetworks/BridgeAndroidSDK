package org.sagebionetworks.bridge.android.manager;

import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.rest.api.ForConsentedUsersApi;
import org.sagebionetworks.bridge.rest.model.StudyParticipant;
import org.sagebionetworks.bridge.rest.model.UserSessionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Completable;
import rx.Single;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.android.util.retrofit.RxUtils.toBodySingle;

/**
 * Any authenticated user may use this class's methods. The user does not need to have consented to
 * the study in order to manage their participant record.
 * <p>
 * TODO: offline syncing
 */
@AnyThread
public class ParticipantManager {
    private static final Logger LOG = LoggerFactory.getLogger(ParticipantManager.class);

    @NonNull
    private final ForConsentedUsersApi api;
    @NonNull
    private final AccountDAO accountDAO;

    public ParticipantManager(@NonNull AuthenticationManager authenticationManager,
                              @NonNull AccountDAO accountDAO) {
        checkNotNull(authenticationManager);
        checkNotNull(accountDAO);

        this.api = authenticationManager.getApi();
        this.accountDAO = accountDAO;
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

        return toBodySingle(api.updateUsersParticipantRecord(studyParticipant)).doOnSuccess(
                userSessionInfo -> {
                    LOG.debug("Successfully updated participant");
                    getLatestParticipant().toCompletable()
                            .onErrorComplete(e -> {
                                LOG.warn("Could not retrieve updated participant", e);
                                return true;
                            });
                });
    }

    /**
     * @return Get cached information about participant.
     */
    @Nullable
    public StudyParticipant getParticipant() {
        return accountDAO.getStudyParticipant();
    }

    /**
     * Calls Bridge for participant information. Updates local cache of participant.
     *
     * @return Current user's participant record
     */
    @NonNull
    public Single<StudyParticipant> getLatestParticipant() {
        return toBodySingle(api.getUsersParticipantRecord())
                .doOnSuccess(studyParticipant -> accountDAO.setStudyParticipant(studyParticipant));
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
