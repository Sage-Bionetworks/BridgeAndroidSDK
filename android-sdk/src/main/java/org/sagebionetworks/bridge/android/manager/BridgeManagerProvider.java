package org.sagebionetworks.bridge.android.manager;

import android.app.Application;
import android.content.Context;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;

import org.sagebionetworks.bridge.android.BridgeConfig;
import org.sagebionetworks.bridge.android.data.StudyUploadEncryptor;
import org.sagebionetworks.bridge.android.manager.dao.AccountDAO;
import org.sagebionetworks.bridge.android.manager.dao.ConsentDAO;
import org.sagebionetworks.bridge.rest.ApiClientProvider;

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

        authenticationManager = new AuthenticationManager(bridgeConfig, apiClientProvider, accountDAO);
        participantManager = new ParticipantManager(authenticationManager, accountDAO);
        consentManager = new ConsentManager(authenticationManager, consentDAO);

        try {
            studyUploadEncryptor = new StudyUploadEncryptor(bridgeConfig.getPublicKey());
        } catch (Exception e) {
            throw new RuntimeException("Could create StudyUploadEncryptor", e);
        }

        uploadManager = new UploadManager(authenticationManager, studyUploadEncryptor);
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
    private final ParticipantManager participantManager;
    @NonNull
    private final ConsentManager consentManager;
    @NonNull
    private final ConsentDAO consentDAO;
    @NonNull
    private final AccountDAO accountDAO;
    @NonNull
    private final StudyUploadEncryptor studyUploadEncryptor;
    @NonNull
    private final UploadManager uploadManager;

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
    public ParticipantManager getParticipantManager() {
        return participantManager;
    }

    @NonNull
    public ConsentManager getConsentManager() {
        return consentManager;
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
    public StudyUploadEncryptor getStudyUploadEncryptor() {
        return studyUploadEncryptor;
    }

}
