package org.sagebionetworks.bridge.android.manager;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.rest.api.ForConsentedUsersApi;
import org.sagebionetworks.bridge.rest.model.ConsentSignature;
import org.sagebionetworks.bridge.rest.model.ForwardCursorScheduledActivityList;
import org.sagebionetworks.bridge.rest.model.GuidHolder;
import org.sagebionetworks.bridge.rest.model.Message;
import org.sagebionetworks.bridge.rest.model.NotificationRegistration;
import org.sagebionetworks.bridge.rest.model.NotificationRegistrationList;
import org.sagebionetworks.bridge.rest.model.ReportDataList;
import org.sagebionetworks.bridge.rest.model.ReportIndexList;
import org.sagebionetworks.bridge.rest.model.ScheduleList;
import org.sagebionetworks.bridge.rest.model.ScheduledActivity;
import org.sagebionetworks.bridge.rest.model.ScheduledActivityList;
import org.sagebionetworks.bridge.rest.model.StudyParticipant;
import org.sagebionetworks.bridge.rest.model.SubscriptionRequest;
import org.sagebionetworks.bridge.rest.model.SubscriptionStatusList;
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
 * Proxies ForConsentedUserApi calls so it uses retrieves authenticated client from
 * AuthenticationManager
 * to make the call.
 * TODO: push refreshing of credentials into rest-client's authentication handler
 */
class ProxiedForConsentedUsersApi implements ForConsentedUsersApi {
    private final AuthenticationManager authenticationManager;

    ProxiedForConsentedUsersApi(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @Override
    public Call<Message> completeUploadSession(@Path("uploadId") String s) {
        return getRawApi().completeUploadSession(s);
    }

    @Override
    public Call<UserSessionInfo> createConsentSignature(@Path("subpopulationGuid") String s, @Body
            ConsentSignature consentSignature) {
        return getRawApi().createConsentSignature(s, consentSignature);
    }

    @Override
    public Call<GuidHolder> createNotificationRegistration(@Body NotificationRegistration
                                                                   notificationRegistration) {
        return getRawApi().createNotificationRegistration(notificationRegistration);
    }

    @Override
    public Call<Message> deleteNotificationRegistration(@Path("guid") String s) {
        return getRawApi().deleteNotificationRegistration(s);
    }

    @Override
    public Call<Message> emailConsentAgreement(@Path("subpopulationGuid") String s) {
        return getRawApi().emailConsentAgreement(s);
    }

    @Override
    public Call<Message> emailDataToUser(@Query("startDate") LocalDate localDate, @Query
            ("getEndDate") LocalDate localDate1) {
        return getRawApi().emailDataToUser(localDate, localDate1);
    }

    @Override
    public Call<ForwardCursorScheduledActivityList> getActivityHistory(@Path("activityGuid") String activityGuid, @Query("scheduledOnStart") DateTime scheduledOnStart, @Query("scheduledOnEnd") DateTime scheduledOnEnd, @Query("offsetBy") String offsetBy, @Query("pageSize") Long pageSize) {
        return getRawApi().getActivityHistory(activityGuid, scheduledOnStart, scheduledOnEnd, offsetBy, pageSize);
    }

    @Override
    public Call<ConsentSignature> getConsentSignature(@Path("subpopulationGuid") String s) {
        return getRawApi().getConsentSignature(s);
    }

    @Override
    public Call<NotificationRegistration> getNotificationRegistration(@Path("guid") String s) {
        return getRawApi().getNotificationRegistration(s);
    }

    @Override
    public Call<NotificationRegistrationList> getNotificationRegistrations() {
        return getRawApi().getNotificationRegistrations();
    }

    @Override
    public Call<ReportDataList> getParticipantReportRecords(@Path("identifier") String s, @Query
            ("startDate") LocalDate localDate, @Query("getEndDate") LocalDate localDate1) {
        return getRawApi().getParticipantReportRecords(s, localDate, localDate1);
    }

    @Override
    public Call<Survey> getPublishedSurveyVersion(@Path("surveyGuid") String s) {
        return getRawApi().getPublishedSurveyVersion(s);
    }

    @Override
    public Call<ReportIndexList> getReportIndices(@Query("type") String s) {
        return getRawApi().getReportIndices(s);
    }

    @Override
    public Call<ScheduledActivityList> getScheduledActivities(@Query("offset") String s, @Query
            ("daysAhead") Integer integer, @Query("minimumPerSchedule") Integer integer1) {
        return getRawApi().getScheduledActivities(s, integer, integer1);
    }

    @Override
    public Call<ScheduleList> getSchedules() {
        return getRawApi().getSchedules();
    }

    @Override
    public Call<ReportDataList> getStudyReportRecords(@Path("identifier") String s, @Query
            ("startDate") LocalDate localDate, @Query("getEndDate") LocalDate localDate1) {
        return getRawApi().getStudyReportRecords(s, localDate, localDate1);
    }

    @Override
    public Call<Survey> getSurvey(@Path("surveyGuid") String s, @Path("createdOn") DateTime
            dateTime) {
        return getRawApi().getSurvey(s, dateTime);
    }

    @Override
    public Call<SubscriptionStatusList> getTopicSubscriptions(@Path("guid") String guid) {
        return getRawApi().getTopicSubscriptions(guid);
    }

    @Override
    public Call<UploadValidationStatus> getUploadStatus(@Path("uploadId") String s) {
        return getRawApi().getUploadStatus(s);
    }

    @Override
    public Call<StudyParticipant> getUsersParticipantRecord() {
        return getRawApi().getUsersParticipantRecord();
    }

    @Override
    public Call<UploadSession> requestUploadSession(@Body UploadRequest uploadRequest) {
        return getRawApi().requestUploadSession(uploadRequest);
    }

    @Override
    public Call<SubscriptionStatusList> subscribeToTopics(@Path("guid") String guid, @Body
            SubscriptionRequest body) {
        return getRawApi().subscribeToTopics(guid, body);
    }

    @Override
    public Call<GuidHolder> updateNotificationRegistration(@Path("guid") String s, @Body
            NotificationRegistration notificationRegistration) {
        return getRawApi().updateNotificationRegistration(s, notificationRegistration);
    }

    @Override
    public Call<Message> updateScheduledActivities(@Body List<ScheduledActivity> list) {
        return getRawApi().updateScheduledActivities(list);
    }

    @Override
    public Call<UserSessionInfo> updateUsersParticipantRecord(@Body StudyParticipant
                                                                      studyParticipant) {
        return getRawApi().updateUsersParticipantRecord(studyParticipant);
    }

    @Override
    public Call<UserSessionInfo> withdrawAllConsents(@Body Withdrawal withdrawal) {
        return getRawApi().withdrawAllConsents(withdrawal);
    }

    @Override
    public Call<UserSessionInfo> withdrawConsentFromSubpopulation(@Path("subpopulationGuid") String s,
                                                                  @Body Withdrawal withdrawal) {
        return getRawApi().withdrawConsentFromSubpopulation(s, withdrawal);
    }

    private ForConsentedUsersApi getRawApi() {
        return authenticationManager.getRawApi();
    }
}
