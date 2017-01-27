package org.sagebionetworks.bridge.researchstack;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sagebionetworks.bridge.android.manager.BridgeManagerProvider;
import org.sagebionetworks.bridge.android.manager.StudyParticipantManager;
import org.sagebionetworks.bridge.android.manager.auth.AuthenticationManager;
import org.sagebionetworks.bridge.rest.model.SharingScope;
import org.sagebionetworks.bridge.rest.model.UserSessionInfo;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Response;
import rx.Completable;
import rx.Single;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Created by liujoshua on 9/12/16.
 */
public class BridgeDataProvider2Test {
    @Mock
    private BridgeManagerProvider bridgeManagerProvider;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private AuthenticationManager.DAO authenticationDAO;
    @Mock
    private StudyParticipantManager studyParticipantManager;
    @Mock
    private Context context;

    private BridgeDataProvider2 dataProvider;

    @Before
    public void beforeTest() {
        MockitoAnnotations.initMocks(this);

        when(authenticationManager.getDao()).thenReturn(authenticationDAO);

        when(bridgeManagerProvider.getAuthenticationManager()).thenReturn(authenticationManager);
        when(bridgeManagerProvider.getStudyParticipantManager()).thenReturn
                (studyParticipantManager);

        dataProvider =
                new BridgeDataProvider2(bridgeManagerProvider);

    }

    @Test
    public void testSignIn() throws IOException {
        Call<UserSessionInfo> sessionCall = mock(Call.class);
        UserSessionInfo session = mock(UserSessionInfo.class);

        when(sessionCall.clone()).thenReturn(sessionCall);
        when(sessionCall.execute()).thenReturn(Response.success(session));

        when(authenticationManager.signIn("email", "password")).thenReturn(Single.just(session));
        Completable completable =
                dataProvider.signIn("email", "password");
        completable.test().assertCompleted();

        verify(authenticationManager).signIn("email", "password");

        // TODO: verify background tasks are triggered when session is established
        // verify(uploadHandler).uploadPendingFiles(forConsentedUsersApi);
    }

    @Test
    public void testSignOut() throws IOException {
        when(authenticationManager.signOut()).thenReturn(Completable.complete());

        Completable completable = dataProvider.signOut();

        completable.test().assertCompleted();

        verify(authenticationManager).signOut();
    }

    @Test
    public void testGetUserSharingScope() {
        UserSessionInfo session = mock(UserSessionInfo.class);
        String scope = "SPONSORS_AND_PARTNERS";
        when(session.getSharingScope()).thenReturn(SharingScope.valueOf(scope));
        when(authenticationDAO.getUserSessionInfo()).thenReturn(session);
        //TODO: deprecate and remove

        String scopeResult = dataProvider.getUserSharingScope();

        assertEquals("sponsors_and_partners", scopeResult);
        verify(authenticationDAO).getUserSessionInfo();
        verify(session, atLeastOnce()).getSharingScope();
    }
}