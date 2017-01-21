package org.sagebionetworks.bridge.android.manager.auth;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.rest.api.ForConsentedUsersApi;
import org.sagebionetworks.bridge.rest.model.ConsentSignature;
import org.sagebionetworks.bridge.rest.model.Message;
import org.sagebionetworks.bridge.rest.model.NotificationRegistration;
import org.sagebionetworks.bridge.rest.model.NotificationRegistrationList;
import org.sagebionetworks.bridge.rest.model.ReportDataList;
import org.sagebionetworks.bridge.rest.model.ReportIndexList;
import org.sagebionetworks.bridge.rest.model.ScheduleList;
import org.sagebionetworks.bridge.rest.model.ScheduledActivity;
import org.sagebionetworks.bridge.rest.model.ScheduledActivityList;
import org.sagebionetworks.bridge.rest.model.StudyParticipant;
import org.sagebionetworks.bridge.rest.model.Survey;
import org.sagebionetworks.bridge.rest.model.UploadRequest;
import org.sagebionetworks.bridge.rest.model.UploadSession;
import org.sagebionetworks.bridge.rest.model.UploadValidationStatus;
import org.sagebionetworks.bridge.rest.model.UserSessionInfo;
import org.sagebionetworks.bridge.rest.model.Withdrawal;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Proxies ForConsentedUserApi calls so it uses retrieves authenticated client from AuthManager
 * to make the call.
 */
class ProxiedForConsentedUsersApi implements ForConsentedUsersApi {
    private final AuthManager authManager;

    ProxiedForConsentedUsersApi(AuthManager authManager) {
        this.authManager = authManager;
    }

    @Override
    public Call<UploadSession> completeUploadSession(@Path("uploadId") String s) {
        return authManager.getApi().completeUploadSession(s);
    }

    @Override
    public Call<Message> createConsentSignature(@Path("subpopulationGuid") String s, @Body
            ConsentSignature consentSignature) {
        return authManager.getApi().createConsentSignature(s, consentSignature);
    }

    @Override
    public Call<Message> createNotificationRegistration(@Body NotificationRegistration
                                                                notificationRegistration) {
        return authManager.getApi().createNotificationRegistration(notificationRegistration);
    }

    @Override
    public Call<Message> deleteNotificationRegistration(@Path("guid") String s) {
        return authManager.getApi().deleteNotificationRegistration(s);
    }

    @Override
    public Call<Message> emailConsentAgreement(@Path("subpopulationGuid") String s) {
        return authManager.getApi().emailConsentAgreement(s);
    }

    @Override
    public Call<Message> emailDataToUser(@Query("startDate") LocalDate localDate, @Query
            ("endDate") LocalDate localDate1) {
        return authManager.getApi().emailDataToUser(localDate, localDate1);
    }

    @Override
    public Call<ConsentSignature> getConsentSignature(@Path("subpopulationGuid") String s) {
        return authManager.getApi().getConsentSignature(s);
    }

    @Override
    public Call<NotificationRegistration> getNotificationRegistration(@Path("guid") String s) {
        return authManager.getApi().getNotificationRegistration(s);
    }

    @Override
    public Call<NotificationRegistrationList> getNotificationRegistrations() {
        return authManager.getApi().getNotificationRegistrations();
    }

    @Override
    public Call<ReportDataList> getParticipantReportRecords(@Path("identifier") String s, @Query
            ("startDate") LocalDate localDate, @Query("endDate") LocalDate localDate1) {
        return authManager.getApi().getParticipantReportRecords(s, localDate, localDate1);
    }

    @Override
    public Call<Survey> getPublishedSurveyVersion(@Path("surveyGuid") String s) {
        return authManager.getApi().getPublishedSurveyVersion(s);
    }

    @Override
    public Call<ReportIndexList> getReportIndices(@Query("type") String s) {
        return authManager.getApi().getReportIndices(s);
    }

    @Override
    public Call<ScheduledActivityList> getScheduledActivities(@Query("offset") String s, @Query
            ("daysAhead") Integer integer, @Query("minimumPerSchedule") Integer integer1) {
        return authManager.getApi().getScheduledActivities(s, integer, integer1);
    }

    @Override
    public Call<ScheduleList> getSchedules() {
        return authManager.getApi().getSchedules();
    }

    @Override
    public Call<ReportDataList> getStudyReportRecords(@Path("identifier") String s, @Query
            ("startDate") LocalDate localDate, @Query("endDate") LocalDate localDate1) {
        return authManager.getApi().getStudyReportRecords(s, localDate, localDate1);
    }

    @Override
    public Call<Survey> getSurvey(@Path("surveyGuid") String s, @Path("createdOn") DateTime
            dateTime) {
        return authManager.getApi().getSurvey(s, dateTime);
    }

    @Override
    public Call<UploadValidationStatus> getUploadStatus(@Path("uploadId") String s) {
        return authManager.getApi().getUploadStatus(s);
    }

    @Override
    public Call<StudyParticipant> getUsersParticipantRecord() {
        return authManager.getApi().getUsersParticipantRecord();
    }

    @Override
    public Call<UploadSession> requestUploadSession(@Body UploadRequest uploadRequest) {
        return authManager.getApi().requestUploadSession(uploadRequest);
    }

    @Override
    public Call<Message> updateNotificationRegistration(@Path("guid") String s, @Body
            NotificationRegistration notificationRegistration) {
        return authManager.getApi().updateNotificationRegistration(s, notificationRegistration);
    }

    @Override
    public Call<Message> updateScheduledActivities(@Body List<ScheduledActivity> list) {
        return authManager.getApi().updateScheduledActivities(list);
    }

    @Override
    public Call<UserSessionInfo> updateUsersParticipantRecord(@Body StudyParticipant
                                                                      studyParticipant) {
        return authManager.getApi().updateUsersParticipantRecord(studyParticipant);
    }

    @Override
    public Call<Message> withdrawAllConsents(@Body Withdrawal withdrawal) {
        return authManager.getApi().withdrawAllConsents(withdrawal);
    }

    @Override
    public Call<Message> withdrawConsentFromSubpopulation(@Path("subpopulationGuid") String s,
                                                          @Body Withdrawal withdrawal) {
        return authManager.getApi().withdrawConsentFromSubpopulation(s, withdrawal);
    }
}
