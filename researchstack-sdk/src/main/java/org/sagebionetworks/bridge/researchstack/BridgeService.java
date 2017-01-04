package org.sagebionetworks.bridge.researchstack;

import org.sagebionetworks.bridge.researchstack.upload.UploadRequest;
import org.sagebionetworks.bridge.sdk.restmm.model.BridgeMessageResponse;
import org.sagebionetworks.bridge.sdk.restmm.model.UploadSession;
import org.sagebionetworks.bridge.sdk.restmm.model.UploadValidationStatus;

import retrofit2.Response;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Path;
import rx.Observable;

/**
 * Created by liujoshua on 9/2/16.
 */
public interface BridgeService {
  @Headers("Content-Type: application/json")
  @POST("v3/uploads")
  Observable<Response<UploadSession>> requestUploadSession(@Body UploadRequest body);

  @POST("v3/uploads/{id}/complete")
  Observable<Response<BridgeMessageResponse>> uploadComplete(
      @Path("id") String id);

  @GET("v3/uploadstatuses/{id}")
  Observable<Response<UploadValidationStatus>> uploadStatus(
      @Path("id") String id);
}
