package org.sagebionetworks.bridge.android;

import android.content.Context;
import android.content.res.AssetManager;
import org.junit.Test;
import org.sagebionetworks.bridge.android.manager.upload.SchemaKey;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BridgeConfigTest {
    @Test
    public void taskToSchemaMap_ErrorOpeningFile() throws Exception {
        // mock context
        Context mockContext = mockContextWithException(IOException.class);

        // execute and validate
        BridgeConfig config = new BridgeConfig(mockContext);
        Map<String, SchemaKey> result = config.getTaskToSchemaMap();
        assertTrue(result.isEmpty());
    }

    @Test
    public void taskToSchemaMap_ErrorParsingJson() throws Exception {
        // mock context
        String jsonText = "This is bad JSON";
        Context mockContext = mockContextWithTaskToSchemaMap(jsonText);

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
        Context mockContext = mockContextWithTaskToSchemaMap(jsonText);

        // execute and validate
        BridgeConfig config = new BridgeConfig(mockContext);
        Map<String, SchemaKey> result = config.getTaskToSchemaMap();
        assertEquals(1, result.size());
        assertEquals("my-schema-id", result.get("my-task-id").getId());
        assertEquals(3, result.get("my-task-id").getRevision());
    }

    private static Context mockContextWithException(Class<? extends Throwable> throwableType)
            throws Exception {
        // mock asset manager
        AssetManager mockAssetManager = mock(AssetManager.class);
        when(mockAssetManager.open(BridgeConfig.TASK_TO_SCHEMA_FILENAME,
                AssetManager.ACCESS_BUFFER)).thenThrow(throwableType);

        // mock context
        return mockContextWithAssetManager(mockAssetManager);
    }

    private static Context mockContextWithTaskToSchemaMap(String mapText) throws Exception {
        // mock file
        InputStream inputStream = new ByteArrayInputStream(mapText.getBytes());

        // mock asset manager
        AssetManager mockAssetManager = mock(AssetManager.class);
        when(mockAssetManager.open(BridgeConfig.TASK_TO_SCHEMA_FILENAME,
                AssetManager.ACCESS_BUFFER)).thenReturn(inputStream);

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
