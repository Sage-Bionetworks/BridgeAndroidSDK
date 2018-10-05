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

package org.sagebionetworks.bridge.android.manager.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.sagebionetworks.bridge.rest.model.AppConfig;

@Config
@RunWith(RobolectricTestRunner.class)
public class AppConfigDAOTest {
    private static final String DUMMY_GUID = "dummy-guid";

    @Test
    public void test() {
        AppConfigDAO appConfigDAO = new AppConfigDAO(RuntimeEnvironment.application);

        // Initially null.
        assertNull(appConfigDAO.getAppConfig());

        // Set/get works.
        AppConfig appConfig = new AppConfig().guid(DUMMY_GUID);
        appConfigDAO.cacheAppConfig(appConfig);

        AppConfig savedAppConfig = appConfigDAO.getAppConfig();
        assertEquals(DUMMY_GUID, savedAppConfig.getGuid());

        // Remove works.
        appConfigDAO.removeAppConfig();
        assertNull(appConfigDAO.getAppConfig());
    }
}
