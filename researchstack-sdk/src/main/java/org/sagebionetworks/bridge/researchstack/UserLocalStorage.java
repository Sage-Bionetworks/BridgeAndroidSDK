package org.sagebionetworks.bridge.researchstack;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;

import org.researchstack.backbone.model.User;
import org.researchstack.backbone.storage.file.FileAccess;
import org.researchstack.backbone.storage.file.StorageAccessException;
import org.sagebionetworks.bridge.rest.model.SignIn;
import org.sagebionetworks.bridge.rest.model.UserSessionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by liujoshua on 9/12/16.
 */
public class UserLocalStorage {
  public static final String USER_SESSION_PATH = "/user_session";
  public static final String USER_PATH = "/user";

  public static final String BRIDGE_USER_PREFS = "bridge-user-prefs";
  public static final String PREFS_EMAIL = "email";
  public static final String PREFS_PASSWORD = "password";
  public static final String PREFS_STUDY = "studyId";

  private static final Logger logger = LoggerFactory.getLogger(UserLocalStorage.class);

  private final FileAccess fileAccess;
  private final Gson gson;
  private final Context applicationContext;
  private final SharedPreferences sharedPreferences;
  private boolean isSignedIn = false;

  public UserLocalStorage(Context applicationContext, Gson gson, FileAccess fileAccess) {
    this.fileAccess = fileAccess;
    this.gson = gson;
    // make sure it's really the application context
    this.applicationContext = applicationContext.getApplicationContext();
    this.sharedPreferences = this.applicationContext.getSharedPreferences(BRIDGE_USER_PREFS,
        Context.MODE_PRIVATE);
  }

  public void saveUser(@NonNull User profile) {
    writeJsonString(gson.toJson(profile), USER_PATH);
  }

  @Nullable
  public User loadUser() {
    try {
      String user = loadJsonString(USER_PATH);
      return gson.fromJson(user, User.class);
    } catch (StorageAccessException e) {
      return null;
    }
  }

  @Nullable
  @Deprecated
  public User loadUser(Context context) {
    return loadUser();
  }

  public boolean isSignedUp() {
    User user = loadUser();
    return user != null && user.getEmail() != null;
  }

  public void clearUser() {
    fileAccess.clearData(applicationContext, USER_PATH);
  }

  public void saveUserSession(@Nullable UserSessionInfo userInfo, @Nullable SignIn signIn) {
    if (userInfo == null || signIn == null) {
      logger.info("Clearing user session because either signIn or session was null");
      clearUserSession();
      return;
    }
    isSignedIn = true;
    String userSessionJson = gson.toJson(userInfo);
    writeJsonString(userSessionJson, USER_SESSION_PATH);

    sharedPreferences.edit()
        .putString(PREFS_STUDY, signIn.getStudy())
        .putString(PREFS_EMAIL, signIn.getEmail())
        .putString(PREFS_PASSWORD, signIn.getPassword()).apply();
  }

  public void clearSignIn() {
    sharedPreferences.edit().clear().apply();
  }

  @Nullable
  public SignIn getSignIn() {
    String email = sharedPreferences.getString(PREFS_EMAIL, null);
    String study = sharedPreferences.getString(PREFS_STUDY, null);
    String password = sharedPreferences.getString(PREFS_PASSWORD, null);

    if (email == null || study == null || password == null) {
      return null;
    }

    return new SignIn().study(study).email(email).password(password);
  }

  public boolean isSignedIn() {
    return this.isSignedIn;
  }

  public void clearUserSession() {
    isSignedIn = false;
    fileAccess.clearData(applicationContext, USER_SESSION_PATH);
  }

  @Nullable
  public UserSessionInfo loadUserSession() {
    try {
      String userSessionJson = loadJsonString(USER_SESSION_PATH);
      UserSessionInfo session = gson.fromJson(userSessionJson, UserSessionInfo.class);
      if (session != null) {
        this.isSignedIn = true;
      }
      return session;
    } catch (StorageAccessException e) {
      return null;
    }
  }

  @Deprecated
  public UserSessionInfo loadUserSession(Context context) {
    return loadUserSession();
  }

  private void writeJsonString(String userSessionJson, String userSessionPath) {
    fileAccess.writeData(applicationContext, userSessionPath, userSessionJson.getBytes());
  }

  private String loadJsonString(String path) {
    return new String(fileAccess.readData(applicationContext, path));
  }
}
