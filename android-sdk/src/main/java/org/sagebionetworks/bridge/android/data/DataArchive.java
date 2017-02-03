package org.sagebionetworks.bridge.android.data;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.ByteSource;
import com.google.gson.annotations.SerializedName;

import org.joda.time.DateTime;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Created by jyliu on 1/30/2017.
 */

public class DataArchive {
    private final ImmutableList<DataFile> dataFiles;
    private final Info info;

    private DataArchive(List<DataFile> dataFiles, Info info) {
        this.dataFiles = ImmutableList.copyOf(dataFiles);
        this.info = info;
    }

    static class Info {
        static class FileInfo {
            @SerializedName("filename")
            private final String filename;
            @SerializedName("timestamp")
            private final DateTime timestamp;

            FileInfo(String filename, DateTime timestamp) {
                this.filename = filename;
                this.timestamp = timestamp;
            }
        }

        @SerializedName("files")
        List<FileInfo> files;
        String item;
        String surveyGuid;
        String surveyCreatedOn;
        int schemaRevision = 1;
        // since this buildconfig is in skin, this won't be correct
        String appVersion;
        String phoneInfo;
    }

    public interface DataFile {
        String getFileName();
        DateTime endDate();
        ByteSource getByteSource();
    }

    public static class Builder {

        List<DataFile> files;
        Info info;

        public Builder() {
            files = Lists.newArrayList();
            info = new Info();
        }

        public void addDataFile(DataFile entry) {
            files.add(entry);
            info.files.add(new Info.FileInfo(entry.getFileName(), entry.endDate()));
        }

        public DataArchive build() {
            return new DataArchive(files, info);
        }
    }

    public ZipOutputStream writeTo(OutputStream os) throws IOException {
        ZipOutputStream zos = new ZipOutputStream(os);

        for (DataFile dataFile : dataFiles) {
            ZipEntry entry = new ZipEntry(dataFile.getFileName());

            zos.putNextEntry(entry);
            zos.write(dataFile.getByteSource().read());
        }
        zos.closeEntry();

        return zos;
    }
}
