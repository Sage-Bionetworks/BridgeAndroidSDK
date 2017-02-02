package org.sagebionetworks.bridge.android;

import android.content.Context;
import android.os.Build;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import org.spongycastle.jcajce.provider.asymmetric.x509.CertificateFactory;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;
import java.util.Locale;

import static android.content.res.AssetManager.ACCESS_BUFFER;
import static android.os.Build.VERSION;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Used to configure settings for Bridge.
 * <p>
 * To provide your own settings, either override this class or modify these files:
 * <ul>
 *     <li>./assets/study_public_key.pem</li>
 *     <li>./res/values/bridge-config.xml</li>
 * </ul>
 */
@AnyThread
public class BridgeConfig {
    /**
     * Filename of the study's public key. File is found in the assets folder can be overriden by
     * your app
     */
    public static final String STUDY_PUBLIC_KEY = "study_public_key.pem";

    private final Context applicationContext;

    public BridgeConfig(@NonNull Context context) {
        checkNotNull(context);

        this.applicationContext = context.getApplicationContext();
    }

    @NonNull
    public Context getApplicationContext() {
        return applicationContext;
    }

    /**
     * Used for Accept-Language header in HTTP requests to Bridge.
     *
     * @return Accept-Language header
     */
    @NonNull
    public String getAcceptLanguage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return applicationContext.getResources()
                    .getConfiguration()
                    .getLocales()
                    .toLanguageTags();
        }

        Locale uiLocale = applicationContext.getResources().getConfiguration().locale;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return uiLocale.toLanguageTag();
        }

        return MessageFormat.format("{0}-{1}",
                                    uiLocale.getLanguage(),
                                    uiLocale.getCountry());
    }

    public int getSdkVersion() {
        return applicationContext.getResources().getInteger(R.integer.osb_android_sdk_version);
    }

    @NonNull
    public String getBaseUrl() {
        return applicationContext.getResources().getString(R.string.osb_base_url);
    }

    @NonNull
    public String getStudyId() {
        return applicationContext.getResources().getString(R.string.osb_study_id);
    }

    @NonNull
    public String getStudyName() {
        return applicationContext.getResources().getString(R.string.osb_study_name);
    }

    @NonNull
    public int getAppVersion() {
        return applicationContext.getResources().getInteger(R.integer.osb_app_version);
    }

    @NonNull
    public String getAppVersionName() {
        return applicationContext.getResources().getString(R.string.osb_app_version_name);
    }

    @NonNull
    public X509Certificate getPublicKey() throws IOException, CertificateException {
        CertificateFactory factory = new CertificateFactory();

        return (X509Certificate) factory.engineGenerateCertificate
                (applicationContext
                         .getAssets()
                         .open(STUDY_PUBLIC_KEY, ACCESS_BUFFER));
    }

    /**
     * Uses {@link #getStudyName()}, {@link #getAppVersion()}, {@link #getDeviceName()}, {@link
     * VERSION#RELEASE}, and {@link #getSdkVersion()}
     *
     * @return user agent in HTTP header format expected by server
     */
    @NonNull
    public final String getUserAgent() {
        return getStudyName() + "/" + getAppVersion() + " (" + getDeviceName() + "; Android "
                + VERSION.RELEASE + ") BridgeSDK/" + getSdkVersion();
    }

    @NonNull
    public String getDeviceName() {
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

    @NonNull
    String capitalize(@Nullable String s) {
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
