package org.sagebionetworks.bridge.android.data;

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

    static class FileInfo {
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


    boolean isValid() {
        boolean isSurvey = !isNullOrEmpty(surveyGuid) && (surveyCreatedOn != null);
        boolean isSchema = !isNullOrEmpty(item);

        return (isSurvey || isSchema)
                && !isNullOrEmpty(appVersion)
                && !isNullOrEmpty(phoneInfo);
    }
}
