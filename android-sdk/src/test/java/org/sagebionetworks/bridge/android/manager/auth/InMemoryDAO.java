package org.sagebionetworks.bridge.android.manager.auth;

import android.content.Context;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.sagebionetworks.bridge.android.manager.AccountDAO;
import org.sagebionetworks.bridge.rest.model.SignIn;
import org.sagebionetworks.bridge.rest.model.StudyParticipant;
import org.sagebionetworks.bridge.rest.model.UserSessionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by jyliu on 1/20/2017.
 */
@AnyThread
class InMemoryDAO implements AccountDAO {
    private static final Logger logger = LoggerFactory.getLogger
            (InMemoryDAO.class);

    public InMemoryDAO(@NonNull Context context) {
        checkNotNull(context);
    }

    @Nullable
    private UserSessionInfo userSessionInfo;
    @Nullable
    private SignIn signIn;
    @Nullable
    private StudyParticipant studyParticipant;

    @Nullable
    @Override
    public UserSessionInfo getUserSessionInfo() {
        logger.debug("getUserSessionInfo called, found: " + userSessionInfo);

        return userSessionInfo;
    }

    @Override
    public void setUserSessionInfo(@Nullable UserSessionInfo userSessionInfo) {
        logger.debug("setUserSessionInfo called with: " + userSessionInfo);

        this.userSessionInfo = userSessionInfo;
    }

    @Nullable
    @Override
    public SignIn getSignIn() {
        logger.debug("getSignIn called, found: " + signIn);

        return signIn;
    }

    @Override
    public void setSignIn(@Nullable SignIn signIn) {
        logger.debug("setSignIn called with: " + signIn);
        this.signIn = signIn;
    }

    @Nullable
    @Override
    public StudyParticipant getStudyParticipant() {
        logger.debug("getStudyParticipant called, found: " + studyParticipant);

        return studyParticipant;
    }

    @Override
    public void setStudyParticipant(@Nullable StudyParticipant studyParticipant) {
        logger.debug("setStudyParticipant called with: " + studyParticipant);

        this.studyParticipant = studyParticipant;
    }


}
