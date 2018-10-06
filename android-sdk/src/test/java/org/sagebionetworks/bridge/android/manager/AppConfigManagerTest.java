/*
 *    Copyright 2018 Sage Bionetworks
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package org.sagebionetworks.bridge.android.manager;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.android.BridgeApiTestUtils;
import org.sagebionetworks.bridge.android.BridgeConfig;
import org.sagebionetworks.bridge.android.manager.dao.AppConfigDAO;
import org.sagebionetworks.bridge.rest.api.PublicApi;
import org.sagebionetworks.bridge.rest.model.AppConfig;

import retrofit2.Call;

public class AppConfigManagerTest {
    private static final String APP_CONFIG_GUID = "app-config-guid";
    private static final String STUDY_ID = "test-study";

    private AppConfigDAO mockDAO;
    private AppConfigManager manager;
    private PublicApi mockApi;

    @Before
    public void setup() {
        mockDAO = mock(AppConfigDAO.class);

        BridgeConfig mockConfig = mock(BridgeConfig.class);
        when(mockConfig.getStudyId()).thenReturn(STUDY_ID);

        mockApi = mock(PublicApi.class);
        manager = new AppConfigManager(mockDAO, mockApi, mockConfig);
    }

    @Test
    public void fromCache() {
        // Set up cache.
        when(mockDAO.getAppConfig()).thenReturn(new AppConfig().guid(APP_CONFIG_GUID));

        // Execute.
        AppConfig result = manager.getAppConfig().toBlocking().value();
        assertEquals(APP_CONFIG_GUID, result.getGuid());

        // Verify we never call the server.
        verify(mockApi, never()).getAppConfig(any());
    }

    @Test
    public void fromRemote() throws Exception {
        // Mock API.
        Call<AppConfig> appConfigCall = BridgeApiTestUtils.mockCallWithValue(new AppConfig().guid(
                APP_CONFIG_GUID));
        when(mockApi.getAppConfig(STUDY_ID)).thenReturn(appConfigCall);

        // Execute.
        AppConfig result = manager.getAppConfig().toBlocking().value();
        assertEquals(APP_CONFIG_GUID, result.getGuid());

        // Verify cache.
        verify(mockDAO).cacheAppConfig(any());
    }
}
