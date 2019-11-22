package org.sagebionetworks.bridge.android.receiver;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sagebionetworks.bridge.android.BridgeConfig;

public class AppVersionManagerTest {
    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private AppVersionManager appVersionManager;

    @Mock
    BridgeConfig bridgeConfig;

    @Before
    public void setupTest() {
        MockitoAnnotations.initMocks(this);

        appVersionManager = new AppVersionManager(InstrumentationRegistry.getTargetContext(), bridgeConfig);
        appVersionManager.getAppVersionSharedPreferences().edit().clear().commit();
    }

    @Test
    public void isUpgradeRequired() {
        when(bridgeConfig.getAppVersion()).thenReturn(1);

        assertFalse(appVersionManager.isUpgradeRequired());
    }

    @Test
    public void setUpgradeRequired() {
        when(bridgeConfig.getAppVersion()).thenReturn(10);
        assertFalse(appVersionManager.isUpgradeRequired());

        // set current version as upgrade required
        appVersionManager.setUpgradeRequired();
        assertTrue(appVersionManager.isUpgradeRequired());

        // simulate upgrade
        when(bridgeConfig.getAppVersion()).thenReturn(11);
        assertFalse(appVersionManager.isUpgradeRequired());
    }
}