package org.sagebionetworks.bridge.android;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;

/**
 * Created by liujoshua on 9/12/16.
 */
public class BridgeIdentifiers {
  private final Context applicationContext;

  public BridgeIdentifiers(Context applicationContext) {
    this.applicationContext = applicationContext;
  }

  public final String getUserAgent() {
    return getStudyName() + "/" + getAppVersion() + " (" + getDeviceName() + "; Android "
        + Build.VERSION.RELEASE + ") BridgeSDK/" + getSdkVersion();
  }

  public final int getSdkVersion() {
    return applicationContext.getResources().getInteger(R.integer.osb_android_sdk_version);
  }

  public String getStudyName() {
    return applicationContext.getResources().getString(R.string.osb_study_name);
  }

  public int getAppVersion() {
    return applicationContext.getResources().getInteger(R.integer.osb_app_version);
  }

  private String getDeviceName() {
    String manufacturer = Build.MANUFACTURER;
    if (TextUtils.isEmpty(manufacturer)) {
      manufacturer = "Unknown";
    }

    String model = Build.MODEL;
    if (TextUtils.isEmpty(model)) {
      model = "Android";
    }

    if (model.startsWith(manufacturer)) {
      return capitalize(model);
    } else {
      return capitalize(manufacturer) + " " + model;
    }
  }

  private String capitalize(String s) {
    if (s == null || s.length() == 0) {
      return "";
    }
    char first = s.charAt(0);
    if (Character.isUpperCase(first)) {
      return s;
    } else {
      return Character.toUpperCase(first) + s.substring(1);
    }
  }
}
