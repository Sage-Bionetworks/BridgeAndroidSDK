package org.sagebionetworks.bridge.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.AssetManager;

import org.junit.Test;
import org.sagebionetworks.bridge.android.manager.upload.SchemaKey;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public class BridgeConfigTest {
    private static final String EXTERNAL_ID = "my-external-id";

    @Test
    public void taskToSchemaMap_ErrorOpeningFile() throws Exception {
        // mock context
        Context mockContext = mockContextWithException(BridgeConfig.TASK_TO_SCHEMA_FILENAME,
                IOException.class);

        // execute and validate
        BridgeConfig config = new BridgeConfig(mockContext);
        Map<String, SchemaKey> result = config.getTaskToSchemaMap();
        assertTrue(result.isEmpty());
    }

    @Test
    public void taskToSchemaMap_ErrorParsingJson() throws Exception {
        // mock context
        String jsonText = "This is bad JSON";
        Context mockContext = mockContextWithJsonFile(BridgeConfig.TASK_TO_SCHEMA_FILENAME,
                jsonText);

        // execute and validate
        BridgeConfig config = new BridgeConfig(mockContext);
        Map<String, SchemaKey> result = config.getTaskToSchemaMap();
        assertTrue(result.isEmpty());
    }

    @Test
    public void taskToSchemaMap_Success() throws Exception {
        // mock context
        String jsonText = "{\n" +
                "   \"my-task-id\":{\n" +
                "       \"id\":\"my-schema-id\",\n" +
                "       \"revision\":3\n" +
                "   }\n" +
                "}";
        Context mockContext = mockContextWithJsonFile(BridgeConfig.TASK_TO_SCHEMA_FILENAME,
                jsonText);

        // execute and validate
        BridgeConfig config = new BridgeConfig(mockContext);
        Map<String, SchemaKey> result = config.getTaskToSchemaMap();
        assertEquals(1, result.size());
        assertEquals("my-schema-id", result.get("my-task-id").getId());
        assertEquals(3, result.get("my-task-id").getRevision());
    }

    @Test
    public void externalIdSettings_NoFile() throws Exception {
        // Mock context
        Context mockContext = mockContextWithException(BridgeConfig.EXTERNAL_ID_SETTINGS_FILENAME,
                FileNotFoundException.class);

        // Execute - getEmail/PasswordForExternalId() should both throw.
        BridgeConfig config = new BridgeConfig(mockContext);

        try {
            config.getEmailForExternalId(EXTERNAL_ID);
            fail("expected exception");
        } catch (UnsupportedOperationException ex) {
            assertEquals("Credentials for external ID require asset file " +
                    BridgeConfig.EXTERNAL_ID_SETTINGS_FILENAME, ex.getMessage());
        }

        try {
            config.getPasswordForExternalId(EXTERNAL_ID);
            fail("expected exception");
        } catch (UnsupportedOperationException ex) {
            assertEquals("Credentials for external ID require asset file " +
                    BridgeConfig.EXTERNAL_ID_SETTINGS_FILENAME, ex.getMessage());
        }
    }

    @Test
    public void externalIdSettings_WithSettings() throws Exception {
        // Mock context
        String settingsJsonText = "{\n" +
                "   \"emailFormat\":\"example+%s@example.com\",\n" +
                "   \"passwordFormat\":\"%s's dummy password\"\n" +
                "}";
        Context mockContext = mockContextWithJsonFile(BridgeConfig.EXTERNAL_ID_SETTINGS_FILENAME,
                settingsJsonText);

        // Execute
        BridgeConfig config = new BridgeConfig(mockContext);
        assertEquals("example+" + EXTERNAL_ID + "@example.com", config
                .getEmailForExternalId(EXTERNAL_ID));
        assertEquals(EXTERNAL_ID + "'s dummy password", config.getPasswordForExternalId(
                EXTERNAL_ID));
    }

    private static Context mockContextWithException(
            String filename, Class<? extends Throwable> throwableType) throws Exception {
        // mock asset manager
        AssetManager mockAssetManager = mock(AssetManager.class);
        when(mockAssetManager.open(filename, AssetManager.ACCESS_BUFFER)).thenThrow(throwableType);

        // Every other file should just throw a FileNotFoundException. Otherwise, the test throws
        // with a NullPointerException.
        when(mockAssetManager.open(not(eq(filename)), anyInt())).thenThrow(
                FileNotFoundException.class);

        // mock context
        return mockContextWithAssetManager(mockAssetManager);
    }

    private static Context mockContextWithJsonFile(String filename, String mapText)
            throws Exception {
        // mock file
        InputStream inputStream = new ByteArrayInputStream(mapText.getBytes());

        // mock asset manager
        AssetManager mockAssetManager = mock(AssetManager.class);
        when(mockAssetManager.open(filename, AssetManager.ACCESS_BUFFER)).thenReturn(inputStream);

        // Every other file should just throw a FileNotFoundException. Otherwise, the test throws
        // with a NullPointerException.
        when(mockAssetManager.open(not(eq(filename)), anyInt())).thenThrow(
                FileNotFoundException.class);

        // mock context
        return mockContextWithAssetManager(mockAssetManager);
    }

    private static Context mockContextWithAssetManager(AssetManager assetManager) {
        // mock app context
        Context mockAppContext = mock(Context.class);
        when(mockAppContext.getAssets()).thenReturn(assetManager);

        // mock context
        Context mockContext = mock(Context.class);
        when(mockContext.getApplicationContext()).thenReturn(mockAppContext);

        return mockContext;
    }
}
