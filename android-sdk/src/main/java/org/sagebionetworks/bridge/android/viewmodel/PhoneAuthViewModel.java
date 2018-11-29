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

package org.sagebionetworks.bridge.android.viewmodel;

import static com.google.common.base.Preconditions.checkArgument;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;
import android.arch.lifecycle.ViewModelProvider;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;

import com.google.common.base.Strings;

import org.sagebionetworks.bridge.android.access.Resource;
import org.sagebionetworks.bridge.android.manager.AuthenticationManager;
import org.sagebionetworks.bridge.android.manager.dao.AccountDAO;
import org.sagebionetworks.bridge.rest.model.Phone;
import org.sagebionetworks.bridge.rest.model.UserSessionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

import rx.subscriptions.CompositeSubscription;

public class PhoneAuthViewModel extends ViewModel {
    private static final Logger LOGGER = LoggerFactory.getLogger(PhoneAuthViewModel.class);

    private final CompositeSubscription compositeSubscription = new CompositeSubscription();

    private final AuthenticationManager authenticationManager;

    private final AccountDAO accountDAO;

    private final MutableLiveData<Resource<Object>> signInStateLiveData;

    public PhoneAuthViewModel(
            final AuthenticationManager authenticationManager,
            final AccountDAO accountDAO) {
        this.authenticationManager = authenticationManager;
        this.accountDAO = accountDAO;
        signInStateLiveData = new MutableLiveData<>();
    }

    public LiveData<Resource<Object>> getSignInStateLiveData() {
        return signInStateLiveData;
    }

    public void signInWithToken(String token) {
        String phoneRegion = accountDAO.getPhoneRegion();
        String phoneNumber = accountDAO.getPhoneNumber();

        boolean newSignIn = false;
        newSignIn = isNewSignIn(phoneRegion, phoneNumber);

        // either we have no current session, or current session is for a different phone number
        if (newSignIn) {
            doPhoneSignIn(token, phoneRegion, phoneNumber);
        } else {
            refreshSessionOrSignIn(token, phoneRegion, phoneNumber);
        }
    }

    @VisibleForTesting
    void refreshSessionOrSignIn(String token, String phoneRegion, String phoneNumber) {
        compositeSubscription.add(
                authenticationManager.getLatestUserSessionInfo()
                        .subscribe(session -> {
                            LOGGER.debug("Session renewal succeeded.");
                            // current session is still useable
                            signInStateLiveData.postValue(Resource.success(new Object()));
                        }, t -> {
                            LOGGER.debug("Session renewal failed, signing in.");
                            doPhoneSignIn(token, phoneRegion, phoneNumber);
                        }));
    }

    @VisibleForTesting
    boolean isNewSignIn(final String phoneRegion, final String phoneNumber) {
        final boolean newSignIn;UserSessionInfo currentSession = authenticationManager.getUserSessionInfo();
        if (currentSession == null) {
            newSignIn = true;
        } else {
            // TODO: may not be reliable since session does not always contain phone
            // but that just means we will sign in again
            Phone phone = currentSession.getPhone();
            newSignIn = !isSamePhone(phone, phoneRegion, phoneNumber);
        }
        return newSignIn;
    }

    @VisibleForTesting
    boolean isSamePhone(Phone expected, String phoneRegion, String phoneNumber) {
        return expected != null && expected.getRegionCode().equals(phoneRegion)
                && phoneNumber != null && expected.getNumber().endsWith(phoneNumber);
    }

    @VisibleForTesting
    void doPhoneSignIn(String token, String phoneRegion, String phoneNumber) {
        if (Strings.isNullOrEmpty(token)) {
            LOGGER.error("Received null or empty token");

            signInStateLiveData.postValue(
                    Resource.error("Unable to process sign in link. Please sign in again to get a new link by SMS",
                            new Object()));
            return;
        }

        if (Strings.isNullOrEmpty(phoneRegion) || Strings.isNullOrEmpty(phoneNumber)) {
            LOGGER.warn("Phone number and region are required and were not found in accountDAO");

            signInStateLiveData.postValue(
                    Resource.error("Phone number unknown. Please sign in again to get a new link by SMS",
                            new Object()));
            return;
        }

        compositeSubscription.add(authenticationManager
                .signInViaPhoneLink(phoneRegion, phoneNumber, token)
                .subscribe(session -> {
                    LOGGER.debug("TokenLoginSubscribe: Authenticated Login Complete");
                    signInStateLiveData.postValue(Resource.success(new Object()));
                }, throwable -> {
                    LOGGER.warn("Sign up failed", throwable);

                    signInStateLiveData.postValue(
                            Resource.error("Error signing in: \n" + throwable.getMessage(),
                                    new Object()));
                }));
    }

    @Override
    protected void onCleared() {
        compositeSubscription.clear();
    }

    public static class Factory implements ViewModelProvider.Factory {
        private final AuthenticationManager authenticationManager;

        private final AccountDAO accountDAO;

        @Inject
        public Factory(
                final AuthenticationManager authenticationManager,
                final AccountDAO accountDAO) {
            this.authenticationManager = authenticationManager;
            this.accountDAO = accountDAO;
        }

        @NonNull
        @Override
        @SuppressWarnings(value = "unchecked")
        public <T extends ViewModel> T create(@NonNull final Class<T> modelClass) {
            checkArgument(modelClass.isAssignableFrom(PhoneAuthViewModel.class));

            return (T) new PhoneAuthViewModel(authenticationManager, accountDAO);
        }
    }
}