package org.sagebionetworks.bridge.researchstack;

import android.content.Context;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.researchstack.skin.DataProvider;
import org.researchstack.skin.DataResponse;
import org.researchstack.skin.model.User;
import org.sagebionetworks.bridge.android.manager.BridgeManagerProvider;
import org.sagebionetworks.bridge.android.manager.auth.AuthManager;
import org.sagebionetworks.bridge.rest.model.SharingScope;
import org.sagebionetworks.bridge.rest.model.StudyParticipant;
import org.sagebionetworks.bridge.rest.model.UserSessionInfo;

import rx.Completable;
import rx.Observable;
import rx.Observer;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.researchstack.ApiUtils.SUCCESS_DATA_RESPONSE;

/**
 * Created by jyliu on 1/20/2017.
 */
@AnyThread
public abstract class BridgeDataProvider2 extends DataProvider {
    private final BridgeManagerProvider bridgeManagerProvider;

    public BridgeDataProvider2(BridgeManagerProvider bridgeManagerProvider) {
        this.bridgeManagerProvider = bridgeManagerProvider;
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

        return bridgeManagerProvider.getAuthManager()
                .signUp(email, password)
                .andThen(SUCCESS_DATA_RESPONSE);
    }

    @Override
    @Deprecated
    public boolean isSignedUp(Context context) {
        return isSignedUp();
    }

    public boolean isSignedUp() {
        return bridgeManagerProvider.getAuthManager()
                .getAuthManagerDelegateProtocol()
                .getEmail() != null;
    }

    @Override
    @Nullable
    @Deprecated
    public User getUser(Context context) {
        return getUser();
    }

    @Nullable
    public User getUser() {
        AuthManager.AuthManagerDelegateProtocol authManagerDelegateProtocol =
                bridgeManagerProvider.getAuthManager()
                        .getAuthManagerDelegateProtocol();

        User user = new User();
        user.setEmail(authManagerDelegateProtocol.getEmail());
        //TODO: populate user's name and birthdate
        return user;
    }

    @Deprecated
    public boolean isSignedIn(Context context) {
        return isSignedIn();
    }

    public boolean isSignedIn() {
        UserSessionInfo session = bridgeManagerProvider.getAuthManager().getUserSessionInfo();
        return session != null && session.getAuthenticated();
    }

    @Override
    @Nullable
    @Deprecated
    public String getUserSharingScope(Context context) {
        return getUserSharingScope();
    }

    @Nullable
    public String getUserSharingScope() {
        UserSessionInfo session = bridgeManagerProvider.getAuthManager()
                .getAuthManagerDelegateProtocol()
                .getUserSessionInfo();
        if (session == null) {
            return null;
        }
        return session.getSharingScope() != null ? session.getSharingScope().toString() : null;
    }

    @Override
    @Deprecated
    public void setUserSharingScope(Context context, String scope) {
        setUserSharingScope(scope).await();
    }

    @NonNull
    public Completable setUserSharingScope(@Nullable String scope) {
        AuthManager.AuthManagerDelegateProtocol authManagerDelegateProtocol =
                bridgeManagerProvider.getAuthManager()
                        .getAuthManagerDelegateProtocol();

        return bridgeManagerProvider.getStudyParticipantManager()
                .updateParticipant((StudyParticipant) new StudyParticipant()
                        .email(authManagerDelegateProtocol.getEmail())
                        .sharingScope(SharingScope.valueOf(scope)));
    }

    /**
     * May invoke {@link Observer#onError(Throwable)} with ConsentRequiredException, to indicate
     *
     * @see super#signIn(Context, String, String)
     */
    @Override
    @Deprecated
    public Observable<DataResponse> signIn(Context context, String username, String password) {
        return signIn(username, password).andThen(SUCCESS_DATA_RESPONSE);
    }

    @NonNull
    public Completable signIn(@NonNull String email, @NonNull String password) {
        checkNotNull(email);
        checkNotNull(password);

        return bridgeManagerProvider.getAuthManager()
                .signIn(email, password).toCompletable();
    }

    @Override
    @Deprecated
    public Observable<DataResponse> signOut(Context context) {
        return signOut().andThen(SUCCESS_DATA_RESPONSE);
    }

    @NonNull
    public Completable signOut() {
        return bridgeManagerProvider.getAuthManager()
                .signOut();
    }

    @Override
    @Deprecated
    public Observable<DataResponse> resendEmailVerification(Context context, String email) {
        return resendEmailVerification(email).andThen(SUCCESS_DATA_RESPONSE);
    }

    @NonNull
    public Completable resendEmailVerification(@NonNull String email) {
        checkNotNull(email);

        return bridgeManagerProvider.getAuthManager()
                .resendEmailVerification(email);
    }

    @Override
    @Deprecated
    public Observable<DataResponse> forgotPassword(Context context, String email) {
        return forgotPassword(email).andThen(SUCCESS_DATA_RESPONSE);
    }

    @NonNull
    public Completable forgotPassword(@NonNull String email) {
        checkNotNull(email);

        return bridgeManagerProvider.getAuthManager()
                .requestPasswordResetForEmail(email);
    }
}
