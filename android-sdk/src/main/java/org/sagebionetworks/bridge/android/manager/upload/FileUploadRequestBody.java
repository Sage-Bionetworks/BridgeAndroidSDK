/*
 *    Copyright 2018 Sage Bionetworks
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package org.sagebionetworks.bridge.android.manager.upload;

import java.io.File;
import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.internal.Util;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;

/**
 * Created by liujoshua on 2/22/2018.
 *
 * See https://gist.github.com/eduardb/dd2dc530afd37108e1ac#file-countingfilerequestbody-java
 */
public class FileUploadRequestBody extends RequestBody {
    
    private static final int SEGMENT_SIZE = 8192; // okio.Segment.SIZE
    
    private final File file;
    private final ProgressListener listener;
    private final String contentType;
    
    public FileUploadRequestBody(File file, String contentType, ProgressListener listener) {
        this.file = file;
        this.contentType = contentType;
        this.listener = listener;
    }
    
    @Override
    public long contentLength() {
        return file.length();
    }
    
    @Override
    public MediaType contentType() {
        return MediaType.parse(contentType);
    }
    
    @Override
    public void writeTo(BufferedSink sink) throws IOException {
        Source source = null;
        try {
            source = Okio.source(file);
            long total = 0;
            long read;
            
            while ((read = source.read(sink.buffer(), SEGMENT_SIZE)) != -1) {
                total += read;
                sink.flush();
                this.listener.transferred(total);
            }
        } finally {
            Util.closeQuietly(source);
        }
    }
    
    public interface ProgressListener {
        void transferred(long num);
    }
}