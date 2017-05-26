package org.sagebionetworks.bridge.android.manager;

import com.squareup.tape.FileObjectQueue;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by jameskizer on 5/26/17.
 */

public class UploadFileConverter implements FileObjectQueue.Converter<UploadFile> {

    @Override
    public UploadFile from(byte[] bytes) throws IOException {
        return null;
    }

    @Override
    public void toStream(UploadFile o, OutputStream bytes) throws IOException {

    }
}
