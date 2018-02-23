package org.sagebionetworks.bridge.android.manager.dao;

import android.content.Context;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.gson.reflect.TypeToken;
import org.sagebionetworks.bridge.rest.model.SignIn;
import org.sagebionetworks.bridge.rest.model.StudyParticipant;
import org.sagebionetworks.bridge.rest.model.UserSessionInfo;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Created by jyliu on 2/8/2017.
 */
@AnyThread
@Singleton
public class AccountDAO extends SharedPreferencesJsonDAO {
    private static final TypeToken<List<String>> STRING_LIST = new TypeToken<List<String>>(){};

    private static final String PREFERENCES_FILE  = "accounts";
    private static final String KEY_DATA_GROUPS = "dataGroups";
    private static final String KEY_SESSION_INFO = "session";
    private static final String KEY_STUDY_PARTICIPANT = "participant";
    private static final String KEY_SIGN_IN = "signIn";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_PASSWORD = "password";

    @Inject
    public AccountDAO(Context applicationContext) {
        super(applicationContext, PREFERENCES_FILE);
    }

    /*
     * Removes all saved data
     */
    public void clear() {
        sharedPreferences.edit().clear().commit();
    }

    /**
     * Returns a list of data groups associated with this account. If there are no data groups,
     * this method returns an empty list.
     */
    @NonNull
    public List<String> getDataGroups() {
        List<String> rawDataGroupList = getValue(KEY_DATA_GROUPS, STRING_LIST);
        if (rawDataGroupList == null) {
            // Null collections are bad. Return an empty list. Make it mutable, because callers
            // might want to update it.
            return new ArrayList<>();
        } else {
            return rawDataGroupList;
        }
    }

    /**
     * Sets data groups for this account locally. Note: this does not call the server to update the
     * participant. Note: this replaces existing data groups. To add to existing data groups, call
     * addDataGroup().
     */
    public void setDataGroups(@NonNull List<String> dataGroupList) {
        setValue(KEY_DATA_GROUPS, dataGroupList, STRING_LIST);
    }

    /**
     * Add data groups to this account locally. Note: this does not call the server to update the
     * participant.
     */
    public void addDataGroup(@NonNull String dataGroup) {
        List<String> dataGroupList = getDataGroups();
        dataGroupList.add(dataGroup);
        setDataGroups(dataGroupList);
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
    public String getPassword() {
        return getValue(KEY_PASSWORD, String.class);
    }

    public void setPassword(@Nullable String password) {
        setValue(KEY_PASSWORD, password, String.class);
    }

    @Nullable
    @Deprecated
    public SignIn getSignIn() {
        return getValue(KEY_SIGN_IN, SignIn.class);
    }

    @Deprecated
    public void setSignIn(@Nullable SignIn signIn) {
        setValue(KEY_SIGN_IN, signIn, SignIn.class);
    }

    @Nullable
    public StudyParticipant getStudyParticipant() {
        return getValue(KEY_STUDY_PARTICIPANT, StudyParticipant.class);
    }

    public void setStudyParticipant(@Nullable StudyParticipant studyParticipant) {
        setValue(KEY_STUDY_PARTICIPANT,studyParticipant, StudyParticipant.class);
    }
}
