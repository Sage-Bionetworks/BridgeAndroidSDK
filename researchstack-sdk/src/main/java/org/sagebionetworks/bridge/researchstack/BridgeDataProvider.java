package org.sagebionetworks.bridge.researchstack;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.gson.Gson;

import org.researchstack.backbone.ResourcePathManager;
import org.researchstack.backbone.result.StepResult;
import org.researchstack.backbone.result.TaskResult;
import org.researchstack.backbone.task.Task;
import org.researchstack.backbone.ui.step.layout.ConsentSignatureStepLayout;
import org.researchstack.backbone.utils.LogExt;
import org.researchstack.backbone.utils.ObservableUtils;
import org.researchstack.skin.AppPrefs;
import org.researchstack.skin.DataProvider;
import org.researchstack.skin.DataResponse;
import org.researchstack.skin.model.SchedulesAndTasksModel;
import org.researchstack.skin.model.TaskModel;
import org.researchstack.skin.model.User;
import org.researchstack.skin.task.ConsentTask;
import org.sagebionetworks.bridge.researchstack.upload.UploadRequest;
import org.sagebionetworks.bridge.researchstack.wrapper.StorageAccessWrapper;
import org.sagebionetworks.bridge.sdk.rest.ApiClientProvider;
import org.sagebionetworks.bridge.sdk.rest.api.AuthenticationApi;
import org.sagebionetworks.bridge.sdk.rest.api.ForConsentedUsersApi;
import org.sagebionetworks.bridge.sdk.rest.model.Email;
import org.sagebionetworks.bridge.sdk.rest.model.SignIn;
import org.sagebionetworks.bridge.sdk.rest.model.SignUp;
import org.sagebionetworks.bridge.sdk.restmm.UserSessionInfo;
import org.sagebionetworks.bridge.sdk.restmm.model.ConsentSignatureBody;
import org.sagebionetworks.bridge.sdk.restmm.model.EmailBody;
import org.sagebionetworks.bridge.sdk.restmm.model.SharingOptionBody;
import org.sagebionetworks.bridge.sdk.restmm.model.SignInBody;
import org.sagebionetworks.bridge.sdk.restmm.model.UploadSession;
import org.sagebionetworks.bridge.sdk.restmm.model.WithdrawalBody;

import java.io.File;
import java.io.IOException;
import java.util.Date;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import rx.Observable;

/*
* This is a very simple implementation that hits only part of the Sage Bridge REST API
* a complete port of the Sage Bridge Java SDK for android: https://github.com/Sage-Bionetworks/BridgeJavaSDK
 */
public abstract class BridgeDataProvider extends DataProvider {

  private final String studyId;
  private final String userAgent;
  private final String baseUrl;
  private final ApiClientProvider apiClientProvider;
  private final TaskHelper taskHelper;
  private final UploadHandler uploadHandler;

  protected final Gson gson = new Gson();
  protected final BridgeHeaderInterceptor interceptor;
  protected final StorageAccessWrapper storageAccess;

  // set in initialize
  protected  AppPrefs appPrefs;
  protected  UserLocalStorage userLocalStorage;
  protected  ConsentLocalStorage consentLocalStorage;

  private final AuthenticationApi authenticationApi;

  private BridgeService service;
  private ForConsentedUsersApi forConsentedUsersApi;

  //used by tests to mock service
  BridgeDataProvider(String baseUrl, String studyId, String userAgent,
      ApiClientProvider apiClientProvider, BridgeService service,
      AppPrefs appPrefs, StorageAccessWrapper storageAccess, UserLocalStorage userLocalStorage,
      ConsentLocalStorage consentLocalStorage, TaskHelper taskHelper, UploadHandler uploadHandler) {
    this.interceptor = new BridgeHeaderInterceptor(userAgent, null);
    this.baseUrl = baseUrl;
    this.studyId = studyId;
    this.userAgent = userAgent;
    this.appPrefs = appPrefs;
    this.service = service;
    this.storageAccess = storageAccess;
    this.userLocalStorage = userLocalStorage;
    this.consentLocalStorage = consentLocalStorage;
    this.taskHelper = taskHelper;
    this.uploadHandler = uploadHandler;

    this.apiClientProvider = apiClientProvider;
    this.authenticationApi = apiClientProvider.getClient(AuthenticationApi.class);

    updateBridgeService(null, null);
  }

  //public BridgeDataProvider(String baseUrl, String studyId, String userAgent) {
  //  this.interceptor = new BridgeHeaderInterceptor(userAgent, null);
  //  this.baseUrl = baseUrl;
  //  this.studyId = studyId;
  //  this.userAgent = userAgent;
  //
  //  this.apiClientProvider = new ApiClientProvider(baseUrl, userAgent);
  //  this.authenticationApi = apiClientProvider.getClient(AuthenticationApi.class);
  //
  //  updateBridgeService(null, null);
  //}

  private void updateBridgeService(@Nullable String sessionToken, @Nullable SignIn signIn) {
    this.forConsentedUsersApi = apiClientProvider.getClient(ForConsentedUsersApi.class, signIn);
    interceptor.setSessionToken(sessionToken);

    if (service != null) {
      return;
    }

    OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder().addInterceptor(interceptor);

    if (BuildConfig.DEBUG) {
      HttpLoggingInterceptor loggingInterceptor =
          new HttpLoggingInterceptor(message -> LogExt.i(getClass(), message));
      loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
      clientBuilder.addInterceptor(loggingInterceptor);
    }

    OkHttpClient client = clientBuilder.build();

    Retrofit retrofit = new Retrofit.Builder().addCallAdapterFactory(
        RxJavaCallAdapterFactory.create())
        .addConverterFactory(GsonConverterFactory.create())
        .baseUrl(baseUrl)
        .client(client)
        .build();
    service = retrofit.create(BridgeService.class);
  }

  @Override
  public Observable<DataResponse> initialize(Context context) {
    appPrefs = AppPrefs.getInstance(context);
    consentLocalStorage = new ConsentLocalStorage(context, gson, storageAccess.getFileAccess());
    userLocalStorage = new UserLocalStorage(context, gson, storageAccess.getFileAccess());

    return Observable.defer(() -> {
      UserSessionInfo userSessionInfo = userLocalStorage.loadUserSession();
      updateBridgeService(userSessionInfo == null ? null : userSessionInfo.getSessionToken(),
          userLocalStorage.getSignIn());
      return Observable.just(new DataResponse(true, null));
    }).doOnNext(response -> {
      // will crash if the user hasn't created a pincode yet, need to fix needsAuth()
      if (storageAccess.hasPinCode(context)) {
        checkForTempConsentAndUpload();
        uploadHandler.uploadPendingFiles(service);
      }
    });
  }

  private void checkForTempConsentAndUpload() {
    // If we are signed in, not consented on the server, but consented locally, upload consent
    if (isSignedIn() && !userLocalStorage.loadUserSession().isConsented()
        && consentLocalStorage.hasConsent()) {
      try {
        ConsentSignatureBody consent = consentLocalStorage.loadConsent();
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
  public boolean isConsented(Context context) {
    return userLocalStorage.loadUserSession().isConsented() || consentLocalStorage.hasConsent();
  }

  @Override
  public Observable<DataResponse> withdrawConsent(Context context, String reason) {
    return service.withdrawConsent(studyId, new WithdrawalBody(reason))
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

    return ApiUtils.toObservable(authenticationApi.signUp(signUp)).map(message -> {
      DataResponse response = new DataResponse();
      response.setSuccess(true);
      return response;
    });
  }

  @Override
  public Observable<DataResponse> signIn(Context context, String username, String password) {
    SignIn signIn = new SignIn().study(studyId).email(username).password(password);
    SignInBody body = new SignInBody(studyId, username, password);

    // response 412 still has a response body, so catch all http errors here
    return service.signIn(body).doOnNext(response -> {

      UserSessionInfo userSessionInfo = null;
      if (response.code() == 200) {
        userSessionInfo = response.body();
      } else if (response.code() == 412) {
        try {
          String errorBody = response.errorBody().string();
          userSessionInfo = gson.fromJson(errorBody, UserSessionInfo.class);
        } catch (IOException e) {
          throw new RuntimeException("Error deserializing server sign in response");
        }
      }

      if (userSessionInfo != null) {
        // if we are direct from signing in, we need to load the user profile object
        // from the server. that wouldn't work right now
        userLocalStorage.saveUserSession(userSessionInfo, signIn);
        updateBridgeService(userSessionInfo.getSessionToken(), signIn);
        checkForTempConsentAndUpload();
        uploadHandler.uploadPendingFiles(service);
      }
    }).map(response -> {
      boolean success = response.isSuccessful() || response.code() == 412;
      return new DataResponse(success, response.message());
    });
  }

  @Override
  public Observable<DataResponse> signOut(Context context) {
    return service.signOut()
        .map(response -> new DataResponse(response.isSuccessful(), null))
        .doOnNext(response -> {
          userLocalStorage.clearUserSession();
          userLocalStorage.clearSignIn();
          // we aren't clearing the user, so we still know their email and that they've signed up
        });
  }

  @Override
  public Observable<DataResponse> resendEmailVerification(Context context, String email) {
    return ApiUtils.toObservable(
        authenticationApi.resendEmailVerification(new Email().study(studyId).email(email)))
        .map(response -> new DataResponse(true, null));
  }

  @Override
  public boolean isSignedUp(Context context) {
    return userLocalStorage.isSignedUp();
  }

  public boolean isSignedIn() {
    return userLocalStorage.isSignedIn();
  }

  @Deprecated
  @Override
  public boolean isSignedIn(Context context) {
    return isSignedIn();
  }

  @Override
  public void saveConsent(Context context, TaskResult consentResult) {
    ConsentSignatureBody signature = createConsentSignatureBody(consentResult);
    consentLocalStorage.saveConsent(signature);

    User user = userLocalStorage.loadUser();
    if (user == null) {
      user = new User();
    }
    user.setName(signature.name);
    user.setBirthDate(signature.birthdate);
    userLocalStorage.saveUser(user);
  }

  @NonNull
  protected ConsentSignatureBody createConsentSignatureBody(TaskResult consentResult) {
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
    return new ConsentSignatureBody(studyId, fullName, new Date(birthdateInMillis), base64Image,
        "image/png", sharingScope);
  }

  @Override
  @Nullable
  public User getUser(Context context) {
    return userLocalStorage.loadUser();
  }

  @Override
  @Nullable
  public String getUserSharingScope(Context context) {
    UserSessionInfo userSessionInfo = userLocalStorage.loadUserSession();
    return userLocalStorage == null ? null : userSessionInfo.getSharingScope();
  }

  @Override
  public void setUserSharingScope(Context context, String scope) {
    // Update scope on server
    service.dataSharing(new SharingOptionBody(scope))
        .compose(ObservableUtils.applyDefault())
        .doOnNext(response -> {
          if (response.isSuccessful()) {
            UserSessionInfo userSessionInfo = userLocalStorage.loadUserSession();
            userSessionInfo.setSharingScope(scope);
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

  private void uploadConsent(String subpopulationGuid,
      ConsentSignatureBody consent) {
    service.consentSignature(subpopulationGuid, consent)
        .compose(ObservableUtils.applyDefault())
        .subscribe(response -> {
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
        });
  }

  @Override
  public String getUserEmail(Context context) {
    User user = userLocalStorage.loadUser();
    return user == null ? null : user.getEmail();
  }

  @Override
  public Observable<DataResponse> forgotPassword(Context context, String email) {
    return service.requestResetPassword(new EmailBody(studyId, email)).map(response -> {
      if (response.isSuccessful()) {
        return new DataResponse(true, response.body().getMessage());
      } else {
        return new DataResponse(false, response.message());
      }
    });
  }

  @Override
  public SchedulesAndTasksModel loadTasksAndSchedules(Context context) {

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
    taskHelper.uploadTaskResult(context, service, taskResult);
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
    uploadHandler.uploadPendingFiles(service);
  }

  protected void uploadFile(UploadRequest request) {
    uploadHandler.uploadFile(service, request);
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

      Request request = original.newBuilder()
          .header("User-Agent", userAgent)
          .header("Bridge-Session", sessionToken)
          .method(original.method(), original.body())
          .build();

      return chain.proceed(request);
    }
  }
}
