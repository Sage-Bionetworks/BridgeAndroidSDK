package org.sagebionetworks.bridge.android.manager;

import android.content.Context;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;

import org.sagebionetworks.bridge.android.BridgeConfig;
import org.sagebionetworks.bridge.android.manager.auth.AuthenticationManager;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Created by jyliu on 1/20/2017.
 */
@AnyThread
public class BridgeManagerProvider {
    private static BridgeManagerProvider instance;

    @NonNull
    public static BridgeManagerProvider getInstance() {
        checkState(instance != null, "BridgeManagerProvider has not been initialized");

        return instance;
    }

    // allow injection of singleton instance for testing purposes
    public static void init(@NonNull BridgeManagerProvider bridgeManagerProvider) {
        checkNotNull(bridgeManagerProvider);
        instance = bridgeManagerProvider;
    }

    public static void init(@NonNull Context applicationContext) {
        checkNotNull(applicationContext);
        checkState(instance == null, "BridgeManagerProvider has already been initialized");

        instance = new BridgeManagerProvider(new BridgeConfig(applicationContext.getApplicationContext()));
    }

    private BridgeManagerProvider(@NonNull BridgeConfig bridgeConfig) {
        checkNotNull(bridgeConfig);

        this.bridgeConfig = bridgeConfig;
        this.authenticationManager = new AuthenticationManager(bridgeConfig);
        this.studyParticipantManager = new StudyParticipantManager(authenticationManager);
        this.consentManager = new ConsentManager(authenticationManager);
    }

    @NonNull
    private final BridgeConfig bridgeConfig;
    @NonNull
    private final AuthenticationManager authenticationManager;
    @NonNull
    private final StudyParticipantManager studyParticipantManager;
    @NonNull
    private final ConsentManager consentManager;

    @NonNull
    public BridgeConfig getBridgeConfig() { return bridgeConfig;}

    @NonNull
    public AuthenticationManager getAuthenticationManager() {
        return authenticationManager;
    }

    @NonNull
    public StudyParticipantManager getStudyParticipantManager() {
        return studyParticipantManager;
    }

    @NonNull
    public ConsentManager getConsentManager() {
        return consentManager;
    }
}
