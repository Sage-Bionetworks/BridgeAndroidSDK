package org.sagebionetworks.bridge.android.manager;

import android.content.Context;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;

import org.sagebionetworks.bridge.android.BridgeConfig;
import org.sagebionetworks.bridge.android.manager.auth.AuthenticationManager;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by jyliu on 1/20/2017.
 */
@AnyThread
public class BridgeManagerProvider {
    private static BridgeManagerProvider instance;

    @NonNull
    public static BridgeManagerProvider getInstance(@NonNull Context context) {
        if (instance == null) {
            instance = new BridgeManagerProvider(new BridgeConfig(context));
        }

        return instance;
    }

    private BridgeManagerProvider(@NonNull BridgeConfig config) {
        checkNotNull(config);

        this.config = config;
        this.authenticationManager = new AuthenticationManager(config);
        this.studyParticipantManager = new StudyParticipantManager(authenticationManager);
        this.consentManager = new ConsentManager(this.authenticationManager.getApi());
    }

    @NonNull
    private final BridgeConfig config;
    @NonNull
    private final AuthenticationManager authenticationManager;
    @NonNull
    private final StudyParticipantManager studyParticipantManager;
    @NonNull
    private final ConsentManager consentManager;

    @NonNull
    public AuthenticationManager getAuthenticationManager() {
        return authenticationManager;
    }

    @NonNull
    public StudyParticipantManager getStudyParticipantManager() {
        return studyParticipantManager;
    }

    @NonNull
    public ConsentManager getConsentManager() { return consentManager; }
}
