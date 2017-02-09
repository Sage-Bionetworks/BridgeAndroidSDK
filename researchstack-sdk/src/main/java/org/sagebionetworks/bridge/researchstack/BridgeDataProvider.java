package org.sagebionetworks.bridge.researchstack;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.gson.Gson;

import org.joda.time.LocalDate;
import org.researchstack.backbone.DataProvider;
import org.researchstack.backbone.DataResponse;
import org.researchstack.backbone.model.ConsentSignatureBody;
import org.researchstack.backbone.model.SchedulesAndTasksModel;
import org.researchstack.backbone.model.User;
import org.researchstack.backbone.result.StepResult;
import org.researchstack.backbone.result.TaskResult;
import org.researchstack.backbone.task.Task;
import org.researchstack.backbone.ui.step.layout.ConsentSignatureStepLayout;
import org.researchstack.backbone.utils.ObservableUtils;
import org.researchstack.skin.model.TaskModel;
import org.researchstack.skin.task.ConsentTask;
import org.sagebionetworks.bridge.android.BridgeConfig;
import org.sagebionetworks.bridge.android.manager.AuthenticationManager;
import org.sagebionetworks.bridge.android.manager.BridgeManagerProvider;
import org.sagebionetworks.bridge.android.manager.ConsentManager;
import org.sagebionetworks.bridge.rest.RestUtils;
import org.sagebionetworks.bridge.rest.exceptions.BridgeSDKException;
import org.sagebionetworks.bridge.rest.model.ConsentSignature;
import org.sagebionetworks.bridge.rest.model.SharingScope;
import org.sagebionetworks.bridge.rest.model.SignUp;
import org.sagebionetworks.bridge.rest.model.StudyParticipant;
import org.sagebionetworks.bridge.rest.model.UserSessionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Completable;
import rx.Observable;
import rx.Single;
import rx.functions.Action0;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.researchstack.ApiUtils.SUCCESS_DATA_RESPONSE;

/**
 * DataProvider implementation backed by a Bridge study.
 */
public abstract class BridgeDataProvider extends DataProvider {
    private static final Logger logger = LoggerFactory.getLogger(BridgeDataProvider.class);

    protected final Gson gson = RestUtils.GSON;

    // set in initialize
    protected UserLocalStorage userLocalStorage;
    protected ConsentLocalStorage consentLocalStorage;
    protected TaskHelper taskHelper;
    protected UploadHandler uploadHandler;

    private final BridgeManagerProvider bridgeManagerProvider;
    private final BridgeConfig bridgeConfig;
    private final AuthenticationManager authenticationManager;
    private final ConsentManager consentManager;


    //used by tests to mock service
    BridgeDataProvider(UserLocalStorage userLocalStorage,
                       ConsentLocalStorage consentLocalStorage, TaskHelper taskHelper,
                       UploadHandler uploadHandler) {

        this.userLocalStorage = userLocalStorage;
        this.consentLocalStorage = consentLocalStorage;
        this.taskHelper = taskHelper;
        this.uploadHandler = uploadHandler;

        this.bridgeManagerProvider = BridgeManagerProvider.getInstance();

        // convenience accessors
        this.bridgeConfig = bridgeManagerProvider.getBridgeConfig();
        this.authenticationManager = bridgeManagerProvider.getAuthenticationManager();
        this.consentManager = bridgeManagerProvider.getConsentManager();

    }

    public BridgeDataProvider(BridgeManagerProvider bridgeManagerProvider) {
        this.bridgeManagerProvider = bridgeManagerProvider;

        // convenience accessors
        this.bridgeConfig = bridgeManagerProvider.getBridgeConfig();
        this.authenticationManager = bridgeManagerProvider.getAuthenticationManager();
        this.consentManager = bridgeManagerProvider.getConsentManager();

    }


    @Override
    public Observable<DataResponse> initialize(Context context) {
        logger.debug("Called initialize");

        // TODO: data provider

        return SUCCESS_DATA_RESPONSE;
    }

    @Override
    public String getStudyId() {
        return bridgeConfig.getStudyId();
    }

    //region Consent

    @Override
    public Observable<DataResponse> withdrawConsent(Context context, String reason) {
        logger.debug("Called withdrawConsent");
        //TODO: allow withdrawal from specific subpopulation

        return withdrawAllConsents(reason).andThen(SUCCESS_DATA_RESPONSE);
    }

    @NonNull
    public Completable withdrawConsent(@NonNull String subpopulationGuid, @Nullable String reason) {
        return consentManager.withdrawConsent(subpopulationGuid, reason);
    }


    @NonNull
    public Completable withdrawAllConsents(@Nullable String reason) {
        return consentManager.withdrawAll(reason);
    }

    /**
     * @return true if participant has consented to all required consents
     */
    @Override
    public boolean isConsented() {
        logger.debug("Called isConsented");
        return consentManager.isConsented();
    }

    @NonNull
    public Completable giveConsent(@NonNull String subpopulationGuid, @NonNull String name,
                                   @NonNull LocalDate birthdate,
                                   @NonNull String base64Image, @NonNull String imageMimeType,
                                   @Nullable SharingScope sharingScope) {
        return consentManager.giveConsent(subpopulationGuid, name, birthdate, base64Image,
                imageMimeType, sharingScope);
    }

    @NonNull
    public Single<ConsentSignature> getConsent(@NonNull String subpopulation) {
        checkNotNull(subpopulation);

        return consentManager.getConsentSignature(subpopulation);
    }

    // TODO: get rid of Consent methods below on the interface. let ConsentManager handle the
    // implementation details and expose leave giveConsent, getConsent, withdrawConsent, and
    // isConsented
    @Override
    public ConsentSignatureBody loadLocalConsent(Context context) {
        if (consentLocalStorage == null) {
            return null;
        }
        if (!consentLocalStorage.hasConsent()) {
            return null;
        }
        ConsentSignature bridgeSignature = consentLocalStorage.loadConsent();
        ConsentSignatureBody backboneSignature = new ConsentSignatureBody(
                getStudyId(), bridgeSignature.getName(), bridgeSignature.getBirthdate().toDate(),
                bridgeSignature.getImageData(), bridgeSignature.getImageMimeType(), bridgeSignature.getScope().toString());
        return backboneSignature;
    }

    @Override
    public void saveConsent(Context context, TaskResult consentResult) {
        saveLocalConsent(context, createConsentSignatureBody(consentResult));
    }

    @Override
    public void saveLocalConsent(Context context, ConsentSignatureBody signatureBody) {
        ConsentSignature signature = createConsentSignature(signatureBody);
        saveLocalConsent(context, signature);
    }

    protected void saveLocalConsent(Context context, ConsentSignature signature) {
        consentLocalStorage.saveConsent(signature);

        User user = userLocalStorage.loadUser();
        if (user == null) {
            user = new User();
        }

        user.setName(signature.getName());
        LocalDate birthdate = signature.getBirthdate();
        user.setBirthDate(birthdate.toDate());

        userLocalStorage.saveUser(user);
    }

    protected ConsentSignature createConsentSignature(ConsentSignatureBody consentSignatureBody) {
        ConsentSignature signature = new ConsentSignature();
        signature.setName(consentSignatureBody.name);
        signature.setBirthdate(LocalDate.fromDateFields(consentSignatureBody.birthdate));
        signature.setImageData(consentSignatureBody.imageData);
        signature.setImageMimeType(consentSignatureBody.imageMimeType);
        SharingScope sharingScope = SharingScope.NO_SHARING;
        for (SharingScope scope : SharingScope.values()) {
            if (scope.toString().equals(consentSignatureBody.scope)) {
                sharingScope = scope;
            }
        }
        signature.setScope(sharingScope);
        return signature;
    }

    @NonNull
    protected ConsentSignature createConsentSignatureBody(TaskResult consentResult) {
        StepResult<StepResult> formResult =
                (StepResult<StepResult>) consentResult.getStepResult(ConsentTask.ID_FORM);

        String sharingScope = (String) consentResult.getStepResult(ConsentTask.ID_SHARING).getResult();

        String fullName =
                (String) formResult.getResultForIdentifier(ConsentTask.ID_FORM_NAME).getResult();

        Long birthdateInMillis =
                (Long) formResult.getResultForIdentifier(ConsentTask.ID_FORM_DOB).getResult();

        String base64Image = (String) consentResult.getStepResult(ConsentTask.ID_SIGNATURE)
                .getResultForIdentifier(ConsentSignatureStepLayout.KEY_SIGNATURE);

        String signatureDate = (String) consentResult.getStepResult(ConsentTask.ID_SIGNATURE)
                .getResultForIdentifier(ConsentSignatureStepLayout.KEY_SIGNATURE_DATE);

        // Save Consent Information
        // User is not signed in yet, so we need to save consent info to disk for later upload
        return new ConsentSignature().name(fullName).birthdate(new LocalDate(birthdateInMillis)).imageData(base64Image).imageMimeType("image/png").scope(SharingScope.valueOf(sharingScope));
    }

    @Override
    public void uploadConsent(Context context, TaskResult consentResult) {
        uploadConsent(BuildConfig.STUDY_SUBPOPULATION_GUID,
                createConsentSignatureBody(consentResult));
    }

    @Override
    public Observable<DataResponse> uploadConsent(Context context, ConsentSignatureBody signature) {
        return uploadConsent(BuildConfig.STUDY_SUBPOPULATION_GUID, createConsentSignature(signature));
    }

    private Observable<DataResponse> uploadConsent(String subpopulationGuid, ConsentSignature consent) {
        return giveConsent(
                subpopulationGuid,
                consent.getName(),
                consent.getBirthdate(),
                consent.getImageData(),
                consent.getImageMimeType(),
                consent.getScope()).andThen(SUCCESS_DATA_RESPONSE)
                .compose(ObservableUtils.applyDefault())
                .doOnNext(response -> {

                    UserSessionInfo userSessionInfo = userLocalStorage.loadUserSession();
                    userSessionInfo.setConsented(true);
                    userLocalStorage.saveUserSession(userSessionInfo, userLocalStorage.getSignIn());


                    if (consentLocalStorage.hasConsent()) {
                        consentLocalStorage.deleteConsent();
                    }

                });
    }

    //endregion

    //region Account

    @Override
    public Observable<DataResponse> signUp(Context context, String email, String username,
                                           String password) {
        logger.debug("Called signUp");
        // we should pass in data groups, remove roles
        SignUp signUp = new SignUp().study(getStudyId()).email(email).password(password);
        return signUp(signUp);
    }

    public Observable<DataResponse> signUp(SignUp signUp) {
        // saving email to user object should exist elsewhere.
        // Save email to user object.
        User user = userLocalStorage.loadUser();
        if (user == null) {
            user = new User();
        }
        user.setEmail(signUp.getEmail());
        userLocalStorage.saveUser(user);

        return signUp(signUp.getEmail(), signUp.getPassword());
    }

    @NonNull
    public Observable<DataResponse> signUp(@NonNull String email, @NonNull String password) {
        checkNotNull(email);
        checkNotNull(password);

        return authenticationManager
                .signUp(email, password)
                .andThen(SUCCESS_DATA_RESPONSE);
    }

    @Override
    public boolean isSignedUp(@Nullable Context context) {
        if (userLocalStorage == null) {
            return false;
        }
        return userLocalStorage.isSignedUp();
    }

    public boolean isSignedUp() {
        return authenticationManager.getEmail() != null;
    }

    @Override
    @NonNull
    public Observable<DataResponse> signIn(@Nullable Context context, @NonNull String username, @NonNull String password) {
        logger.debug("Called signIn");

        return signIn(username, password)
                .andThen(SUCCESS_DATA_RESPONSE);
    }

    /**
     * @see DataProvider#signIn(Context, String, String)
     * <p>
     * May fail with ConsentRequiredException, to indicate
     * consent is required.
     * NotAuthenticatedException could indicate the user has not verified their email
     * @param email the participant's email
     * @param password participant's password
     * @return completion
     */
    @NonNull
    public Completable signIn(@NonNull String email, @NonNull String password) {
        checkNotNull(email);
        checkNotNull(password);

        return authenticationManager
                .signIn(email, password)
                .toCompletable().doOnCompleted((Action0) () -> {
                    // TODO: upload pending files
                });
    }

    public boolean isSignedIn() {
        return authenticationManager.getEmail() != null;
    }

    @Deprecated
    @Override
    public boolean isSignedIn(Context context) {
        return isSignedIn();
    }


    @Override
    public Observable<DataResponse> signOut(Context context) {
        logger.debug("Called signOut");

        return signOut().andThen(SUCCESS_DATA_RESPONSE)
                .doOnNext(response -> {
                    userLocalStorage.clearUserSession();
                    userLocalStorage.clearSignIn();
                    // we aren't clearing the user, so we still know their email and that they've signed up
                });
    }

    @NonNull
    public Completable signOut() {
        return authenticationManager.signOut();
    }

    @Override
    public Observable<DataResponse> resendEmailVerification(Context context, @NonNull String
            email) {
        return resendEmailVerification(email).andThen(SUCCESS_DATA_RESPONSE);
    }

    @NonNull
    public Completable resendEmailVerification(@NonNull String email) {
        checkNotNull(email);

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
    public Observable<DataResponse> verifyEmail(@Nullable Context context, @NonNull String password) {
        return verifyEmail(getUserEmail(context), password).andThen(SUCCESS_DATA_RESPONSE);
    }

    @NonNull
    public Completable verifyEmail(@NonNull String email, @NonNull String password) {
        checkNotNull(email);
        checkNotNull(password);

        return authenticationManager.signIn(email, password).toCompletable();
    }

    @Override
    public Observable<DataResponse> forgotPassword(Context context, String email) {
        return forgotPassword(email).andThen(SUCCESS_DATA_RESPONSE);
    }

    @NonNull
    public Completable forgotPassword(@NonNull String email) {
        checkNotNull(email);

        return authenticationManager
                .requestPasswordReset(email);
    }

    //endregion

    //region User

    @Override
    @Nullable
    public User getUser(@Nullable Context context) {
        logger.debug("Called getUser");
        if (userLocalStorage == null) {
            return null;
        }
        return userLocalStorage.loadUser();
    }

    @Override
    @Nullable
    public void setUser(Context context, User user) {
        logger.debug("Called getUser");
        userLocalStorage.saveUser(user);
    }

    @Override
    public String getUserEmail(Context context) {
        User user = userLocalStorage.loadUser();
        return user == null ? null : user.getEmail();
    }

    //endregion

    //region SharingScope

    @Override
    @Nullable
    public String getUserSharingScope(Context context) {
        logger.debug("Called getUserSharingScope");

        SharingScope scope = getUserSharingScope();
        return scope == null ? null : scope.toString();
    }

    @Nullable
    public SharingScope getUserSharingScope() {
        UserSessionInfo session = authenticationManager.getUserSessionInfo();
        if (session == null) {
            return null;
        }
        return session.getSharingScope();
    }


    @Override
    public void setUserSharingScope(Context context, String scope) {
        StudyParticipant participant = new StudyParticipant();
        participant.setSharingScope(SharingScope.valueOf(scope));

        setUserSharingScope(scope)
                .doOnSuccess(userSessionInfo -> userLocalStorage
                        .saveUserSession(userSessionInfo, userLocalStorage.getSignIn()))
                .doOnError(throwable -> ApiUtils
                        .handleError(context, ((BridgeSDKException) throwable).getStatusCode()))
                .subscribe();
    }

    @NonNull
    public Single<UserSessionInfo> setUserSharingScope(@Nullable String scope) {

        return bridgeManagerProvider.getParticipantManager()
                .updateParticipant((StudyParticipant) new StudyParticipant()
                        .email(authenticationManager.getEmail())
                        .sharingScope(SharingScope.valueOf(scope)));
    }

    //endregion

    //region TasksAndSchedules

    @Override
    public SchedulesAndTasksModel loadTasksAndSchedules(Context context) {
        // TODO: integrate with bridge
        // forConsentedUsersApi.getSchedules();
        // forConsentedUsersApi.getScheduledActivities();

        return taskHelper.loadTasksAndSchedules(context);
    }

    private TaskModel loadTaskModel(Context context, SchedulesAndTasksModel.TaskScheduleModel task) {

        // cache guid and createdOnDate

        return taskHelper.loadTaskModel(context, task);
    }

    @Override
    public Task loadTask(Context context, SchedulesAndTasksModel.TaskScheduleModel task) {
        // currently we only support task json files, override this method to taskClassName

        return taskHelper.loadTask(context, task);
    }

    @Override
    public void uploadTaskResult(Context context, TaskResult taskResult) {
        // TODO: Update/Create TaskNotificationService
        // FIXME: Use ArchiveInfo and UploadManager
        taskHelper.uploadTaskResult(context, authenticationManager.getApi(), taskResult);
    }

    @Override
    public abstract void processInitialTaskResult(Context context, TaskResult taskResult);
    //endregion
}
