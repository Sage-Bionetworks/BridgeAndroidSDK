package org.sagebionetworks.bridge.android.manager.upload;

import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.PUT;
import retrofit2.http.Url;

public interface S3Service {
    @PUT
    Call<Void> uploadToS3(@Url String url, @Body RequestBody body, @Header("Content-MD5") String
            md5Hash, @Header("Content-Type") String contentType);
}