package org.sagebionetworks.bridge.android.data;

import com.google.common.io.ByteSource;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.rest.RestUtils;

import java.lang.reflect.Type;

/**
 * Created by jyliu on 2/2/2017.
 */
public class JsonArchiveFile implements ArchiveFile {
    private final String filename;
    private final DateTime endDate;
    private final String json;

    public JsonArchiveFile(String filename, DateTime endDate, String json) {
        this.filename = filename;
        this.endDate = endDate;
        this.json = json;
    }

    public JsonArchiveFile(String filename, DateTime endDate, Object object) {
        this(filename, endDate, RestUtils.GSON.toJson(object));
    }

    public JsonArchiveFile(String filename, DateTime endDate, Object object, Type objectTYpe) {
        this(filename, endDate, RestUtils.GSON.toJson(object, objectTYpe));
    }

    @Override
    public String getFilename() {
        return filename;
    }

    @Override
    public DateTime getEndDate() {
        return endDate;
    }

    @Override
    public ByteSource getByteSource() {
        return ByteSource.wrap(json.getBytes());
    }
}
