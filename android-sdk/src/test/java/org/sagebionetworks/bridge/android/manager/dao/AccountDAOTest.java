/*
 *    Copyright 2017 Sage Bionetworks
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
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Lists;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.sagebionetworks.bridge.rest.model.UserSessionInfo;

@Config
@RunWith(RobolectricTestRunner.class)
public class AccountDAOTest {
    private AccountDAO accountDAO;

    @Before
    public void setup() {
        accountDAO = new AccountDAO(RuntimeEnvironment.application);
    }

    @Test
    public void dataGroups() {
        // Data Groups is initially empty.
        assertTrue(accountDAO.getDataGroups().isEmpty());

        // Add data groups and verify.
        accountDAO.addDataGroup("foo");
        accountDAO.addDataGroup("bar");
        assertEquals(Lists.newArrayList("foo", "bar"), accountDAO.getDataGroups());

        // Set data groups and verify.
        accountDAO.setDataGroups(Lists.newArrayList("asdf", "jkl"));
        assertEquals(Lists.newArrayList("asdf", "jkl"), accountDAO.getDataGroups());
    }

    @Test
    public void testSetUserSessionInfo_MergesReauthToken() {
        String reauthToken = "reauthToken";
        String sessionToken = "sessionToken";
        UserSessionInfo userSessionInfo = new UserSessionInfo()
                .sessionToken(sessionToken)
                .reauthToken(reauthToken);

        accountDAO.setUserSessionInfo(userSessionInfo);

        UserSessionInfo retrievedUserSessionInfo = accountDAO.getUserSessionInfo();

        assertEquals(userSessionInfo, retrievedUserSessionInfo);

        String newSessionToken = "newSessionToken";
        UserSessionInfo newUserSessionInfo = new UserSessionInfo()
                .sessionToken(newSessionToken);

        accountDAO.setUserSessionInfo(newUserSessionInfo);

        retrievedUserSessionInfo = accountDAO.getUserSessionInfo();

        assertEquals(newSessionToken, retrievedUserSessionInfo.getSessionToken()); // check this isn't the old session
        assertEquals(reauthToken,
                retrievedUserSessionInfo.getReauthToken()); // check new session has old reauth token

        String newReauthToken = "newReauthToken";
        String evenNewerSessionToken = "evenNewerSessionToken";
        UserSessionInfo evenNewerSession = new UserSessionInfo()
                .sessionToken(evenNewerSessionToken)
                .reauthToken(newReauthToken);

        accountDAO.setUserSessionInfo(evenNewerSession);

        retrievedUserSessionInfo = accountDAO.getUserSessionInfo();

        assertEquals(evenNewerSessionToken, retrievedUserSessionInfo.getSessionToken());
        assertEquals(newReauthToken, retrievedUserSessionInfo.getReauthToken()); // check we wrote a new reauth token
    }

    @Test
    public void testEmail() {
        assertNull(accountDAO.getEmail());

        String email = "email@example.com";
        accountDAO.setEmail(email);
        assertEquals(email, accountDAO.getEmail());

        accountDAO.setEmail(null);
        assertNull(accountDAO.getEmail());
    }

    @Test
    public void testPassword() {
        assertNull(accountDAO.getPassword());

        String password = "password";
        accountDAO.setPassword(password);
        assertEquals(password, accountDAO.getPassword());

        accountDAO.setPassword(null);
        assertNull(accountDAO.getPassword());
    }
}
