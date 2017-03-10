package org.sagebionetworks.bridge.android.data;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.gson.annotations.SerializedName;

import org.joda.time.DateTime;

import java.util.List;

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * This is the model for info.json
 */
@SuppressWarnings("unused")
class ArchiveInfo {
    private static final int DEFAULT_SCHEMA_REVISION = 1;

    public static class FileInfo {
        @SerializedName("filename")
        String filename;
        @SerializedName("timestamp")
        DateTime timestamp;

        FileInfo(String filename, DateTime timestamp) {
            this.filename = filename;
            this.timestamp = timestamp;
        }
    }

    @SerializedName("appVersion")
    String appVersion;
    @SerializedName("phoneInfo")
    String phoneInfo;

    @SerializedName("files")
    List<FileInfo> files;

    // used for surveys
    @SerializedName("SurveyGuid")
    String surveyGuid;
    @SerializedName("surveyCreatedOn")
    DateTime surveyCreatedOn;

    // used for non-survey activities
    @SerializedName("item")
    String item;
    @SerializedName("schemaRevision")
    int schemaRevision = DEFAULT_SCHEMA_REVISION;

    boolean isSurvey() {
        return !isNullOrEmpty(surveyGuid) && (surveyCreatedOn != null);
    }

    boolean isSchema() {
        return !isNullOrEmpty(item);
    }

    boolean isValid() {
        return (isSurvey() ^ isSchema())
                && !isNullOrEmpty(appVersion)
                && !isNullOrEmpty(phoneInfo);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArchiveInfo that = (ArchiveInfo) o;
        return schemaRevision == that.schemaRevision &&
                Objects.equal(appVersion, that.appVersion) &&
                Objects.equal(phoneInfo, that.phoneInfo) &&
                Objects.equal(files, that.files) &&
                Objects.equal(surveyGuid, that.surveyGuid) &&
                Objects.equal(surveyCreatedOn, that.surveyCreatedOn) &&
                Objects.equal(item, that.item);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(appVersion, phoneInfo, files, surveyGuid, surveyCreatedOn, item, schemaRevision);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("appVersion", appVersion)
                .add("phoneInfo", phoneInfo)
                .add("files", files)
                .add("surveyGuid", surveyGuid)
                .add("surveyCreatedOn", surveyCreatedOn)
                .add("item", item)
                .add("schemaRevision", schemaRevision)
                .toString();
    }
}
