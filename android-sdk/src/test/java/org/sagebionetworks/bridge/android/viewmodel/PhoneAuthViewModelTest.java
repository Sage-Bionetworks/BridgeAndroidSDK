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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sagebionetworks.bridge.android.access.Resource;
import org.sagebionetworks.bridge.android.access.Resource.Status;
import org.sagebionetworks.bridge.android.manager.AuthenticationManager;
import org.sagebionetworks.bridge.android.manager.dao.AccountDAO;
import org.sagebionetworks.bridge.rest.model.Phone;
import org.sagebionetworks.bridge.rest.model.UserSessionInfo;

import rx.Single;


public class PhoneAuthViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock
    AuthenticationManager authenticationManager;

    @Mock
    AccountDAO accountDAO;

    private PhoneAuthViewModel phoneAuthViewModel;

    private LiveData<Resource<Object>> signInStateLiveData;

    private final String token = "abcdef";

    private final String region = "US";

    private final String number = "1234567890";

    private final Phone phone = new Phone().regionCode(region).number(number);

    @Before
    public void setupTest() {
        MockitoAnnotations.initMocks(this);

        phoneAuthViewModel = new PhoneAuthViewModel(authenticationManager, accountDAO);

        signInStateLiveData = phoneAuthViewModel.getSignInStateLiveData();
    }

    @Test
    public void signInWithToken_newSignIn() {
        PhoneAuthViewModel spyVM = spy(phoneAuthViewModel);

        when(accountDAO.getPhoneRegion()).thenReturn(region);
        when(accountDAO.getPhoneNumber()).thenReturn(number);

        doReturn(true).when(spyVM).isNewSignIn(region, number);
        doNothing().when(spyVM).doPhoneSignIn(token, region, number);

        // test action
        spyVM.signInWithToken(token);

        verify(spyVM).doPhoneSignIn(token, region, number);
    }

    @Test
    public void signInWithToken_oldSignIn() {
        PhoneAuthViewModel spyVM = spy(phoneAuthViewModel);

        when(accountDAO.getPhoneRegion()).thenReturn(region);
        when(accountDAO.getPhoneNumber()).thenReturn(number);

        doReturn(false).when(spyVM).isNewSignIn(region, number);
        doNothing().when(spyVM).doPhoneSignIn(token, region, number);
        doNothing().when(spyVM).refreshSessionOrSignIn(token, region, number);

        // test action
        spyVM.signInWithToken(token);

        verify(spyVM, times(0)).doPhoneSignIn(token, region, number);
        verify(spyVM).refreshSessionOrSignIn(token, region, number);
    }

    @Test
    public void refreshSessionOrSignIn_alwaysSignIn() {
        PhoneAuthViewModel spyVM = spy(phoneAuthViewModel);

        doNothing().when(spyVM).doPhoneSignIn(token, region, number);

        spyVM.refreshSessionOrSignIn(token, region, number);

        verify(spyVM).doPhoneSignIn(token, region, number);
    }

    @Test
    public void doPhoneSignIn_noToken() {
        phoneAuthViewModel.doPhoneSignIn("", region, number);

        assertEquals(Status.ERROR, signInStateLiveData.getValue().status);
    }

    @Test
    public void doPhoneSignIn_noNumber() {
        phoneAuthViewModel.doPhoneSignIn(token, region, "");

        assertEquals(Status.ERROR, signInStateLiveData.getValue().status);
    }

    @Test
    public void doPhoneSignIn_apiSuccess() {
        UserSessionInfo userSessionInfo = new UserSessionInfo();
        when(authenticationManager.signInViaPhoneLink(region, number, token))
                .thenReturn(Single.just(userSessionInfo));

        phoneAuthViewModel.doPhoneSignIn(token, region, number);

        verify(authenticationManager).signInViaPhoneLink(region, number, token);

        assertEquals(Status.SUCCESS, signInStateLiveData.getValue().status);
    }

    @Test
    public void doPhoneSignIn_apiFailure() {
        when(authenticationManager.signInViaPhoneLink(region, number, token))
                .thenReturn(Single.error(new Exception()));

        phoneAuthViewModel.doPhoneSignIn(token, region, number);

        verify(authenticationManager).signInViaPhoneLink(region, number, token);

        assertEquals(Status.ERROR, signInStateLiveData.getValue().status);
    }

    @Test
    public void isSamePhone_nulls() {
        assertFalse(phoneAuthViewModel.isSamePhone(null, region, number));

        assertFalse(phoneAuthViewModel.isSamePhone(phone, null, number));

        assertFalse(phoneAuthViewModel.isSamePhone(phone, region, null));
    }
}