package org.sagebionetworks.bridge.android.manager.dao;

import android.content.Context;
import android.support.annotation.AnyThread;
import android.support.annotation.Nullable;

import org.sagebionetworks.bridge.rest.model.SignIn;
import org.sagebionetworks.bridge.rest.model.StudyParticipant;
import org.sagebionetworks.bridge.rest.model.UserSessionInfo;

/**
 * Created by jyliu on 2/8/2017.
 */
@AnyThread
public class AccountDAO extends SharedPreferencesJsonDAO {
    private static final String PREFERENCES_FILE  = "accounts";
    private static final String KEY_SESSION_INFO = "session";
    private static final String KEY_STUDY_PARTICIPANT = "participant";
    private static final String KEY_SIGN_IN = "signIn";
    private static final String KEY_EMAIL = "email";

    public AccountDAO(Context applicationContext) {
        super(applicationContext, PREFERENCES_FILE);
    }

    @Nullable
    public UserSessionInfo getUserSessionInfo() {
        return getValue(KEY_SESSION_INFO, UserSessionInfo.class);
    }

    public void setUserSessionInfo(@Nullable UserSessionInfo userSessionInfo) {
        setValue(KEY_SESSION_INFO, userSessionInfo, UserSessionInfo.class);
    }

    @Nullable
    public String getEmail() {
        return getValue(KEY_EMAIL, String.class);
    }
    public void setEmail(@Nullable String email) {
        setValue(KEY_EMAIL, email, String.class);
    }

    @Nullable
    public StudyParticipant getStudyParticipant() {
        return getValue(KEY_STUDY_PARTICIPANT, StudyParticipant.class);
    }

    public void setStudyParticipant(@Nullable StudyParticipant studyParticipant) {
        setValue(KEY_STUDY_PARTICIPANT,studyParticipant, StudyParticipant.class);
    }
}
