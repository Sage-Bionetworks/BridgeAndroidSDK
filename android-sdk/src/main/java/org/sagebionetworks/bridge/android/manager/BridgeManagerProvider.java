package org.sagebionetworks.bridge.android.manager;

import android.app.Application;
import android.content.Context;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

import org.sagebionetworks.bridge.android.BridgeConfig;
import org.sagebionetworks.bridge.android.manager.auth.AuthenticationManager;

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

        this.bridgeConfig = new BridgeConfig(this.applicationContext);
        this.authenticationManager = Suppliers.memoize(this::createAuthenticationManager);
        this.studyParticipantManager = Suppliers.memoize(this::createStudyParticipantManager);
        this.consentManager = Suppliers.memoize(this::createConsentManager);
    }

    @NonNull
    private final Context applicationContext;
    @NonNull
    private final BridgeConfig bridgeConfig;
    @NonNull
    private final Supplier<AuthenticationManager> authenticationManager;
    @NonNull
    private final Supplier<StudyParticipantManager> studyParticipantManager;
    @NonNull
    private final Supplier<ConsentManager> consentManager;

    @NonNull
    public BridgeConfig getBridgeConfig() {
        return bridgeConfig;
    }

    @NonNull
    public AuthenticationManager getAuthenticationManager() {
        return authenticationManager.get();
    }

    private AuthenticationManager createAuthenticationManager() {
        return new AuthenticationManager(getBridgeConfig());
    }

    @NonNull
    public StudyParticipantManager getStudyParticipantManager() {
        return studyParticipantManager.get();
    }

    private StudyParticipantManager createStudyParticipantManager() {
        return new StudyParticipantManager(getAuthenticationManager());
    }

    @NonNull
    public ConsentManager getConsentManager() {
        return consentManager.get();
    }

    private ConsentManager createConsentManager() {
        return new ConsentManager(getAuthenticationManager());
    }
}
