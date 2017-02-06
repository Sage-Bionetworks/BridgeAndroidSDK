package org.sagebionetworks.bridge.researchstack;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.common.base.Strings;
import com.google.gson.Gson;

import org.joda.time.LocalDate;
import org.researchstack.backbone.DataResponse;
import org.researchstack.backbone.ResourceManager;
import org.researchstack.backbone.ResourcePathManager;
import org.researchstack.backbone.model.ConsentSignatureBody;
import org.researchstack.backbone.model.SchedulesAndTasksModel;
import org.researchstack.backbone.model.User;
import org.researchstack.backbone.result.StepResult;
import org.researchstack.backbone.result.TaskResult;
import org.researchstack.backbone.task.Task;
import org.researchstack.backbone.ui.step.layout.ConsentSignatureStepLayout;
import org.researchstack.backbone.utils.LogExt;
import org.researchstack.backbone.utils.ObservableUtils;
import org.researchstack.skin.AppPrefs;
import org.researchstack.skin.model.TaskModel;
import org.researchstack.skin.task.ConsentTask;
import org.sagebionetworks.bridge.android.manager.BridgeManagerProvider;
import org.sagebionetworks.bridge.researchstack.upload.UploadRequest;
import org.sagebionetworks.bridge.researchstack.wrapper.StorageAccessWrapper;
import org.sagebionetworks.bridge.rest.ApiClientProvider;
import org.sagebionetworks.bridge.rest.RestUtils;
import org.sagebionetworks.bridge.rest.api.AuthenticationApi;
import org.sagebionetworks.bridge.rest.api.ForConsentedUsersApi;
import org.sagebionetworks.bridge.rest.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.rest.model.ConsentSignature;
import org.sagebionetworks.bridge.rest.model.Email;
import org.sagebionetworks.bridge.rest.model.SharingScope;
import org.sagebionetworks.bridge.rest.model.SignIn;
import org.sagebionetworks.bridge.rest.model.SignUp;
import org.sagebionetworks.bridge.rest.model.StudyParticipant;
import org.sagebionetworks.bridge.rest.model.UserSessionInfo;
import org.sagebionetworks.bridge.rest.model.Withdrawal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import rx.Observable;

/*
* This is a very simple implementation that hits only part of the Sage Bridge REST API
* a complete port of the Sage Bridge Java SDK for android: https://github.com/Sage-Bionetworks/BridgeJavaSDK
 */
public abstract class BridgeDataProvider extends BridgeDataProvider2 {
  private static final Logger logger = LoggerFactory.getLogger(BridgeDataProvider.class);

  private final String studyId;
  private final String userAgent;
  private final String baseUrl;
  private final ApiClientProvider apiClientProvider;
  private final ResourcePathManager.Resource publicKey;

  protected final Gson gson = RestUtils.GSON;
  protected final BridgeHeaderInterceptor interceptor;
  protected final StorageAccessWrapper storageAccess;

  // set in initialize
  protected AppPrefs appPrefs;
  protected UserLocalStorage userLocalStorage;
  protected ConsentLocalStorage consentLocalStorage;
  protected TaskHelper taskHelper;
  protected UploadHandler uploadHandler;

  private final AuthenticationApi authenticationApi;

  private ForConsentedUsersApi forConsentedUsersApi;

  //used by tests to mock service
  BridgeDataProvider(String baseUrl, String studyId,
                     String userAgent, ResourcePathManager.Resource publicKey, ApiClientProvider
                             apiClientProvider, AppPrefs appPrefs, StorageAccessWrapper
                             storageAccess, UserLocalStorage userLocalStorage,
                     ConsentLocalStorage consentLocalStorage, TaskHelper taskHelper,
                     UploadHandler uploadHandler) {
    this.interceptor = new BridgeHeaderInterceptor(userAgent, null);
    this.baseUrl = baseUrl;
    this.studyId = studyId;
    this.userAgent = userAgent;
    this.publicKey = publicKey;
    this.appPrefs = appPrefs;
    this.storageAccess = storageAccess;
    this.userLocalStorage = userLocalStorage;
    this.consentLocalStorage = consentLocalStorage;
    this.taskHelper = taskHelper;
    this.uploadHandler = uploadHandler;

    this.apiClientProvider = apiClientProvider;
    this.authenticationApi = apiClientProvider.getClient(AuthenticationApi.class);

    updateBridgeService(null, null);
  }

  /**
   * @param baseUrl base URL of Bridge server
   * @param studyId study identifier
   * @param userAgent user agent, in format expected by Bridge
   * @param publicKey relative path to x.509 certificate for Bridge uploads
   */
  public BridgeDataProvider(String baseUrl, String
          studyId, String userAgent, ResourcePathManager.Resource publicKey) {
    this.interceptor = new BridgeHeaderInterceptor(userAgent, null);
    this.baseUrl = baseUrl;
    this.studyId = studyId;
    this.userAgent = userAgent;
    this.publicKey = publicKey;

    this.apiClientProvider = new ApiClientProvider(baseUrl, userAgent, "en-US");
    this.authenticationApi = apiClientProvider.getClient(AuthenticationApi.class);

    this.storageAccess = new StorageAccessWrapper();
    updateBridgeService(null, null);
  }

  private void updateBridgeService(@Nullable String sessionToken, @Nullable SignIn signIn) {
    if (signIn == null) {
      this.forConsentedUsersApi = null;
    } else {
      this.forConsentedUsersApi = apiClientProvider.getClient(ForConsentedUsersApi.class, signIn);
    }
    interceptor.setSessionToken(sessionToken);
  }

  @Override
  public Observable<DataResponse> initialize(Context context) {
    logger.debug("Called initialize");

    appPrefs = AppPrefs.getInstance(context);
    consentLocalStorage = new ConsentLocalStorage(context, gson, storageAccess.getFileAccess());
    userLocalStorage = new UserLocalStorage(context, gson, storageAccess.getFileAccess());

    this.uploadHandler = new UploadHandler(context, storageAccess, publicKey);
    this.taskHelper = new TaskHelper(storageAccess, ResourceManager.getInstance(), appPrefs,
        uploadHandler);

    return Observable.defer(() -> {
      UserSessionInfo userSessionInfo = userLocalStorage.loadUserSession();
      updateBridgeService(userSessionInfo == null ? null : userSessionInfo.getSessionToken(),
          userLocalStorage.getSignIn());
      return Observable.just(new DataResponse(true, null));
    }).doOnNext(response -> {
      // will crash if the user hasn't created a pincode yet, need to fix needsAuth()
      if (storageAccess.hasPinCode(context)) {
        checkForTempConsentAndUpload();
        uploadHandler.uploadPendingFiles(forConsentedUsersApi);
      }
    });
  }

  @Override
  public String getStudyId() {
    return studyId;
  }

  private void checkForTempConsentAndUpload() {
    // If we are signed in, not consented on the server, but consented locally, upload consent
    if (isSignedIn() && !userLocalStorage.loadUserSession().getConsented()
        && consentLocalStorage.hasConsent()) {
      try {
        ConsentSignature consent = consentLocalStorage.loadConsent();
        uploadConsent(BuildConfig.STUDY_SUBPOPULATION_GUID, consent);
      } catch (Exception e) {
        throw new RuntimeException("Error loading consent", e);
      }
    }
  }

  /**
   * @return true if we are consented
   */
  @Override
  public boolean isConsented() {
    logger.debug("Called isConsented");
    return userLocalStorage.loadUserSession().getConsented() || consentLocalStorage.hasConsent();
  }

  @Override
  public Observable<DataResponse> withdrawConsent(Context context, String reason) {
    logger.debug("Called withdrawConsent");
    //TODO: allow withdrawal from specific subpopulation
    return ApiUtils.toResponseObservable(forConsentedUsersApi.withdrawAllConsents(new Withdrawal().reason(reason)))
        .compose(ObservableUtils.applyDefault())
        .doOnNext(response -> {
          if (response.isSuccessful()) {
            UserSessionInfo userSessionInfo = userLocalStorage.loadUserSession();
            userSessionInfo.setConsented(false);
            userLocalStorage.saveUserSession(userSessionInfo, userLocalStorage.getSignIn());
          } else {
            ApiUtils.handleError(context, response.code());
          }
        })
        .map(response -> new DataResponse(response.isSuccessful(), response.message()));
  }

  @Override
  public Observable<DataResponse> signUp(Context context, String email, String username,
      String password) {
    logger.debug("Called signUp");
    // we should pass in data groups, remove roles
    SignUp signUp = new SignUp().study(studyId).email(email).password(password);
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

    return ApiUtils.toBodyObservable(authenticationApi.signUp(signUp)).map(message -> {
      DataResponse response = new DataResponse();
      response.setSuccess(true);
      return response;
    });
  }

  @Override
  public Observable<DataResponse> signIn(Context context, String username, String password) {
    logger.debug("Called signIn");
    final SignIn signIn = new SignIn().study(studyId).email(username).password(password);

    // response 412 still has a response body, so catch all http errors here
    return ApiUtils.toResponseObservable(authenticationApi.signIn(signIn)).doOnNext(response -> {
      UserSessionInfo userSessionInfo = response.body();
      processSignInResponse(signIn, userSessionInfo);
    }).map(response -> {
      boolean success = response.isSuccessful() || response.code() == 412;
      return new DataResponse(success, response.message());
    }).doOnError(throwable -> {
      if (throwable instanceof ConsentRequiredException) {
        UserSessionInfo userSessionInfo = ((ConsentRequiredException) throwable).getSession();
        processSignInResponse(signIn, userSessionInfo);
      }
    });
  }

  protected void processSignInResponse(SignIn signIn, UserSessionInfo userSessionInfo) {
    logger.debug("Received signIn response");
    logger.debug("signIn userSessionInfo: " + userSessionInfo);

    if (userSessionInfo != null) {
      // if we are direct from signing in, we need to load the user profile object
      // from the server. that wouldn't work right now
      userLocalStorage.saveUserSession(userSessionInfo, signIn);
      updateBridgeService(userSessionInfo.getSessionToken(), signIn);

      // TODO: seems like we would want to wait until the consent is successfully uploaded?
      checkForTempConsentAndUpload();
      uploadHandler.uploadPendingFiles(forConsentedUsersApi);
    }
  }

  @Override
  public Observable<DataResponse> signOut(Context context) {
    logger.debug("Called signOut");
    return ApiUtils.toResponseObservable(authenticationApi.signOut())
        .map(response -> new DataResponse(response.isSuccessful(), response.body().getMessage()))
        .doOnNext(response -> {
          userLocalStorage.clearUserSession();
          userLocalStorage.clearSignIn();
          // we aren't clearing the user, so we still know their email and that they've signed up
        });
  }

  @Override
  public Observable<DataResponse> resendEmailVerification(Context context, String email) {
    return ApiUtils.toBodyObservable(
        authenticationApi.resendEmailVerification(new Email().study(studyId).email(email)))
        .map(response -> new DataResponse(true, null));
  }

  /**
   * Called to verify the user's email address
   * Behind the scenes this calls signIn with securely stored username and password
   *
   * @param context android context
   * @return Observable of the result of the method, with {@link DataResponse#isSuccess()}
   * returning true if verifyEmail was successful
   */
  public Observable<DataResponse> verifyEmail(Context context, String password) {
    User user = getUser(context);
    final String email = user.getEmail();
    return signIn(context, email, password);
  }

  @Override
  public boolean isSignedUp(Context context) {
    if (userLocalStorage == null) {
      return false;
    }
    return userLocalStorage.isSignedUp();
  }

  public boolean isSignedIn() {
    if (userLocalStorage != null) {
      return userLocalStorage.isSignedIn();
    }
    return false;
  }

  @Deprecated
  @Override
  public boolean isSignedIn(Context context) {
    return isSignedIn();
  }

  @Override
  public void saveConsent(Context context, TaskResult consentResult) {
    saveLocalConsent(context, createConsentSignatureBody(consentResult));
  }

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
  @Nullable
  public User getUser(Context context) {
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
  @Nullable
  public String getUserSharingScope(Context context) {
    logger.debug("Called getUserSharingScope");
    UserSessionInfo userSessionInfo = userLocalStorage.loadUserSession();
    return userLocalStorage == null ? null : userSessionInfo.getSharingScope().name();
  }

  @Override
  public void setUserSharingScope(Context context, String scope) {
    StudyParticipant participant = new StudyParticipant();
    participant.setSharingScope(SharingScope.valueOf(scope));

    ApiUtils.toResponseObservable(forConsentedUsersApi.updateUsersParticipantRecord(participant))
    .compose(ObservableUtils.applyDefault())
    .doOnNext(response -> {
      if (response.isSuccessful()) {
        UserSessionInfo userSessionInfo = userLocalStorage.loadUserSession();
        userSessionInfo.setSharingScope(SharingScope.valueOf(scope));
        userLocalStorage.saveUserSession(userSessionInfo, userLocalStorage.getSignIn());
      } else {
        ApiUtils.handleError(context, response.code());
      }
    })
    .subscribe(response -> LogExt.d(getClass(), "Response: " + response.code() + ", message: " +
        response.message()), error -> {
      LogExt.e(getClass(), error.getMessage());
    });
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

    return ApiUtils.toResponseObservable(forConsentedUsersApi.createConsentSignature(subpopulationGuid,consent)).
            compose(ObservableUtils.applyDefault())
            .doOnNext(response -> {
              if (response.code() == 201 || response.code() == 409) // success or already consented
              {
                UserSessionInfo userSessionInfo = userLocalStorage.loadUserSession();
                userSessionInfo.setConsented(true);
                userLocalStorage.saveUserSession(userSessionInfo, userLocalStorage.getSignIn());

                LogExt.d(getClass(), "Response: " + response.code() + ", message: " +
                    response.message());

                if (consentLocalStorage.hasConsent()) {
                  consentLocalStorage.deleteConsent();
                }
              } else {
                throw new RuntimeException(
                    "Error uploading consent, code: " + response.code() + " message: " +
                        response.message());
              }
            })
            .map(response -> new DataResponse(response.isSuccessful() || response.code() == 409, response.message()));
  }

  @Override
  public String getUserEmail(Context context) {
    User user = userLocalStorage.loadUser();
    return user == null ? null : user.getEmail();
  }

  @Override
  public Observable<DataResponse> forgotPassword(Context context, String email) {
    return ApiUtils.toBodyObservable(
        authenticationApi.requestResetPassword(new Email().study(studyId).email(email)))
        .map(message -> new DataResponse(true, message.getMessage()));
  }

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
    // Update/Create TaskNotificationService
    taskHelper.uploadTaskResult(context, forConsentedUsersApi, taskResult);
  }

  // these stink, I should be able to query the DB and find these
  private String getCreatedOnDate(String identifier) {
    return taskHelper.getCreatedOnDate(identifier);
  }

  @Override
  public abstract void processInitialTaskResult(Context context, TaskResult taskResult);

  public void uploadPendingFiles(Context context) {

    // There is an issue here, being that this will loop through the upload requests and upload
    // a zip async. The service cannot handle more than two async calls so any other requested
    // async calls fail due to SockTimeoutException
    uploadHandler.uploadPendingFiles(forConsentedUsersApi);
  }

  protected void uploadFile(UploadRequest request) {
    uploadHandler.uploadFile(forConsentedUsersApi, request);
  }

  private static class BridgeHeaderInterceptor implements Interceptor {
    private String userAgent;
    private String sessionToken;

    BridgeHeaderInterceptor(String userAgent, String sessionToken) {
      this.userAgent = userAgent;
      setSessionToken(sessionToken);
    }

    public String getSessionToken() {
      return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
      this.sessionToken = sessionToken;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
      Request original = chain.request();

      Request.Builder builder = original.newBuilder()
          .header("User-Agent", userAgent);
      if (!Strings.isNullOrEmpty(sessionToken)) {
        builder
            .header("Bridge-Session", sessionToken);
      }

      builder.method(original.method(), original.body())
          .build();

      return chain.proceed(builder.build());
    }
  }
}
