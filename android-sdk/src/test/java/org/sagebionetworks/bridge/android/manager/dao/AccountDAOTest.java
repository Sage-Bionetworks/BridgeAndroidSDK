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

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.sagebionetworks.bridge.android.BuildConfig;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Config(constants = BuildConfig.class)
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
}
