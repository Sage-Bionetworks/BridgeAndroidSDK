package org.sagebionetworks.bridge.researchstack;

import android.content.Context;
import com.google.gson.Gson;
import org.researchstack.backbone.storage.file.FileAccess;
import org.researchstack.backbone.storage.file.StorageAccessException;
import org.researchstack.skin.model.User;
import org.sagebionetworks.bridge.sdk.restmm.UserSessionInfo;

/**
 * Created by liujoshua on 9/12/16.
 */
public class UserLocalStorage {
  public static final String USER_SESSION_PATH = "/user_session";
  public static final String USER_PATH = "/user";

  private final FileAccess fileAccess;
  private final Gson gson;
  private final Context applicationContext;

  public UserLocalStorage(Context applicationContext, Gson gson, FileAccess fileAccess) {
    this.fileAccess = fileAccess;
    this.gson = gson;
    this.applicationContext = applicationContext;
  }

  public void saveUser(User profile) {
    writeJsonString(gson.toJson(profile), USER_PATH);
  }

  public User loadUser(Context context) {
    try {
      String user = loadJsonString(USER_PATH);
      return gson.fromJson(user, User.class);
    } catch (StorageAccessException e) {
      return null;
    }
  }

  public void saveUserSession(UserSessionInfo userInfo) {
    String userSessionJson = gson.toJson(userInfo);
    writeJsonString(userSessionJson, USER_SESSION_PATH);
  }

  public UserSessionInfo loadUserSession(Context context) {
    try {
      String userSessionJson = loadJsonString(USER_SESSION_PATH);
      return gson.fromJson(userSessionJson, UserSessionInfo.class);
    } catch (StorageAccessException e) {
      return null;
    }
  }

  private void writeJsonString(String userSessionJson, String userSessionPath) {
    fileAccess.writeData(applicationContext, userSessionPath, userSessionJson.getBytes());
  }

  private String loadJsonString(String path) {
    return new String(fileAccess.readData(applicationContext, path));
  }
}
