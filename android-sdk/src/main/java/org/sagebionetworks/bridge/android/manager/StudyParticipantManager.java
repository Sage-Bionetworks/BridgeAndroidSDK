package org.sagebionetworks.bridge.android.manager;

import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;

import org.sagebionetworks.bridge.android.manager.auth.AuthenticationManager;
import org.sagebionetworks.bridge.android.util.retrofit.RxUtils;
import org.sagebionetworks.bridge.rest.model.StudyParticipant;
import org.sagebionetworks.bridge.rest.model.UserSessionInfo;

import rx.Completable;
import rx.functions.Action1;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by jyliu on 1/20/2017.
 */
@AnyThread
public class StudyParticipantManager {
    @NonNull
    private final AuthenticationManager authenticationManager;

    public StudyParticipantManager(@NonNull AuthenticationManager authenticationManager) {
        checkNotNull(authenticationManager);

        this.authenticationManager = authenticationManager;
    }

    @NonNull
    public Completable updateParticipant(@NonNull StudyParticipant studyParticipant) {
        checkNotNull(studyParticipant);

        return RxUtils.toBodySingle(authenticationManager.getApi()
                                            .updateUsersParticipantRecord(studyParticipant))
                .doOnSuccess(new Action1<UserSessionInfo>() {
                    @Override
                    public void call(UserSessionInfo userSessionInfo) {
                        authenticationManager.getDao()
                                .setUserSessionInfo(userSessionInfo);
                    }
                })
                .toCompletable();
    }
}
