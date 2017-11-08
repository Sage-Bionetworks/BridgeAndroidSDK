package org.sagebionetworks.bridge.android.manager;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.android.BridgeApiTestUtils;
import org.sagebionetworks.bridge.rest.api.ForConsentedUsersApi;
import org.sagebionetworks.bridge.rest.model.Survey;
import retrofit2.Call;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SurveyManagerTest {
    private static final String SURVEY_GUID = "my-survey-guid";
    private static final DateTime SURVEY_CREATED_ON = DateTime.parse("2017-11-02T14:52:15.437Z");

    private ForConsentedUsersApi mockApi;
    private SurveyManager surveyManager;

    @Before
    public void setup() {
        mockApi = mock(ForConsentedUsersApi.class);
        AuthenticationManager mockAuthenticationManager = mock(AuthenticationManager.class);
        when(mockAuthenticationManager.getApiReference()).thenReturn(new AtomicReference<>(
                mockApi));

        surveyManager = new SurveyManager(mockAuthenticationManager);
    }

    @Test
    public void getSurvey_WithCreatedOn() throws Exception {
        // Mock api
        Survey apiOutputSurvey = new Survey();
        Call<Survey> apiOutputSurveyCall = BridgeApiTestUtils.mockCallWithValue(apiOutputSurvey);
        when(mockApi.getSurvey(SURVEY_GUID, SURVEY_CREATED_ON)).thenReturn(apiOutputSurveyCall);

        // execute and validate
        Survey managerOutputSurvey = surveyManager.getSurvey(SURVEY_GUID, SURVEY_CREATED_ON)
                .toBlocking().value();
        assertSame(apiOutputSurvey, managerOutputSurvey);

        // verify back-end call
        verify(mockApi).getSurvey(SURVEY_GUID, SURVEY_CREATED_ON);
    }

    @Test
    public void getSurvey_NullCreatedOn() throws Exception {
        // Mock api
        Survey apiOutputSurvey = new Survey();
        Call<Survey> apiOutputSurveyCall = BridgeApiTestUtils.mockCallWithValue(apiOutputSurvey);
        when(mockApi.getPublishedSurveyVersion(SURVEY_GUID)).thenReturn(apiOutputSurveyCall);

        // execute and validate
        Survey managerOutputSurvey = surveyManager.getSurvey(SURVEY_GUID, null)
                .toBlocking().value();
        assertSame(apiOutputSurvey, managerOutputSurvey);

        // verify back-end call
        verify(mockApi).getPublishedSurveyVersion(SURVEY_GUID);
    }
}
