package org.sagebionetworks.bridge.android.manager;

import android.app.Application;
import android.content.Context;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;

import org.sagebionetworks.bridge.android.BridgeConfig;
import org.sagebionetworks.bridge.android.manager.dao.AccountDAO;
import org.sagebionetworks.bridge.android.manager.dao.ConsentDAO;
import org.sagebionetworks.bridge.android.manager.dao.UploadDAO;
import org.sagebionetworks.bridge.data.AndroidStudyUploadEncryptor;
import org.sagebionetworks.bridge.rest.ApiClientProvider;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * This class contains configuration and performs setup of dependencies for a Bridge Application.
 * <p>
 * A call to {@link #getInstance()} returns a BridgeManagerProvider instance that is usable
 * globally. The managers made available by BridgeManagerProvider maintain and share state across
 * the Application.
 */
@AnyThread
public class BridgeManagerProvider {
    private static BridgeManagerProvider instance;

    /**
     * @return singleton instance
     */
    @NonNull
    public static BridgeManagerProvider getInstance() {
        checkState(instance != null, "BridgeManagerProvider has not been initialized. " +
                "Call init(Context) in your Application#onCreate()");

        return instance;
    }

    /**
     * Allows injection of singleton instance for testing purposes.
     *
     * @param bridgeManagerProvider instance to be returned by {@link #getInstance()}
     */
    public static void init(@NonNull BridgeManagerProvider bridgeManagerProvider) {
        checkNotNull(bridgeManagerProvider);
        instance = bridgeManagerProvider;
    }

    /**
     * Intended to be called in {@link Application#onCreate()}
     *
     * @param applicationContext application's global context
     */
    public static void init(@NonNull Context applicationContext) {
        checkNotNull(applicationContext);
        checkState(instance == null, "BridgeManagerProvider has already been initialized");

        instance = new BridgeManagerProvider(applicationContext);
    }

    private BridgeManagerProvider(@NonNull Context applicationContext) {
        this.applicationContext = applicationContext;
        bridgeConfig = new BridgeConfig(this.applicationContext);

        apiClientProvider = new ApiClientProvider(
                bridgeConfig.getBaseUrl(),
                bridgeConfig.getUserAgent(),
                bridgeConfig.getAcceptLanguage());

        accountDAO = new AccountDAO(applicationContext);
        consentDAO = new ConsentDAO(applicationContext);
        uploadDAO = new UploadDAO(applicationContext);

        authenticationManager = new AuthenticationManager(bridgeConfig, apiClientProvider, accountDAO, consentDAO);
        participantManager = new ParticipantRecordManager(accountDAO, authenticationManager);

        activityManager = new ActivityManager(authenticationManager);

        try {
            studyUploadEncryptor = new AndroidStudyUploadEncryptor(bridgeConfig.getPublicKey());
        } catch (Exception e) {
            throw new RuntimeException("Could not create StudyUploadEncryptor", e);
        }

        s3OkHttpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false).build();

        uploadManager = new UploadManager(authenticationManager, studyUploadEncryptor, uploadDAO);
    }

    @NonNull
    private final Context applicationContext;
    @NonNull
    private final BridgeConfig bridgeConfig;
    @NonNull
    private final ApiClientProvider apiClientProvider;
    @NonNull
    private final AuthenticationManager authenticationManager;
    @NonNull
    private final ParticipantRecordManager participantManager;
    @NonNull
    private final ActivityManager activityManager;
    @NonNull
    private final ConsentDAO consentDAO;
    @NonNull
    private final AccountDAO accountDAO;
    @NonNull
    private final UploadDAO uploadDAO;
    @NonNull
    private final AndroidStudyUploadEncryptor studyUploadEncryptor;
    @NonNull
    private final UploadManager uploadManager;
    @NonNull
    private final OkHttpClient s3OkHttpClient;

    @NonNull
    public Context getApplicationContext() {
        return applicationContext;
    }

    @NonNull
    public BridgeConfig getBridgeConfig() {
        return bridgeConfig;
    }

    @NonNull
    public ApiClientProvider getApiClientProvider() {
        return apiClientProvider;
    }

    @NonNull
    public AuthenticationManager getAuthenticationManager() {
        return authenticationManager;
    }

    @NonNull
    public ActivityManager getActivityManager() {
        return activityManager;
    }

    @NonNull
    public AccountDAO getAccountDao() {
        return accountDAO;
    }

    @NonNull
    public ConsentDAO getConsentDao() {
        return consentDAO;
    }

    @NonNull
    public UploadManager getUploadManager() {
        return uploadManager;
    }

    @NonNull
    public AndroidStudyUploadEncryptor getStudyUploadEncryptor() {
        return studyUploadEncryptor;
    }

    @NonNull
    public OkHttpClient getS3OkHttpClient() {
        return s3OkHttpClient;
    }

    public ParticipantRecordManager getParticipantManager() {
        return participantManager;
    }
}
