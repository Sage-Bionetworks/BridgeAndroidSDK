package org.sagebionetworks.bridge.android.manager;

import android.app.Application;
import android.content.Context;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

import org.sagebionetworks.bridge.android.BridgeConfig;
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
        apiClientProviderSupplier = Suppliers.memoize(this::createApiClientProvider);
        authenticationManagerSupplier = Suppliers.memoize(this::createAuthenticationManager);
        participantManagerSupplier = Suppliers.memoize(this::createStudyParticipantManager);
        consentManagerSupplier = Suppliers.memoize(this::createConsentManager);
        plaintextSharedPrefsDAOSupplier = Suppliers.memoize(this::createSharedPreferencesDAO);

    }

    @NonNull
    private final Context applicationContext;
    @NonNull
    private final BridgeConfig bridgeConfig;
    @NonNull
    private final Supplier<ApiClientProvider> apiClientProviderSupplier;
    @NonNull
    private final Supplier<AuthenticationManager> authenticationManagerSupplier;
    @NonNull
    private final Supplier<ParticipantManager> participantManagerSupplier;
    @NonNull
    private final Supplier<ConsentManager> consentManagerSupplier;
    @NonNull
    private final Supplier<PlaintextSharedPreferencesDAO> plaintextSharedPrefsDAOSupplier;

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
        return apiClientProviderSupplier.get();
    }

    private ApiClientProvider createApiClientProvider() {
        BridgeConfig config = getBridgeConfig();

        return new ApiClientProvider(
                config.getBaseUrl(),
                config.getUserAgent(),
                config.getAcceptLanguage());
    }

    @NonNull
    public AuthenticationManager getAuthenticationManager() {
        return authenticationManagerSupplier.get();
    }

    private AuthenticationManager createAuthenticationManager() {
        return new AuthenticationManager(getBridgeConfig(), getApiClientProvider(), getAccountDao());
    }

    @NonNull
    public ParticipantManager getParticipantManager() {
        return participantManagerSupplier.get();
    }

    private ParticipantManager createStudyParticipantManager() {
        return new ParticipantManager(getAuthenticationManager(), getAccountDao());
    }

    @NonNull
    public ConsentManager getConsentManager() {
        return consentManagerSupplier.get();
    }

    private ConsentManager createConsentManager() {
        return new ConsentManager(getAuthenticationManager(), getConsentDao());
    }

    @NonNull
    public AccountDAO getAccountDao() {
        return plaintextSharedPrefsDAOSupplier.get();
    }

    @NonNull
    public ConsentDAO getConsentDao() {
        return plaintextSharedPrefsDAOSupplier.get();
    }

    private PlaintextSharedPreferencesDAO createSharedPreferencesDAO() {
        return new PlaintextSharedPreferencesDAO(applicationContext);
    }
}
