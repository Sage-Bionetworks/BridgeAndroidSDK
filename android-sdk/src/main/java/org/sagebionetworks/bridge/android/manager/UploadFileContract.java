package org.sagebionetworks.bridge.android.manager;

import android.provider.BaseColumns;

/**
 * Created by jameskizer on 5/26/17.
 */

public class UploadFileContract {

    private UploadFileContract() {}

    public static class UploadFileSchema implements BaseColumns {

        public static final String TABLE_NAME = "upload_file";
        public static final String COLUMN_NAME_FILENAME = "filename";
        public static final String COLUMN_NAME_CONTENT_TYPE = "content_type";
        public static final String COLUMN_NAME_FILE_LENGTH = "file_length";
        public static final String COLUMN_NAME_MD5_HASH = "md5_hash";

    }
}
