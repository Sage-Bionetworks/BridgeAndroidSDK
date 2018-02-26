package org.sagebionetworks.bridge.android.manager;

import android.content.Context;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sagebionetworks.bridge.android.BridgeConfig;
import org.sagebionetworks.bridge.rest.ApiClientProvider;
import org.sagebionetworks.bridge.rest.api.ForConsentedUsersApi;
import org.sagebionetworks.bridge.rest.exceptions.BridgeSDKException;
import org.sagebionetworks.bridge.rest.model.Message;
import org.sagebionetworks.bridge.rest.model.ScheduledActivity;
import org.sagebionetworks.bridge.rest.model.ScheduledActivityList;
import org.sagebionetworks.bridge.rest.model.ScheduledActivityListV4;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import retrofit2.Call;
import retrofit2.Response;
import rx.Completable;
import rx.Single;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Created by rianhouston on 2/12/17.
 */

public class ActivityManagerTest {
    
    @Mock
    private BridgeConfig config;
    @Mock
    private ApiClientProvider apiClientProvider;
    @Mock
    private ForConsentedUsersApi activitiesApi;
    @Mock
    private AuthenticationManager authenticationManager;

    private ActivityManager activityManager;

    @Mock
    private Context context;


    @Before
    public void beforeTest() {
        MockitoAnnotations.initMocks(this);

        when(authenticationManager.getAuthStateReference())
                .thenReturn(new AtomicReference<>(
                        new AuthenticationManager.AuthStateHolder(activitiesApi, null)));

        activityManager = new ActivityManager(authenticationManager, context);
    }
    
    @Test
    public void testGetActivities_differentOffsets() throws Exception {

    
        Call<ScheduledActivityListV4> activityCall = errorCall(new BridgeSDKException("Failed",
                500));
        when(activitiesApi.getScheduledActivitiesByDateRange(any(), any())).thenReturn(activityCall);
    
        DateTime start = DateTime.parse("2018-03-09T23:00:00.000-08:00");
        DateTime end = DateTime.parse("2018-03-14T23:00:00.000-07:00");
        activityManager.getActivities(start, end);
    
        DateTime newEnd = DateTime.parse("2018-03-14T22:00:00.000-08:00");
        verify(activitiesApi).getScheduledActivitiesByDateRange(start, newEnd);
    }
    
    @Test
    public void getActivities_success() throws IOException {
        ScheduledActivityListV4 list = mock(ScheduledActivityListV4.class);
        Call<ScheduledActivityListV4> activityCall = successCall(list);

        DateTime start = new DateTime().minusDays(1);
        DateTime end = new DateTime().plusDays(1);
        when(activitiesApi.getScheduledActivitiesByDateRange(start, end)).thenReturn(activityCall);

        Single<ScheduledActivityListV4> single = activityManager.getActivities(start, end);
        single.test().awaitTerminalEvent().assertCompleted();

        verify(activitiesApi).getScheduledActivitiesByDateRange(start, end);
    }

    @Test
    public void getActivities_failure() throws IOException {
        Call<ScheduledActivityListV4> activityCall = errorCall(new BridgeSDKException("Failed",
                500));

        DateTime start = new DateTime().minusDays(1);
        DateTime end = new DateTime().plusDays(1);
        when(activitiesApi.getScheduledActivitiesByDateRange(start, end)).thenReturn(activityCall);

        Single<ScheduledActivityListV4> single = activityManager.getActivities(start, end);
        single.test().awaitTerminalEvent().assertNotCompleted();

        verify(activitiesApi).getScheduledActivitiesByDateRange(start, end);
    }

    @Test
    public void updateActivities_success() throws IOException {

        List<ScheduledActivity> activityList = new ArrayList<>();
        Message message = mock(Message.class);
        Call<Message> activityCall = successCall(message);
        when(activitiesApi.updateScheduledActivities(activityList)).thenReturn(activityCall);

        Completable completable = activityManager.updateActivities(activityList);
        completable.test().awaitTerminalEvent().assertCompleted();

        verify(activitiesApi).updateScheduledActivities(activityList);
    }

    @Test
    public void updateActivities_failure() throws IOException {

        List<ScheduledActivity> activityList = new ArrayList<>();
        Call<Message> activityCall = errorCall(new BridgeSDKException("Failed", 500));
        when(activitiesApi.updateScheduledActivities(activityList)).thenReturn(activityCall);

        Completable completable = activityManager.updateActivities(activityList);
        completable.test().awaitTerminalEvent().assertNotCompleted();

        verify(activitiesApi).updateScheduledActivities(activityList);
    }

    @Test(expected = NullPointerException.class)
    public void updateActivities_null() throws IOException {
        activityManager.updateActivities(null);
    }

    // TODO: Move these helper methods since they are duplicated in AuthenticationManagerTest
    private <T> Call<T> successCall(T value) throws IOException {
        Response<T> response = Response.success(value);

        Call<T> messageCall = mock(Call.class);
        when(messageCall.clone()).thenReturn(messageCall);
        when(messageCall.isExecuted()).thenReturn(true);
        when(messageCall.isCanceled()).thenReturn(false);
        when(messageCall.execute()).thenReturn(response);
        return messageCall;
    }

    private <T> Call<T> errorCall(Throwable t) throws IOException {
        Call<T> messageCall = mock(Call.class);

        when(messageCall.clone()).thenReturn(messageCall);
        when(messageCall.isExecuted()).thenReturn(true);
        when(messageCall.isCanceled()).thenReturn(false);
        when(messageCall.execute()).thenThrow(t);

        return messageCall;
    }
}
