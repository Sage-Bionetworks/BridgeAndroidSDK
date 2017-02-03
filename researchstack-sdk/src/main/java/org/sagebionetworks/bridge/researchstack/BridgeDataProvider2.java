package org.sagebionetworks.bridge.researchstack;

import android.content.Context;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.joda.time.LocalDate;
import org.researchstack.backbone.DataProvider;
import org.researchstack.backbone.DataResponse;
import org.researchstack.backbone.model.ConsentSignatureBody;
import org.researchstack.backbone.model.SchedulesAndTasksModel;
import org.researchstack.backbone.model.User;
import org.researchstack.backbone.result.TaskResult;
import org.researchstack.backbone.task.Task;
import org.sagebionetworks.bridge.android.BridgeConfig;
import org.sagebionetworks.bridge.android.manager.BridgeManagerProvider;
import org.sagebionetworks.bridge.android.manager.ConsentManager;
import org.sagebionetworks.bridge.android.manager.auth.AuthenticationManager;
import org.sagebionetworks.bridge.rest.model.ConsentSignature;
import org.sagebionetworks.bridge.rest.model.SharingScope;
import org.sagebionetworks.bridge.rest.model.StudyParticipant;
import org.sagebionetworks.bridge.rest.model.UserSessionInfo;

import rx.Completable;
import rx.Observable;
import rx.Observer;
import rx.Single;
import rx.functions.Action0;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.researchstack.ApiUtils.SUCCESS_DATA_RESPONSE;

/**
 * Created by jyliu on 1/20/2017.
 */
@AnyThread
public class BridgeDataProvider2 extends DataProvider {
    private final BridgeManagerProvider bridgeManagerProvider;
    private final BridgeConfig bridgeConfig;
    private final AuthenticationManager authenticationManager;
    private final ConsentManager consentManager;


    public BridgeDataProvider2() {
        this.bridgeManagerProvider = BridgeManagerProvider.getInstance();

        // convenience accessors
        this.bridgeConfig = bridgeManagerProvider.getBridgeConfig();
        this.authenticationManager = bridgeManagerProvider.getAuthenticationManager();
        this.consentManager = bridgeManagerProvider.getConsentManager();
    }

    @Override
    @Deprecated
    public Observable<DataResponse> initialize(@NonNull Context context) {
        checkNotNull(context);

        return Observable.just(new DataResponse(true, "donothing"));
    }

    @Override
    @NonNull
    @Deprecated
    public Observable<DataResponse> signUp(@NonNull Context context, @NonNull String email,
                                           @NonNull String username, @NonNull String password) {
        return signUp(email, password);
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
    @Deprecated
    public boolean isSignedUp(Context context) {
        return isSignedUp();
    }

    public boolean isSignedUp() {
        return authenticationManager
                .getDao()
                .getEmail() != null;
    }

    @Override
    @Nullable
    @Deprecated
    public User getUser(Context context) {
        return getUser();
    }

    @Override
    public void setUser(Context context, User user) {
        throw new UnsupportedOperationException();
    }

    @Nullable
    public User getUser() {
        User user = new User();
        user.setEmail(authenticationManager.getDao().getEmail());

        //TODO: populate user's name and birthdate
        return user;
    }

    @Deprecated
    public boolean isSignedIn(Context context) {
        return isSignedIn();
    }

    @Override
    public boolean isConsented() {
        throw new UnsupportedOperationException();
    }

    @NonNull
    public Completable withdrawConsent(@NonNull String subpopulationGuid, @Nullable String reason) {
        return consentManager.withdrawConsent(subpopulationGuid, reason);
    }

    @Override
    @Deprecated
    public Observable<DataResponse> withdrawConsent(Context context, String reason) {
        return consentManager.withdrawConsent(bridgeConfig.getStudyId(), reason)
                .andThen(SUCCESS_DATA_RESPONSE);
    }

    @NonNull
    public Completable giveConsent(@NonNull String subpopulationGuid, @NonNull String name,
                                   @NonNull LocalDate birthdate,
                                   @NonNull String base64Image, @NonNull String imageMimeType,
                                   @Nullable SharingScope sharingScope) {
        return consentManager.giveConsent(subpopulationGuid, name, birthdate, base64Image, imageMimeType, sharingScope);
    }

    @Override
    public void uploadConsent(Context context, TaskResult consentResult) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Observable<DataResponse> uploadConsent(Context context, ConsentSignatureBody signature) {
        throw new UnsupportedOperationException();
    }

    public ConsentSignature retrieveConsent(String subpopulationGuid) {
        return consentManager.getConsentSignature(subpopulationGuid).toBlocking().value();
    }
    @Override
    public ConsentSignatureBody loadLocalConsent(Context context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void saveConsent(Context context, TaskResult consentResult) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void saveLocalConsent(Context context, ConsentSignatureBody consentSignatureBody) {
        throw new UnsupportedOperationException();
    }

    public boolean isSignedIn() {
        UserSessionInfo session = authenticationManager
                .getUserSessionInfo();
        return session != null && session.getAuthenticated();
    }

    @Override
    @Nullable
    @Deprecated
    public String getUserSharingScope(Context context) {
        SharingScope scope = getUserSharingScope();
        return scope != null ? scope.toString() : null;
    }

    @Nullable
    public SharingScope getUserSharingScope() {
        UserSessionInfo session = authenticationManager
                .getDao()
                .getUserSessionInfo();
        if (session == null) {
            return null;
        }
        return session.getSharingScope();
    }

    @Override
    @Deprecated
    public void setUserSharingScope(Context context, String scope) {
        setUserSharingScope(scope).toCompletable().await();
    }

    @Override
    public String getUserEmail(Context context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void uploadTaskResult(Context context, TaskResult taskResult) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SchedulesAndTasksModel loadTasksAndSchedules(Context context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Task loadTask(Context context, SchedulesAndTasksModel.TaskScheduleModel task) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void processInitialTaskResult(Context context, TaskResult taskResult) {
        throw new UnsupportedOperationException();
    }

    @NonNull
    public Single<UserSessionInfo> setUserSharingScope(@Nullable String scope) {
        AuthenticationManager.DAO DAO =
                authenticationManager
                        .getDao();

        return bridgeManagerProvider.getStudyParticipantManager()
                .updateParticipant((StudyParticipant) new StudyParticipant()
                        .email(DAO.getEmail())
                        .sharingScope(SharingScope.valueOf(scope)));
    }

    /**
     * @see DataProvider#signIn(Context, String, String)
     * <p>
     * May invoke {@link Observer#onError(Throwable)} with ConsentRequiredException, to indicate
     * consent is required
     */
    @Override
    @Deprecated
    public Observable<DataResponse> signIn(Context context, @NonNull String username, @NonNull
            String password) {
        return signIn(username, password).andThen(SUCCESS_DATA_RESPONSE);
    }

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

    @Override
    @Deprecated
    public Observable<DataResponse> signOut(Context context) {
        return signOut().andThen(SUCCESS_DATA_RESPONSE);
    }

    @NonNull
    public Completable signOut() {
        return authenticationManager
                .signOut();
    }

    @Override
    @Deprecated
    public Observable<DataResponse> resendEmailVerification(Context context, @NonNull String
            email) {
        return resendEmailVerification(email).andThen(SUCCESS_DATA_RESPONSE);
    }

    @Override
    public Observable<DataResponse> verifyEmail(Context context, String password) {
        throw new UnsupportedOperationException();
    }

    @NonNull
    public Completable resendEmailVerification(@NonNull String email) {
        checkNotNull(email);

        return authenticationManager
                .resendEmailVerification(email);
    }

    @Override
    @Deprecated
    public Observable<DataResponse> forgotPassword(Context context, @NonNull String email) {
        return forgotPassword(email).andThen(SUCCESS_DATA_RESPONSE);
    }

    @Override
    public String getStudyId() {
        throw new UnsupportedOperationException();
    }

    @NonNull
    public Completable forgotPassword(@NonNull String email) {
        checkNotNull(email);

        return authenticationManager
                .requestPasswordResetForEmail(email);
    }
}
