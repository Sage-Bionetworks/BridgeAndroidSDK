package org.sagebionetworks.bridge.android.manager;

import java.io.Serializable;

/**
 * Created by jameskizer on 5/26/17.
 */

class UploadFile implements Serializable {
    public String filename;
    public String contentType;
    public long fileLength;
    public String md5Hash;
}
