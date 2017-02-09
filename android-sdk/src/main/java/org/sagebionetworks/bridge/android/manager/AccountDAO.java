package org.sagebionetworks.bridge.android.manager;

import android.support.annotation.AnyThread;
import android.support.annotation.Nullable;

import org.sagebionetworks.bridge.rest.model.SignIn;
import org.sagebionetworks.bridge.rest.model.StudyParticipant;
import org.sagebionetworks.bridge.rest.model.UserSessionInfo;

/**
 * Created by jyliu on 2/8/2017.
 */
@AnyThread
public interface AccountDAO {
    @Nullable
    UserSessionInfo getUserSessionInfo();

    void setUserSessionInfo(@Nullable UserSessionInfo userSessionInfo);

    @Nullable
    SignIn getSignIn();

    void setSignIn(@Nullable SignIn signIn);

    @Nullable
    StudyParticipant getStudyParticipant();

    void setStudyParticipant(@Nullable StudyParticipant studyParticipant);
}
