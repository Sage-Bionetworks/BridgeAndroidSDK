package org.sagebionetworks.bridge.android.data;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.android.BridgeConfig;
import org.sagebionetworks.bridge.rest.RestUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Created by jyliu on 1/30/2017.
 */

public class Archive {
    private static final String ARCHIVE_INFO_FILE_NAME = "info.json";

    public final List<ArchiveFile> dataFiles;
    public final ArchiveInfo archiveInfo;

    private Archive(List<ArchiveFile> dataFiles, ArchiveInfo archiveInfo) {
        this.dataFiles = Collections.unmodifiableList(dataFiles);
        this.archiveInfo = archiveInfo;
    }

    public ZipOutputStream writeTo(OutputStream os) throws IOException {
        ZipOutputStream zos = new ZipOutputStream(os);
        try {
            for (ArchiveFile dataFile : dataFiles) {
                ZipEntry entry = new ZipEntry(dataFile.getFilename());

                zos.putNextEntry(entry);
                zos.write(dataFile.getByteSource().read());
                zos.closeEntry();
            }

            ZipEntry infoFileEntry = new ZipEntry(ARCHIVE_INFO_FILE_NAME);
            zos.putNextEntry(infoFileEntry);
            zos.write(RestUtils.GSON.toJson(archiveInfo).getBytes());
            zos.closeEntry();
        } finally {
            zos.close();
        }
        return zos;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Archive archive = (Archive) o;
        return Objects.equal(dataFiles, archive.dataFiles)
                && Objects.equal(archiveInfo, archive.archiveInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(dataFiles, archiveInfo);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("dataFiles", dataFiles)
                .add("archiveInfo", archiveInfo)
                .toString();
    }

    public static class Builder implements WithBridgeConfig {
        private List<ArchiveFile> files = Lists.newArrayList();
        private BridgeConfig bridgeConfig;
        private ArchiveInfo archiveInfo = new ArchiveInfo();

        public Builder withBridgeConfig(BridgeConfig bridgeConfig) {
            checkNotNull(bridgeConfig);

            archiveInfo.appVersion = bridgeConfig.getAppVersionName();
            archiveInfo.phoneInfo = bridgeConfig.getDeviceName();
            this.bridgeConfig = bridgeConfig;
            return this;
        }

        public Builder addDataFile(ArchiveFile entry) {
            checkNotNull(entry);

            files.add(entry);
            return this;
        }

        public Archive build() {
            checkState(archiveInfo.isValid(), "archive info is invalid");

            archiveInfo.files = Lists.newArrayList();
            for (ArchiveFile file : files) {
                archiveInfo.files.add(new ArchiveInfo.FileInfo(file.getFilename(), file.getEndDate()));
            }

            return new Archive(files, archiveInfo);
        }

        private Builder() {
        }

        public static WithBridgeConfig forActivity(String item) {
            checkNotNull(item);

            Builder builder = new Builder();
            builder.archiveInfo.item = item;
            return builder;
        }

        public static WithBridgeConfig forActivity(String item, int schemaRevision) {
            checkNotNull(item);

            Builder builder = new Builder();
            builder.archiveInfo.item = item;
            builder.archiveInfo.schemaRevision = schemaRevision;
            return builder;
        }

        public static WithBridgeConfig forSurvey(String surveyGuid, DateTime surveyCreatedOn) {
            checkNotNull(surveyGuid);
            checkNotNull(surveyCreatedOn);

            Builder builder = new Builder();
            builder.archiveInfo.surveyGuid = surveyGuid;
            builder.archiveInfo.surveyCreatedOn = surveyCreatedOn;
            return builder;
        }

    }

    public interface WithBridgeConfig {
        Builder withBridgeConfig(BridgeConfig bridgeConfig);
    }

}
