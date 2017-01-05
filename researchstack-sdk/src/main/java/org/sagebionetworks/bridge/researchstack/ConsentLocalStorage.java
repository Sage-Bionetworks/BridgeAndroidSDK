package org.sagebionetworks.bridge.researchstack;

import android.content.Context;

import com.google.gson.Gson;

import org.researchstack.backbone.storage.file.FileAccess;
import org.sagebionetworks.bridge.rest.model.ConsentSignature;

/**
 * Created by liujoshua on 9/12/16.
 */
public class ConsentLocalStorage {
  public static final String TEMP_CONSENT_JSON_FILE_NAME = "/consent_sig";

  private final FileAccess fileAccess;
  private final Context applicationContext;
  private final Gson gson;

  public ConsentLocalStorage(Context applicationContext, Gson gson, FileAccess fileAccess) {
    this.fileAccess = fileAccess;
    this.applicationContext = applicationContext;
    this.gson = gson;
  }

  public void deleteConsent() {
    fileAccess.clearData(applicationContext, TEMP_CONSENT_JSON_FILE_NAME);
  }

  public boolean hasConsent() {
    return fileAccess.dataExists(applicationContext, TEMP_CONSENT_JSON_FILE_NAME);
  }

  public void saveConsent(ConsentSignature consent) {
    writeJsonString(gson.toJson(consent), TEMP_CONSENT_JSON_FILE_NAME);
  }

  public ConsentSignature loadConsent() {
    String consentJson = loadJsonString(applicationContext, TEMP_CONSENT_JSON_FILE_NAME);
    return gson.fromJson(consentJson, ConsentSignature.class);
  }

  private void writeJsonString(String userSessionJson, String userSessionPath) {
    fileAccess.writeData(applicationContext, userSessionPath, userSessionJson.getBytes());
  }

  private String loadJsonString(Context context, String path) {
    return new String(fileAccess.readData(applicationContext, path));
  }
}
