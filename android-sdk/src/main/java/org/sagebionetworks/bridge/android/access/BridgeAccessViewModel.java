/*
 * BSD 3-Clause License
 *
 * Copyright 2018  Sage Bionetworks. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1.  Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2.  Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3.  Neither the name of the copyright holder(s) nor the names of any contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission. No license is granted to the trademarks of
 * the copyright holders even if such marks are included in this software.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.sagebionetworks.bridge.android.access;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;
import android.arch.lifecycle.ViewModelProvider;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;

import org.sagebionetworks.bridge.android.BridgeConfig;
import org.sagebionetworks.bridge.android.manager.AuthenticationManager;
import org.sagebionetworks.bridge.android.receiver.AppVersionManager;
import org.sagebionetworks.bridge.rest.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.rest.model.SharingScope;
import org.sagebionetworks.bridge.rest.model.UserSessionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

import rx.subscriptions.CompositeSubscription;

/**
 * ViewModel representing Bridge access rules.
 *
 * <li>Check App Version</li>
 * <li>Check Authentication</li>
 * <li>Check Consent</li>
 */
public class BridgeAccessViewModel extends ViewModel {
    private static final Logger LOGGER = LoggerFactory.getLogger(BridgeAccessViewModel.class);

    private final AuthenticationManager authenticationManager;

    private final AppVersionManager appVersionManager;

    private final BridgeConfig bridgeConfig;

    private final CompositeSubscription compositeSubscription;

    private final MutableLiveData<Resource<BridgeAccessState>> bridgeAccessStatusMutableLiveData;

    public static class Factory implements ViewModelProvider.Factory {
        private final AuthenticationManager authenticationManager;

        private final AppVersionManager appVersionManager;

        private final BridgeConfig bridgeConfig;

        @Inject
        public Factory(
                @NonNull final AuthenticationManager authenticationManager,
                @NonNull final AppVersionManager appVersionManager,
                @NonNull final BridgeConfig bridgeConfig) {
            this.authenticationManager = checkNotNull(authenticationManager);
            this.appVersionManager = checkNotNull(appVersionManager);
            this.bridgeConfig = checkNotNull(bridgeConfig);
        }

        @NonNull
        @Override
        @SuppressWarnings(value = "unchecked")
        public <T extends ViewModel> T create(@NonNull final Class<T> modelClass) {
            checkArgument(modelClass.isAssignableFrom(BridgeAccessViewModel.class));
            return (T) new BridgeAccessViewModel(authenticationManager, appVersionManager, bridgeConfig);
        }
    }

    public BridgeAccessViewModel(
            @NonNull final AuthenticationManager authenticationManager,
            @NonNull final AppVersionManager appVersionManager,
            @NonNull final BridgeConfig bridgeConfig) {
        this.authenticationManager = checkNotNull(authenticationManager);
        this.appVersionManager = checkNotNull(appVersionManager);
        this.bridgeConfig = checkNotNull(bridgeConfig);

        compositeSubscription = new CompositeSubscription();
        bridgeAccessStatusMutableLiveData = new MutableLiveData<>();
    }

    /**
     * Performs checks for Bridge access rules.
     */
    public void checkAccess() {
        checkAppVersion();
    }

    @VisibleForTesting
    void checkAppVersion() {
        // Loading BridgeAccessState, currently checking value of REQUIRES_APP_UPGRADE
        bridgeAccessStatusMutableLiveData.postValue(
                Resource.loading(BridgeAccessState.REQUIRES_APP_UPGRADE)
        );

        if (appVersionManager.isUpgradeRequired()) {
            // Successfully determined BridgeAccessState is REQUIRES_APP_UPGRADE
            bridgeAccessStatusMutableLiveData.postValue(
                    Resource.success(BridgeAccessState.REQUIRES_APP_UPGRADE)
            );
        } else {
            checkAuth();
        }
    }

    @VisibleForTesting
    void checkAuth() {
        UserSessionInfo userSessionInfo = authenticationManager.getUserSessionInfo();
        boolean authenticated = userSessionInfo != null && userSessionInfo.isAuthenticated();

        // Loading BridgeAccessState, currently checking value of REQUIRES_AUTHENTICATION
        bridgeAccessStatusMutableLiveData.postValue(
                Resource.loading(BridgeAccessState.REQUIRES_AUTHENTICATION)
        );
        BridgeAccessState state;
        if (!authenticated) {
            // Determined BridgeAccessState is REQUIRES_AUTHENTICATION
            bridgeAccessStatusMutableLiveData.postValue(
                    Resource.success(BridgeAccessState.REQUIRES_AUTHENTICATION)
            );
        } else {
            checkConsent();
        }
    }

    @VisibleForTesting
    void checkConsent() {
        boolean consented = authenticationManager.isConsented();

        // Loading BridgeAccessState, currently checking value of REQUIRES_CONSENT
        bridgeAccessStatusMutableLiveData.postValue(
                Resource.loading(BridgeAccessState.REQUIRES_CONSENT)
        );

        BridgeAccessState state;
        if (!consented) {
            // Determined BridgeAccessState is REQUIRES_CONSENT
            bridgeAccessStatusMutableLiveData.postValue(
                    Resource.success(BridgeAccessState.REQUIRES_CONSENT)
            );
        } else {
            // Determined BridgeAccessState is ACCESS_GRANTED
            bridgeAccessStatusMutableLiveData.postValue(
                    Resource.success(BridgeAccessState.ACCESS_GRANTED)
            );
        }
    }

    @AnyThread
    public void consentsToResearch(@NonNull String participantName, @NonNull SharingScope sharingScope) {
        LOGGER.debug("consentsToResearch called");

        bridgeAccessStatusMutableLiveData.postValue(
                Resource.loading(BridgeAccessState.REQUIRES_CONSENT)
        );

        compositeSubscription.add(
                authenticationManager.giveConsent(
                        bridgeConfig.getStudyId(),
                        participantName,
                        null, null, null,
                        sharingScope

                )
                        .subscribe(this::onConsentSuccess, this::onConsentFailure)
        );
    }

    /**
     * Contains latest information about Bridge access rules related to this application.
     *
     * @return LiveData of a Resource containing BridgeAccessState
     */
    public MutableLiveData<Resource<BridgeAccessState>> getBridgeAccessStatus() {
        return bridgeAccessStatusMutableLiveData;
    }

    @VisibleForTesting
    void onConsentSuccess(UserSessionInfo userSessionInfo) {
        LOGGER.debug("consent upload success");

        bridgeAccessStatusMutableLiveData.postValue(
                Resource.success(BridgeAccessState.ACCESS_GRANTED)
        );
    }

    @VisibleForTesting
    void onConsentFailure(Throwable t) {
        if (t instanceof EntityAlreadyExistsException) {
            LOGGER.warn("consent already exists");

            bridgeAccessStatusMutableLiveData.postValue(
                    Resource.success(BridgeAccessState.ACCESS_GRANTED)
            );
        } else {
            LOGGER.warn("consent upload error", t);

            bridgeAccessStatusMutableLiveData.postValue(
                    Resource.error(t.getLocalizedMessage(), BridgeAccessState.REQUIRES_CONSENT)
            );
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        compositeSubscription.clear();
    }
}