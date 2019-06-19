package org.sagebionetworks.bridge.android.manager;

import static com.google.common.base.Preconditions.checkNotNull;

import static org.sagebionetworks.bridge.android.util.retrofit.RxUtils.toBodySingle;

import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.android.di.BridgeStudyParticipantScope;
import org.sagebionetworks.bridge.android.manager.dao.AccountDAO;
import org.sagebionetworks.bridge.rest.model.DateRange;
import org.sagebionetworks.bridge.rest.model.ForwardCursorReportDataList;
import org.sagebionetworks.bridge.rest.model.Message;
import org.sagebionetworks.bridge.rest.model.ReportData;
import org.sagebionetworks.bridge.rest.model.ReportDataList;
import org.sagebionetworks.bridge.rest.model.StudyParticipant;
import org.sagebionetworks.bridge.rest.model.UserSessionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nonnull;
import javax.inject.Inject;

import rx.Completable;
import rx.Single;

/**
 * Any authenticated user may use this class's methods. The user does not need to have consented to
 * the study in order to manage their participant record.
 */
@AnyThread
@BridgeStudyParticipantScope
public class ParticipantRecordManager {
    private static final Logger logger = LoggerFactory.getLogger(ParticipantRecordManager.class);

    @NonNull
    private final AccountDAO accountDAO;
    @NonNull
    private final AtomicReference<AuthenticationManager.AuthStateHolder>
            authStateHolderAtomicReference;

    @Inject
    public ParticipantRecordManager(@NonNull AccountDAO accountDAO,
                                    @NonNull AuthenticationManager authenticationManager) {
        this.accountDAO = accountDAO;
        this.authStateHolderAtomicReference = authenticationManager.getAuthStateReference();
    }

    /**
     * @return Get cached information about participant.
     */
    @Nullable
    public StudyParticipant getCachedParticipantRecord() {
        return accountDAO.getStudyParticipant();
    }

    /**
     * Calls Bridge for participant information. Updates local cache of participant.
     *
     * @return Current user's participant record
     */
    @NonNull
    public Single<StudyParticipant> getParticipantRecord() {
        return toBodySingle(authStateHolderAtomicReference.get().forConsentedUsersApi
                .getUsersParticipantRecord(false))
                .doOnSuccess(accountDAO::setStudyParticipant)
                .doOnError(throwable -> logger.error(throwable.getLocalizedMessage()));
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
    public Single<UserSessionInfo> updateParticipantRecord(
            @NonNull StudyParticipant studyParticipant) {
        checkNotNull(studyParticipant);

        return toBodySingle(authStateHolderAtomicReference.get().forConsentedUsersApi
                .updateUsersParticipantRecord(studyParticipant))
                .doOnSuccess(
                        userSessionInfo -> {
                            logger.debug("Successfully updated participant");
                            getParticipantRecord().toCompletable()
                                    .onErrorComplete(e -> {
                                        logger.warn("Could not retrieve updated participant", e);
                                        return true;
                                    });
                        });
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

        return toBodySingle(authStateHolderAtomicReference.get().forConsentedUsersApi
                .sendDataToUser(
                        new DateRange().startDate(startDate).endDate((endDate))
                )).toCompletable();
    }

    /**
     * @return the local tz date the participant created their account
     *         null is returned if the user has not signed in yet
     */
    @Nullable
    public DateTime getParticipantCreatedOn() {
        UserSessionInfo sessionInfo = accountDAO.getUserSessionInfo();

        if (sessionInfo == null) {
            return null;
        }

        DateTime existingCreatedOnServerTimezone = sessionInfo.getCreatedOn();
        if (existingCreatedOnServerTimezone == null) {
            return null;
        }

        // Convert the date to local timezone, the rest of the app uses "DateTime.now()"
        return existingCreatedOnServerTimezone.toDateTime(DateTimeZone.getDefault());
    }

    /**
     * @param reportIdentifier associated with this report data
     * @param reportData to be uploaded to bridge
     * @return a completable that signals when the request is complete
     */
    public Single<Message> saveReportJSON(String reportIdentifier, ReportData reportData) {
        return toBodySingle(authStateHolderAtomicReference.get().forConsentedUsersApi
                .saveParticipantReportRecordsV4(
                        reportIdentifier, reportData
                ));
    }

    /**
     * Helper function to get reports with default page size and offset key
     * @param reportIdentifier for the reports
     * @param startLocalDate to query the reports
     * @param endLocalDate to query the reports
     * @return a Single for carrying out the network request to get reports
     */
    public Single<ReportDataList> getReportsV3(
            @Nonnull String reportIdentifier,
            @Nonnull LocalDate startLocalDate,
            @Nonnull LocalDate endLocalDate) {

        return toBodySingle(authStateHolderAtomicReference.get().forConsentedUsersApi
                .getParticipantReportRecords(reportIdentifier, startLocalDate, endLocalDate))
                .doOnSuccess(scheduleActivityList -> {
                    logger.debug("Got report list");
                })
                .doOnError(throwable -> {
                    logger.error(throwable.getMessage());
                });
    }

    /**
     * @param reportIdentifier for the reports
     * @param startTime to query the reports
     * @param endTime to query the reports
     * @param pageSize for the requests
     * @param offsetKey of the report group, obtained from a previous network call
     * @return a Single for carrying out the network request to get reports
     */
    public Single<ForwardCursorReportDataList> getReportsV4(
            @Nonnull String reportIdentifier,
            @Nonnull DateTime startTime,
            @Nonnull DateTime endTime,
            @Nullable Integer pageSize,
            @Nullable String offsetKey) {

        DateTime requestEndTime = endTime;

        try {
            int startOffset = startTime.getZone().getOffset(startTime);
            int endOffset = endTime.getZone().getOffset(endTime);
            if (startOffset != endOffset) {
                requestEndTime = endTime.toDateTime(DateTimeZone.forOffsetMillis(startOffset));
                logger.warn("Correcting for mismatched offset. startTime: {}, endTime: {}, newEndTime: {}", startTime,
                        endTime, requestEndTime);
            }
        } catch (Exception e) {
            // in case we do something wrong with JodaTime
            return Single.error(e);
        }

        return toBodySingle(authStateHolderAtomicReference.get().forConsentedUsersApi
                .getParticipantReportRecordsV4(reportIdentifier, startTime, requestEndTime, pageSize, offsetKey))
                .doOnSuccess(scheduleActivityList -> {
                    logger.debug("Got report list");
                })
                .doOnError(throwable -> {
                    logger.error(throwable.getMessage());
                });
    }
}