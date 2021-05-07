package org.sagebionetworks.bridge.researchstack;

import static com.google.common.base.Preconditions.checkNotNull;

import static org.sagebionetworks.bridge.researchstack.ApiUtils.SUCCESS_DATA_RESPONSE;

import android.annotation.SuppressLint;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimaps;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.sagebionetworks.researchstack.backbone.AppPrefs;
import org.sagebionetworks.researchstack.backbone.DataProvider;
import org.sagebionetworks.researchstack.backbone.DataResponse;
import org.sagebionetworks.researchstack.backbone.ResourceManager;
import org.sagebionetworks.researchstack.backbone.StorageAccess;
import org.sagebionetworks.researchstack.backbone.model.ConsentSignatureBody;
import org.sagebionetworks.researchstack.backbone.model.SchedulesAndTasksModel;
import org.sagebionetworks.researchstack.backbone.model.TaskModel;
import org.sagebionetworks.researchstack.backbone.model.User;
import org.sagebionetworks.researchstack.backbone.result.TaskResult;
import org.sagebionetworks.researchstack.backbone.storage.NotificationHelper;
import org.sagebionetworks.researchstack.backbone.task.Task;
import org.sagebionetworks.researchstack.backbone.ui.ActiveTaskActivity;
import org.sagebionetworks.researchstack.backbone.utils.ObservableUtils;
import org.sagebionetworks.bridge.android.BridgeConfig;
import org.sagebionetworks.bridge.android.manager.AuthenticationManager;
import org.sagebionetworks.bridge.android.manager.BridgeManagerProvider;
import org.sagebionetworks.bridge.android.manager.ParticipantRecordManager;
import org.sagebionetworks.bridge.android.manager.upload.ArchiveUtil;
import org.sagebionetworks.bridge.android.manager.upload.SchemaKey;
import org.sagebionetworks.bridge.data.JsonArchiveFile;
import org.sagebionetworks.bridge.researchstack.survey.SurveyTaskScheduleModel;
import org.sagebionetworks.bridge.researchstack.wrapper.StorageAccessWrapper;
import org.sagebionetworks.bridge.rest.RestUtils;
import org.sagebionetworks.bridge.rest.model.Activity;
import org.sagebionetworks.bridge.rest.model.ConsentSignature;
import org.sagebionetworks.bridge.rest.model.Message;
import org.sagebionetworks.bridge.rest.model.Phone;
import org.sagebionetworks.bridge.rest.model.ScheduledActivity;
import org.sagebionetworks.bridge.rest.model.ScheduledActivityList;
import org.sagebionetworks.bridge.rest.model.ScheduledActivityListV4;
import org.sagebionetworks.bridge.rest.model.SharingScope;
import org.sagebionetworks.bridge.rest.model.SignUp;
import org.sagebionetworks.bridge.rest.model.StudyParticipant;
import org.sagebionetworks.bridge.rest.model.UserSessionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import rx.Completable;
import rx.Observable;
import rx.Single;
import rx.functions.Action0;

/**
 * DataProvider implementation backed by a Bridge study.
 */
public abstract class BridgeDataProvider extends DataProvider {
    private static final Logger logger = LoggerFactory.getLogger(BridgeDataProvider.class);

    public static BridgeDataProvider getInstance() {
        if (!(DataProvider.getInstance() instanceof BridgeDataProvider)) {
            throw new IllegalStateException("This app only works with BridgeDataProvider");
        }
        return  (BridgeDataProvider)DataProvider.getInstance();
    }

    // set in initialize
    protected final TaskHelper taskHelper;
    @NonNull
    protected final StorageAccessWrapper storageAccessWrapper;
    @NonNull
    protected final ResearchStackDAO researchStackDAO;
    @NonNull
    protected final BridgeManagerProvider bridgeManagerProvider;
    @NonNull
    protected final BridgeConfig bridgeConfig;
    @NonNull
    private final AuthenticationManager authenticationManager;
    @NonNull
    private final ParticipantRecordManager participantRecordManager;
    /**
     * The GUID of the last task that was loaded (used in completion)
     */
    protected String lastLoadedTaskGuid = null;

    //used by tests to mock service
    BridgeDataProvider(BridgeManagerProvider bridgeManagerProvider, ResearchStackDAO researchStackDAO,
                       StorageAccessWrapper
                               storageAccessWrapper,
                       TaskHelper taskHelper) {
        this.researchStackDAO = researchStackDAO;
        this.storageAccessWrapper = storageAccessWrapper;
        this.taskHelper = taskHelper;

        this.bridgeManagerProvider = bridgeManagerProvider;

        // convenience accessors
        this.bridgeConfig = bridgeManagerProvider.getBridgeConfig();
        this.authenticationManager = bridgeManagerProvider.getAuthenticationManager();
        this.participantRecordManager = bridgeManagerProvider.getParticipantManager();
    }

    public BridgeDataProvider(@NonNull BridgeManagerProvider bridgeManagerProvider) {
        this.researchStackDAO = new ResearchStackDAO(bridgeManagerProvider.getApplicationContext());
        this.bridgeManagerProvider = bridgeManagerProvider;
        // convenience accessors
        this.bridgeConfig = bridgeManagerProvider.getBridgeConfig();
        this.authenticationManager = bridgeManagerProvider.getAuthenticationManager();
        this.participantRecordManager = bridgeManagerProvider.getParticipantManager();

        this.storageAccessWrapper = new StorageAccessWrapper();

        NotificationHelper notificationHelper = NotificationHelper.
                getInstance(bridgeManagerProvider.getApplicationContext());
        this.taskHelper = createTaskHelper(notificationHelper, storageAccessWrapper,
                bridgeManagerProvider);
    }

    public TaskHelper createTaskHelper(NotificationHelper notif, StorageAccessWrapper wrapper,
                                       BridgeManagerProvider provider) {
        return new TaskHelper(wrapper, ResourceManager.getInstance(), AppPrefs.getInstance(),
                notif, provider);
    }

    @Override
    public Observable<DataResponse> initialize(Context context) {
        logger.debug("Called initialize");

        return SUCCESS_DATA_RESPONSE;
    }

    @NonNull
    @Override
    public String getStudyId() {
        return bridgeConfig.getStudyId();
    }


    //region Consent

    public Completable withdrawAndSignout(Context context, String reason) {
        return withdrawAllConsents(reason).andThen(signOut(context)).toCompletable();
    }

    @NonNull
    @Override
    public Observable<DataResponse> withdrawConsent(Context context, String reason) {
        logger.debug("Called withdrawConsent");

        return withdrawAllConsents(reason).andThen(SUCCESS_DATA_RESPONSE);
    }

    @NonNull
    public Completable withdrawConsent(@NonNull String subpopulationGuid, @Nullable String reason) {
        logger.debug("Called withdrawConsent for subpopulation: " + subpopulationGuid);
        return authenticationManager.withdrawConsent(subpopulationGuid, reason);
    }


    @NonNull
    public Completable withdrawAllConsents(@Nullable String reason) {
        return authenticationManager.withdrawAll(reason);
    }

    /**
     * @return true if participant has consented to all required consents
     */
    @Override
    public boolean isConsented() {
        logger.debug("Called isConsented");
        return authenticationManager.isConsented();
    }

    @NonNull
    public Single<UserSessionInfo> giveConsent(@NonNull String subpopulationGuid, @NonNull
            ConsentSignature consentSignature) {
        return giveConsent(subpopulationGuid,
                consentSignature.getName(),
                consentSignature.getBirthdate(),
                consentSignature.getImageData(),
                consentSignature.getImageMimeType(),
                consentSignature.getScope());
    }

    @NonNull
    public Single<UserSessionInfo> giveConsent(@NonNull String subpopulationGuid,
                                               @NonNull String name,
                                               @NonNull LocalDate birthdate,
                                               @Nullable String base64Image,
                                               @Nullable String imageMimeType,
                                               @NonNull SharingScope sharingScope) {
        logger.debug("Called giveConsent");
        return authenticationManager.giveConsent(subpopulationGuid, name, birthdate, base64Image,
                imageMimeType, sharingScope);
    }


    @NonNull
    public Single<ConsentSignature> getConsent(@NonNull String subpopulation) {
        checkNotNull(subpopulation);
        logger.debug("Called getConsent");

        return authenticationManager.getConsent(subpopulation);
    }

    // TODO: getConsent rid of Consent methods below on the interface. let ConsentManager handle the
    // implementation details and expose leave giveConsent, getConsent, withdrawConsent, and
    // isConsented
    @Nullable
    @Override
    public ConsentSignatureBody loadLocalConsent(Context context) {
        ConsentSignatureBody consent = createConsentSignatureBody(
                authenticationManager.retrieveLocalConsent(getDefaultConsentSubpopulationGuid()));
        logger.debug("loadLocalConsent called, got: " + consent);
        return consent;
    }

    @Override
    @Deprecated
    public void saveConsent(Context context, @NonNull TaskResult consentResult) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void saveLocalConsent(Context context, ConsentSignatureBody signatureBody) {
        logger.debug("Called saveLocalConsent with: " + signatureBody);
        ConsentSignature consentSignature = createConsentSignature(signatureBody);
        saveLocalConsent(consentSignature);
    }

    @VisibleForTesting
    SharingScope toSharingScope(String sharingScope) {
        SharingScope scopeEnum = SharingScope.NO_SHARING;
        for (SharingScope scope : SharingScope.values()) {
            if (scope.toString().equals(sharingScope)) {
                scopeEnum = scope;
            }
        }
        return scopeEnum;
    }

    @Nullable
    @VisibleForTesting
    ConsentSignature createConsentSignature(@Nullable ConsentSignatureBody
                                                    consentSignatureBody) {
        if (consentSignatureBody == null) {
            return null;
        }
        ConsentSignature signature = new ConsentSignature();
        signature.setName(consentSignatureBody.name);
        if (consentSignatureBody.birthdate != null) {
            signature.setBirthdate(LocalDate.fromDateFields(consentSignatureBody.birthdate));
        }
        SharingScope sharingScope = toSharingScope(consentSignatureBody.scope);
        signature.setImageData(consentSignatureBody.imageData);
        signature.setImageMimeType(consentSignatureBody.imageMimeType);
        signature.setScope(sharingScope);
        return signature;
    }

    @Nullable
    @VisibleForTesting
    ConsentSignatureBody createConsentSignatureBody(@Nullable ConsentSignature
                                                            consentSignature) {
        if (consentSignature == null) {
            return null;
        }

        return new ConsentSignatureBody(
                getStudyId(),
                consentSignature.getName(),
                consentSignature.getBirthdate() != null ? consentSignature.getBirthdate().toDate
                        () : null,
                consentSignature.getImageData(),
                consentSignature.getImageMimeType(),
                consentSignature.getScope() != null ? consentSignature.getScope().toString() :
                        null);
    }

    @Override
    @Deprecated
    public void uploadConsent(Context context, @NonNull TaskResult consentResult) {
        throw new UnsupportedOperationException();
    }

    /**
     * @return the default consent subpopulation guid. The first one made on bridge will
     *         be the study id, but any other ones created after that will have a unique id.
     */
    protected String getDefaultConsentSubpopulationGuid() {
        return bridgeConfig.getStudyId();
    }

    private void saveLocalConsent(@NonNull ConsentSignature consentSignature) {
        authenticationManager.storeLocalConsent(
                getDefaultConsentSubpopulationGuid(),
                consentSignature.getName(),
                consentSignature.getBirthdate(),
                consentSignature.getImageData(),
                consentSignature.getImageMimeType(),
                consentSignature.getScope());
    }

    @Override
    public Observable<DataResponse> uploadConsent(Context context, ConsentSignatureBody signature) {
        logger.debug("Called uploadConsent");
        return uploadConsent(bridgeConfig.getStudyId(), createConsentSignature(signature));
    }

    protected Observable<DataResponse> uploadConsent(@NonNull String subpopulationGuid,
                                                     @NonNull ConsentSignature consent) {
        return giveConsent(
                subpopulationGuid,
                consent.getName(),
                consent.getBirthdate(),
                consent.getImageData(),
                consent.getImageMimeType(),
                consent.getScope())
                .flatMapObservable(session -> SUCCESS_DATA_RESPONSE)
                .compose(ObservableUtils.applyDefault());
    }

    //endregion

    //region Account

    @NonNull
    @Override
    public Observable<DataResponse> signUp(@Nullable Context context, @NonNull String email,
                                           @Nullable String username, @Nullable String password) {
        logger.debug("Called signUp");
        // we should pass in data groups, removeConsent roles
        SignUp signUp = new SignUp().study(getStudyId()).email(email).password(password);
        return signUp(signUp);
    }

    @NonNull
    public Observable<DataResponse> signUp(@NonNull SignUp signUp) {
        // saving email to user object should exist elsewhere.
        // Save email to user object.

        return signUp(signUp.getEmail(), signUp.getPassword());
    }

    @NonNull
    public Observable<DataResponse> signUpPhone(@NonNull SignUp signUp) {
        logger.debug("Called signUpPhone");

        return authenticationManager
                .signUp(signUp)
                .andThen(SUCCESS_DATA_RESPONSE);
    }

    @NonNull
    public Observable<DataResponse> signUp(@NonNull String email, @Nullable String password) {
        checkNotNull(email);

        logger.debug("Called signUp");

        return authenticationManager
                .signUp(email, password)
                .andThen(SUCCESS_DATA_RESPONSE);
    }

    @NonNull
    public Observable<DataResponse> signUp(@NonNull Phone phone) {
        checkNotNull(phone);

        logger.debug("Called signUp using phone");

        return authenticationManager
                .signUp(phone)
                .andThen(SUCCESS_DATA_RESPONSE);
    }

    @Override
    public boolean isSignedUp(@Nullable Context context) {
        logger.debug("Called isSignedUp");
        return isSignedUp();
    }

    public boolean isSignedUp() {
        logger.debug("Called isSignedUp");
        return participantRecordManager.getCachedParticipantRecord() != null;
    }

    @Override
    @NonNull
    public Observable<DataResponse> signIn(@Nullable Context context, @NonNull String username,
                                           @NonNull String password) {
        logger.debug("Called signIn");

        return signIn(username, password)
                .andThen(SUCCESS_DATA_RESPONSE);
    }

    @NonNull
    @Override
    public Observable<DataResponse> signInWithExternalId(
            @Nullable Context context, @NonNull String externalId) {
        logger.debug("Called signInWithExternalId");
        String email = bridgeConfig.getEmailForExternalId(externalId);
        String password = bridgeConfig.getPasswordForExternalId(externalId);
        return signIn(email, password).andThen(SUCCESS_DATA_RESPONSE);
    }

    @Override
    public Observable<DataResponse> requestSignInLink(String email) {
        logger.debug("Called requestSignInLink");
        return authenticationManager.requestEmailSignIn(email)
                .andThen(SUCCESS_DATA_RESPONSE);
    }

    @Override
    public Observable<DataResponse> requestPhoneSignIn(String regionCode, String phoneNumber) {
        logger.debug("Called requestSMS");
        return authenticationManager.requestPhoneSignIn(regionCode, phoneNumber).andThen(SUCCESS_DATA_RESPONSE);
    }

    @Override
    public Observable<DataResponse> signInWithEmailAndToken(String email, String token) {
        logger.debug("Called signInWithEmailAndToken");
        return authenticationManager.signInViaEmailLink(email, token)
                .doOnSuccess(session -> bridgeManagerProvider.getAccountDao()
                        .setDataGroups(session.getDataGroups()))
                .toCompletable()
                .andThen(SUCCESS_DATA_RESPONSE);
    }

    @NonNull
    public Single<UserSessionInfo> signInWithPhoneAndToken(@NonNull String regionCode, @NonNull String phoneNumber,
                                               @NonNull String token) {
        return authenticationManager.signInViaPhoneLink(regionCode, phoneNumber, token);
    }

    /**`
     * @param email    the participant's email
     * @param password participant's password
     * @return completion
     * @see DataProvider#signIn(Context, String, String)
     * <p>
     * May fail with ConsentRequiredException, to indicate
     * consent is required.
     * NotAuthenticatedException could indicate the user has not verified their email
     */
    @NonNull
    public Completable signIn(@NonNull String email, @NonNull String password) {
        checkNotNull(email);
        checkNotNull(password);

        logger.debug("Called signIn");

        return authenticationManager
                .signIn(email, password)
                .doOnSuccess(session -> bridgeManagerProvider.getAccountDao()
                        .setDataGroups(session.getDataGroups()))
                .toCompletable().doOnCompleted((Action0) () -> {
                    // TODO: upload pending files
                });
    }

    public boolean isSignedIn() {
        logger.debug("Called isSignedIn");
        return authenticationManager.getUserSessionInfo() != null;
    }

    @Deprecated
    @Override
    public boolean isSignedIn(Context context) {
        return isSignedIn();
    }


    @Override
    public Observable<DataResponse> signOut(Context context) {
        logger.debug("Called signOut");
        Observable<DataResponse> dataResponse = authenticationManager.signOut()
                .andThen(SUCCESS_DATA_RESPONSE);

        // Clear all the parts of the user data whether call is successful or not
        AppPrefs.getInstance().clear();
        StorageAccess.getInstance().removePinCode(context);
        bridgeManagerProvider.getActivityManager().clearDAO();

        return dataResponse;
    }

    @NonNull
    @Override
    public Observable<DataResponse> resendEmailVerification(Context context, @NonNull String
            email) {
        return resendEmailVerification(email).andThen(SUCCESS_DATA_RESPONSE);
    }

    @NonNull
    public Completable resendEmailVerification(@NonNull String email) {
        checkNotNull(email);

        logger.debug("Called resendEmailVerification");

        return authenticationManager.resendEmailVerification(email);
    }

    /**
     * Called to verify the user's email address
     * Behind the scenes this calls signIn with securely stored username and password
     *
     * @param context android context
     * @return Observable of the result of the method, with {@link DataResponse#isSuccess()}
     * returning true if verifyEmail was successful
     */
    @NonNull
    public Observable<DataResponse> verifyEmail(Context context, @NonNull String password) {
        return verifyEmail(checkNotNull(getUserEmail(context)), password).andThen(SUCCESS_DATA_RESPONSE);
    }

    @NonNull
    public Completable verifyEmail(@NonNull String email, @NonNull String password) {
        logger.debug("Called verifyEmail");

        return authenticationManager.signIn(checkNotNull(email), checkNotNull(password)).toCompletable();
    }

    @NonNull
    @Override
    public Observable<DataResponse> forgotPassword(Context context, @NonNull String email) {
        return forgotPassword(email).andThen(SUCCESS_DATA_RESPONSE);
    }

    @NonNull
    public Completable forgotPassword(@NonNull String email) {
        checkNotNull(email);

        logger.debug("Called forgotPassword");

        return authenticationManager
                .requestPasswordReset(email);
    }

    //endregion

    // region Data Groups

    /**
     * Add data groups to this account locally. Note: this does not call the server to update the
     * participant.
     */
    public void addLocalDataGroup(@NonNull String dataGroup) {
        logger.debug("Called addLocalDataGroup for: " + dataGroup);

        bridgeManagerProvider.getAccountDao().addDataGroup(dataGroup);
    }

    /**
     * Returns a list of data groups associated with this account. If there are no data groups,
     * this method returns an empty list.
     */
    @NonNull
    public List<String> getLocalDataGroups() {
        logger.debug("Called getLocalDataGroups");

        return ImmutableList.copyOf(bridgeManagerProvider.getAccountDao().getDataGroups());
    }

    // endregion Data Groups

    //region User

    @Override
    @Nullable
    public User getUser(@Nullable Context context) {
        return researchStackDAO.getUser();
    }

    @Override
    @Nullable
    public void setUser(Context context, User user) {
        researchStackDAO.setUser(user);
    }

    @Nullable
    @Override
    public String getUserEmail(Context context) {
        return authenticationManager.getEmail();
    }

    //endregion

    //region SharingScope

    @Override
    @Nullable
    public String getUserSharingScope(Context context) {
        SharingScope scope = getUserSharingScope();
        return scope == null ? null : scope.toString();
    }

    @Nullable
    public SharingScope getUserSharingScope() {
        logger.debug("Called getUserSharingScope");

        UserSessionInfo session = authenticationManager.getUserSessionInfo();
        if (session == null) {
            return null;
        }
        return session.getSharingScope();
    }

    @Override
    public void setUserSharingScope(Context context, String scope) {
        StudyParticipant participant = new StudyParticipant();
        SharingScope sharingScope = RestUtils.GSON.fromJson(scope, SharingScope.class);
        participant.setSharingScope(sharingScope);

        setUserSharingScope(sharingScope).toBlocking().value();
    }

    @NonNull
    public Single<UserSessionInfo> setUserSharingScope(@Nullable SharingScope scope) {
        logger.debug("Called setUserSharingScope with: " + scope);

        return bridgeManagerProvider.getParticipantManager()
                .updateParticipantRecord((StudyParticipant) new StudyParticipant()
                        .email(authenticationManager.getEmail())
                        .sharingScope(scope))
                .doOnSuccess(session -> bridgeManagerProvider.getAccountDao()
                        .setDataGroups(session.getDataGroups()));
    }

    @NonNull
    public Observable<StudyParticipant> getStudyParticipant() {
        logger.debug("Called getStudyParticipant");

        return bridgeManagerProvider.getParticipantManager().getParticipantRecord()
                .doOnSuccess(participant -> bridgeManagerProvider.getAccountDao()
                        .setDataGroups(participant.getDataGroups()))
                .doOnError(throwable -> {
                    logger.error(throwable.getMessage());
                })
                .toObservable();
    }

    @NonNull
    public Observable<UserSessionInfo> updateStudyParticipant(StudyParticipant studyParticipant) {
        logger.debug("Called updateStudyParticipant");

        return bridgeManagerProvider.getParticipantManager().updateParticipantRecord(studyParticipant)
                .doOnSuccess(session -> bridgeManagerProvider.getAccountDao()
                        .setDataGroups(session.getDataGroups()))
                .toObservable();
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
    public Observable<DataResponse> downloadData(LocalDate startDate,
                                                 LocalDate endDate) {
        logger.debug("Called downloadData");

        return bridgeManagerProvider.getParticipantManager()
                .emailDataToParticipant(startDate, endDate).andThen(SUCCESS_DATA_RESPONSE);
    }

    //endregion

    //region TasksAndSchedules

    public Observable<Message> updateActivity(ScheduledActivity activity) {
        logger.debug("Called updateActivity");

        return bridgeManagerProvider.getActivityManager().updateActivity(activity);
    }

    public Observable<ScheduledActivityListV4> getActivities(DateTime start, DateTime end) {
        logger.debug("Called getActivities");

        return bridgeManagerProvider.getActivityManager().getActivities(start, end)
                .doOnSuccess(scheduleActivityList -> logger.debug("Got scheduled activity list"))
                .doOnError(throwable -> logger.error(throwable.getMessage()))
                .toObservable();
    }

    @NonNull
    @Override
    public Single<SchedulesAndTasksModel> loadTasksAndSchedules(Context context) {
        logger.info("loadTasksAndSchedules()");

        DateTime now = DateTime.now();
        return bridgeManagerProvider.getActivityManager()
                .getActivities(now, now.plusDays(14))
                .map(ScheduledActivityListV4::getItems)
                .map(this::translateActivities);
    }

    private TaskModel loadTaskModel(Context context, SchedulesAndTasksModel.TaskScheduleModel
            task) {
        logger.debug("Called loadTaskModels");

        // cache guid and createdOnDate

        return taskHelper.loadTaskModel(context, task);
    }

    @NonNull
    @Override
    public Single<Task> loadTask(Context context, SchedulesAndTasksModel.TaskScheduleModel task) {
        logger.debug("Called loadTask for: " + task);

        lastLoadedTaskGuid = task.taskGUID;
        // currently we only support task json files, override this method to taskClassName
        return taskHelper.loadTask(context, task);
    }

    public void uploadTaskResult(@NonNull TaskResult taskResult) {
        // TODO: Update/Create TaskNotificationService
        logger.debug("Called uploadTaskResult ");
        // Context currently isn't need, we just need to fit the base interface implementation
        uploadTaskResult(null, taskResult);
    }

    @SuppressLint("RxLeakedSubscription")    // upload should run as long as it needs to, no early unsubscribe
    @Override
    public void uploadTaskResult(Context context, @NonNull TaskResult taskResult) {
        // TODO: Update/Create TaskNotificationService
        logger.debug("Called uploadTaskResult");

        boolean isActivity = false;
        if (taskResult.getTaskDetails().containsKey(ActiveTaskActivity.ACTIVITY_TASK_RESULT_KEY)) {
            Object isActivityObject = taskResult.getTaskDetails().get(ActiveTaskActivity
                    .ACTIVITY_TASK_RESULT_KEY);
            if (isActivityObject instanceof Boolean) {
                isActivity = (Boolean) isActivityObject;
            }
        }

        ScheduledActivity lastLoadedActivity = null;
        if (lastLoadedTaskGuid == null) {
            logger.error("lastLoadedTaskGuid must be set for this task to complete");
            logger.error("The activity or metadata.json will NOT be updated on bridge");
        } else {
            lastLoadedActivity = bridgeManagerProvider
                    .getActivityManager().getLocalActivity(lastLoadedTaskGuid);

            if (lastLoadedActivity == null) {
                lastLoadedActivity = new ScheduledActivity();
            }
            lastLoadedActivity.setGuid(lastLoadedTaskGuid);
            if (taskResult.getStartDate() != null) {
                lastLoadedActivity.setStartedOn(new DateTime(taskResult.getStartDate()));
            }
            if (taskResult.getEndDate() != null) {
                lastLoadedActivity.setFinishedOn(new DateTime(taskResult.getEndDate()));
            }

            bridgeManagerProvider.getActivityManager().updateActivity(lastLoadedActivity).subscribe(message -> {
                logger.info("Update activity success " + message);
            }, throwable -> logger.error(throwable.getLocalizedMessage()));
        }

        JsonArchiveFile metadataFile = null;
        if (lastLoadedActivity != null) {
            metadataFile = ArchiveUtil.createMetaDataFile(lastLoadedActivity, ImmutableList.copyOf(getLocalDataGroups()));
            logger.debug("metadata.json has been successfully created " + metadataFile.toString());
        }

        if (isActivity) {
            String taskId = taskResult.getIdentifier();
            SchemaKey schemaKey = bridgeConfig.getTaskToSchemaMap().get(taskId);

            if (schemaKey != null) {
                taskHelper.uploadActivityResult(
                        schemaKey.getId(), schemaKey.getRevision(),
                        metadataFile, taskResult);
            } else {
                logger.error("No schema key found for task " + taskId +
                        ", falling back to task ID as schema ID");
                taskHelper.uploadActivityResult(taskId, metadataFile, taskResult);
            }
        } else {
            taskHelper.uploadSurveyResult(metadataFile, taskResult);
        }
    }

    @Override
    public abstract void processInitialTaskResult(Context context, TaskResult taskResult);
    //endregion

    @NonNull
    protected SchedulesAndTasksModel translateActivities(@NonNull ScheduledActivityList activityList) {
        return translateActivities(activityList.getItems());
    }

    SchedulesAndTasksModel translateSchedules(@NonNull Collection<Collection<ScheduledActivity>>
                                                      activitiesBySchedule) {
        SchedulesAndTasksModel model = new SchedulesAndTasksModel();
        model.schedules = new ArrayList<>();

        for (Collection<ScheduledActivity> activities : activitiesBySchedule) {
            List<ScheduledActivity> aList = Lists.newArrayList(activities);
            ScheduledActivity temp = aList.get(0);

            SchedulesAndTasksModel.ScheduleModel sm = new SchedulesAndTasksModel.ScheduleModel();
            sm.scheduleType = temp.isPersistent() ? "persistent" : "once";

            DateTime scheduledOn = temp.getScheduledOn();
                sm.scheduledOn = temp.getScheduledOn().toDate();
                sm.tasks = new ArrayList<>();

                model.schedules.add(sm);

                for (ScheduledActivity sa : aList) {
                    Activity activity = sa.getActivity();

                    SchedulesAndTasksModel.TaskScheduleModel tsm;
                    if (activity.getSurvey() != null) {
                        // This is a survey. Use the subclass.
                        SurveyTaskScheduleModel surveyTaskScheduleModel = new SurveyTaskScheduleModel();
                        surveyTaskScheduleModel.surveyGuid = activity.getSurvey().getGuid();
                        surveyTaskScheduleModel.surveyCreatedOn = activity.getSurvey().getCreatedOn();
                        tsm = surveyTaskScheduleModel;
                    } else {
                        // This is a non-survey. Use the base TaskScheduleModel.
                        tsm = new SchedulesAndTasksModel.TaskScheduleModel();
                    }

                    tsm.taskTitle = activity.getLabel();
                    tsm.taskCompletionTime = activity.getLabelDetail();
                    if (activity.getTask() != null) {
                        tsm.taskID = activity.getTask().getIdentifier();
                    }
                    tsm.taskIsOptional = sa.isPersistent();
                    tsm.taskType = activity.getActivityType().toString();
                if (sa.getFinishedOn() != null) {
                    tsm.taskFinishedOn = sa.getFinishedOn().toDate();
                }
                tsm.taskGUID = sa.getGuid();
                sm.tasks.add(tsm);
            }
        }

        return model;
    }

    //
    // NOTE: this is a crude translation and needs to be updated to properly
    //       handle schedules and filters
    @NonNull
    protected SchedulesAndTasksModel translateActivities(@NonNull List<ScheduledActivity>
                                                                 activityList) {
        logger.info("called translateActivities");

        // group activities by day
        return translateSchedules(
                Multimaps.index(
                        activityList,
                        sa -> sa.getScheduledOn().toLocalDate()

                ).asMap().values());
    }

    /**
     * @return the local tz date the participant created their account
     *         null is returned if the user has not signed in yet
     */
    @Nullable
    public DateTime getParticipantCreatedOn() {
        return participantRecordManager.getParticipantCreatedOn();
    }
}
