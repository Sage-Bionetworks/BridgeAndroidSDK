package org.sagebionetworks.bridge.android.manager;

import android.content.Context;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;

import org.sagebionetworks.bridge.android.BridgeConfig;
import org.sagebionetworks.bridge.android.manager.auth.AuthManager;

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

    @NonNull
    private final BridgeConfig config;
    @NonNull
    private final AuthManager authManager;
    @NonNull
    private final StudyParticipantManager studyParticipantManager;

    private BridgeManagerProvider(@NonNull BridgeConfig config) {
        checkNotNull(config);

        this.config = config;
        this.authManager = new AuthManager(config);
        this.studyParticipantManager = new StudyParticipantManager(authManager);
    }

    @NonNull
    public AuthManager getAuthManager() {
        return this.authManager;
    }

    @NonNull
    public StudyParticipantManager getStudyParticipantManager() {
        return studyParticipantManager;
    }
}
