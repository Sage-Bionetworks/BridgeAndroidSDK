package org.sagebionetworks.bridge.researchstack;

import android.os.Build;
import android.text.TextUtils;

/**
 * Created by liujoshua on 9/12/16.
 */
public abstract class BridgeIdentifiers {
  public final String getUserAgent() {
    return getStudyName()
        + "/"
        + getAppVersion()
        + " ("
        + getDeviceName()
        + "; Android "
        + Build.VERSION.RELEASE
        + ") BridgeSDK/0";
  }

  public abstract String getStudyName();

  public abstract int getAppVersion();

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
