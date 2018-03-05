package org.sagebionetworks.bridge.android;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import org.sagebionetworks.bridge.android.manager.upload.SchemaKey;
import org.sagebionetworks.bridge.rest.RestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.jcajce.provider.asymmetric.x509.CertificateFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import static android.content.res.AssetManager.ACCESS_BUFFER;
import static android.os.Build.VERSION;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Used to configure settings for Bridge.
 * <p>
 * To provide your own settings, either override this class or modify these files:
 * <ul>
 * <li>./assets/study_public_key.pem</li>
 * <li>./res/values/bridge-config.xml</li>
 * </ul>
 */
@AnyThread
@Singleton
public class BridgeConfig {
    private static final Logger logger = LoggerFactory.getLogger(BridgeConfig.class);

    /**
     * Filename for external ID settings. This file should be in the format:
     * {
     *   "emailFormat":"example+%s@example.com",
     *   "passwordFormat":"%s"
     * }
     *
     * Where %s will be replaced by the external ID. This file is optional.
     */
    public static final String EXTERNAL_ID_SETTINGS_FILENAME = "external_id_settings.json";

    /**
     * Filename of the study's public key. File is found in the assets folder can be overriden by
     * your app
     */
    public static final String STUDY_PUBLIC_KEY = "study_public_key.pem";

    /**
     * Filename for the task ID to schema key file. This file should be placed in the assets folder
     * of your app.
     */
    public static final String TASK_TO_SCHEMA_FILENAME = "task_to_schema.json";

    private static final String KEY_EXTERNAL_ID_EMAIL_FORMAT = "emailFormat";
    private static final String KEY_EXTERNAL_ID_PASSWORD_FORMAT = "passwordFormat";
    private static final Type STRING_TO_STRING_MAP =
            new TypeToken<Map<String, String>>(){}.getType();
    private static final Type TASK_TO_SCHEMA_TYPE =
            new TypeToken<Map<String, SchemaKey>>(){}.getType();

    private final Context applicationContext;
    private final String externalIdEmailFormat;
    /**
     * @return external ID format specified from file, if one exists, null otherwise
     */
    public String getExternalIdEmailFormat() {
        return externalIdEmailFormat;
    }

    private final String externalIdPasswordFormat;
    private final Map<String, SchemaKey> taskToSchemaMap;

    @Inject
    public BridgeConfig(@NonNull Context context) {
        checkNotNull(context);

        this.applicationContext = context.getApplicationContext();

        // Load task ID to schema map.
        // Temp var is required to satisfy Java's assign-once semantics for final members.
        Map<String, SchemaKey> tempTaskToSchemaMap = loadJsonAsset(TASK_TO_SCHEMA_FILENAME,
                TASK_TO_SCHEMA_TYPE);
        taskToSchemaMap = tempTaskToSchemaMap != null ? tempTaskToSchemaMap : ImmutableMap.of();

        // Load external ID settings, if present.
        Map<String, String> externalIdSettings = loadJsonAsset(EXTERNAL_ID_SETTINGS_FILENAME,
                STRING_TO_STRING_MAP);
        if (externalIdSettings != null) {
            externalIdEmailFormat = externalIdSettings.get(KEY_EXTERNAL_ID_EMAIL_FORMAT);
            externalIdPasswordFormat = externalIdSettings.get(KEY_EXTERNAL_ID_PASSWORD_FORMAT);
        } else {
            // Java assign-once semantics require us to assign a value in the constructor.
            externalIdEmailFormat = null;
            externalIdPasswordFormat = null;
        }
    }

    /**
     * Helper method to load a JSON file from the asset manager.
     *
     * @param filename asset filename
     * @param typeOfT  Type object represented by T
     * @param <T>      type that GSON should convert the object into
     * @return JSON loaded and parsed from the asset file
     */
    @Nullable
    private <T> T loadJsonAsset(@NonNull String filename, @NonNull Type typeOfT) {
        try (InputStream inputStream = applicationContext.getAssets().open(filename, ACCESS_BUFFER);
             Reader reader = new InputStreamReader(inputStream)) {
            return RestUtils.GSON.fromJson(reader, typeOfT);
        } catch (IOException | JsonParseException ex) {
            logger.error("Could not asset " + filename, ex);
        }
        return null;
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
        return BuildConfig.VERSION_CODE;
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
        try {
            PackageInfo pInfo = applicationContext.getPackageManager()
                    .getPackageInfo(applicationContext.getPackageName(), 0);
            return pInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            logger.error("Error retrieving application's version code, using SDK's", e);
            return BuildConfig.VERSION_CODE;
        }
    }

    @NonNull
    public String getAppVersionName() {
        try {
            PackageInfo pInfo = applicationContext.getPackageManager()
                    .getPackageInfo(applicationContext.getPackageName(), 0);
            return pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            logger.error("Error retrieving application's version name, using SDK's", e);
            return BuildConfig.VERSION_NAME;
        }
    }

    @NonNull
    public X509Certificate getPublicKey() throws IOException, CertificateException {
        try (InputStream publicKeyFile = applicationContext.getAssets().open(STUDY_PUBLIC_KEY,
                ACCESS_BUFFER)) {
            return (X509Certificate) new CertificateFactory().engineGenerateCertificate(
                    publicKeyFile);
        } catch(IOException e) {
            logger.error("Could not load public key from /assets/study_public_key.pem", e);
            throw e;
        }
    }

    /**
     * Mapping from task ID to schema key (ID and revision). This is obtained from
     * assets/task_to_schema.json in your app. This will never return null, but may return an
     * empty map.
     */
    @NonNull
    public Map<String, SchemaKey> getTaskToSchemaMap() {
        return taskToSchemaMap;
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

    /**
     * Gets the account's email address for the user's external ID, based on app config.
     *
     * @param externalId user's external ID
     * @return account's email address
     */
    public String getEmailForExternalId(String externalId) {
        if (externalIdEmailFormat != null) {
            return String.format(externalIdEmailFormat, externalId);
        } else {
            throw new UnsupportedOperationException("Credentials for external ID require asset file " +
                    EXTERNAL_ID_SETTINGS_FILENAME);
        }
    }

    /**
     * Gets the account's password for the user's external ID, based on app config.
     *
     * @param externalId user's external ID
     * @return account's password
     */
    public String getPasswordForExternalId(String externalId) {
        if (externalIdPasswordFormat != null) {
            return String.format(externalIdPasswordFormat, externalId);
        } else {
            throw new UnsupportedOperationException("Credentials for external ID require asset file " +
                    EXTERNAL_ID_SETTINGS_FILENAME);
        }
    }
}
