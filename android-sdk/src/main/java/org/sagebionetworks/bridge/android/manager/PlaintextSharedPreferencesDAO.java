package org.sagebionetworks.bridge.android.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.common.collect.Maps;
import com.google.gson.reflect.TypeToken;

import org.sagebionetworks.bridge.rest.RestUtils;
import org.sagebionetworks.bridge.rest.model.ConsentSignature;
import org.sagebionetworks.bridge.rest.model.SignIn;
import org.sagebionetworks.bridge.rest.model.StudyParticipant;
import org.sagebionetworks.bridge.rest.model.UserSessionInfo;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * FIXME: encrypt sensitive information, integrate with fingerprint
 */

public class PlaintextSharedPreferencesDAO implements AccountDAO, ConsentDAO {
    public static final String PREFERENCES_FILE = "bridge-unencrypted";
    private static final String KEY_SESSION_INFO = "session";
    private static final String KEY_STUDY_PARTICIPANT = "participant";
    private static final String KEY_SIGN_IN = "signIn";
    private static final String KEY_CONSENT_MAP = "consent-map";

    private static final Type CONSENT_MAP_TYPE =
            new TypeToken<Map<String, ConsentSignature>>() {
            }.getType();

    private final ConcurrentMap<String, ConsentSignature> consents;
    private final SharedPreferences sharedPreferences;

    public PlaintextSharedPreferencesDAO(Context applicationContext) {
        sharedPreferences = applicationContext
                .getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE);
        consents = Maps.newConcurrentMap();
    }

    @Nullable
    @Override
    public UserSessionInfo getUserSessionInfo() {
        String sessionJson = sharedPreferences.getString(KEY_SESSION_INFO, null);
        return RestUtils.GSON.fromJson(sessionJson, UserSessionInfo.class);
    }

    @Override
    public void setUserSessionInfo(@Nullable UserSessionInfo userSessionInfo) {
        String sessionJson = RestUtils.GSON.toJson(userSessionInfo, UserSessionInfo.class);
        sharedPreferences.edit().putString(KEY_SESSION_INFO, sessionJson).apply();
    }

    @Nullable
    @Override
    public SignIn getSignIn() {
        String signInJson = sharedPreferences.getString(KEY_SIGN_IN, null);
        return RestUtils.GSON.fromJson(signInJson, SignIn.class);
    }

    @Override
    public void setSignIn(@Nullable SignIn signIn) {
        String signInJson = RestUtils.GSON.toJson(signIn, SignIn.class);
        sharedPreferences.edit().putString(KEY_SIGN_IN, signInJson).apply();
    }

    @Nullable
    @Override
    public StudyParticipant getStudyParticipant() {
        String studyParticipantJson = sharedPreferences.getString(KEY_STUDY_PARTICIPANT, null);
        return RestUtils.GSON.fromJson(studyParticipantJson, StudyParticipant.class);
    }

    @Override
    public void setStudyParticipant(@Nullable StudyParticipant studyParticipant) {
        String studyParticipantJson = RestUtils.GSON.toJson(studyParticipant, StudyParticipant.class);
        sharedPreferences.edit().putString(KEY_STUDY_PARTICIPANT, studyParticipantJson).apply();
    }

    @NonNull
    public Set<String> list() {
        return consents.keySet();
    }

    @Nullable
    public ConsentSignature get(@NonNull String subpopulationGuid) {
        checkNotNull(subpopulationGuid);

        return consents.get(subpopulationGuid);
    }

    public synchronized void put(@NonNull String subpopulationGuid,
                                 @NonNull ConsentSignature consentSignature) {
        checkNotNull(subpopulationGuid);
        checkNotNull(consentSignature);

        consents.put(subpopulationGuid, consentSignature);
        persist();
    }

    public synchronized void remove(@NonNull String subpopulationGuid) {
        checkNotNull(subpopulationGuid);

        consents.remove(subpopulationGuid);
        persist();
    }

    private synchronized void load() {
        String consentMapJson = sharedPreferences.getString(KEY_CONSENT_MAP, null);
        Map<String, ConsentSignature> consents =
                RestUtils.GSON.fromJson(consentMapJson, CONSENT_MAP_TYPE);
        if (consents != null) {
            consents.putAll(consents);
        }
    }

    private synchronized void persist() {
        String consentMapJson = RestUtils.GSON.toJson(consents);
        sharedPreferences.edit().putString(KEY_CONSENT_MAP, consentMapJson).apply();
    }
}
