package org.sagebionetworks.bridge.researchstack.upload;

import android.content.Context;

import com.google.gson.Gson;

import org.researchstack.backbone.StorageAccess;
import org.researchstack.backbone.utils.ResUtils;
import org.sagebionetworks.bridge.rest.RestUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Type;

public class BridgeDataInput {

    private static Gson gson = RestUtils.GSON;

    File   file;
    String filename;
    String timestamp;
    private Class clazz;
    private Type type;
    private Object gsonableObject;

    public BridgeDataInput(Object gsonableObject, Class clazz, String filename, String timestamp) {
        this.gsonableObject = gsonableObject;
        this.clazz = clazz;
        this.filename = filename;
        this.timestamp = timestamp;
    }

    public BridgeDataInput(Object gsonableObject, Type type, String filename, String timestamp) {
        this.gsonableObject = gsonableObject;
        this.type = type;
        this.filename = filename;
        this.timestamp = timestamp;
    }

    public BridgeDataInput(File file, String timestamp) {
        this.file = file;
        this.filename = file.getName();
        this.timestamp = timestamp;
    }

    public InputStream getInputStream(Context context) throws FileNotFoundException {
        if (gsonableObject != null) {
            if (clazz != null) {
                return new ByteArrayInputStream(gson.toJson(gsonableObject, clazz).getBytes());
            } else {
                return new ByteArrayInputStream(gson.toJson(gsonableObject, type).getBytes());
            }
        } else {  // file != null
            return new FileInputStream(file);
        }
    }

    public File getFile() {
        return file;
    }
}
