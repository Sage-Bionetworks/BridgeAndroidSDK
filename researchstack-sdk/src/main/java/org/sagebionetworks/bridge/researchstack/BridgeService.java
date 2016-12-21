package org.sagebionetworks.bridge.researchstack;

import org.researchstack.skin.DataResponse;
import org.sagebionetworks.bridge.researchstack.upload.UploadRequest;
import org.sagebionetworks.bridge.sdk.restmm.UserSessionInfo;
import org.sagebionetworks.bridge.sdk.restmm.model.BridgeMessageResponse;
import org.sagebionetworks.bridge.sdk.restmm.model.ConsentSignatureBody;
import org.sagebionetworks.bridge.sdk.restmm.model.EmailBody;
import org.sagebionetworks.bridge.sdk.restmm.model.SharingOptionBody;
import org.sagebionetworks.bridge.sdk.restmm.model.SignInBody;
import org.sagebionetworks.bridge.sdk.restmm.model.SignUpBody;
import org.sagebionetworks.bridge.sdk.restmm.model.UploadSession;
import org.sagebionetworks.bridge.sdk.restmm.model.UploadValidationStatus;
import org.sagebionetworks.bridge.sdk.restmm.model.WithdrawalBody;

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
  @POST("v3/subpopulations/{subpopulationGuid}/consents/signature")
  Observable<Response<BridgeMessageResponse>> consentSignature(
      @Path("subpopulationGuid") String subpopulationGuid, @Body ConsentSignatureBody body);

  /**
   * @return Response code <b>200</b> w/ message explaining instructions on how the user should
   * proceed
   */
  @Headers("Content-Type: application/json")
  @POST("v3/auth/requestResetPassword")
  Observable<Response<BridgeMessageResponse>> requestResetPassword(@Body EmailBody body);

  @POST("v3/subpopulations/{subpopulationGuid}/consents/signature/withdraw")
  Observable<Response<BridgeMessageResponse>> withdrawConsent(
      @Path("subpopulationGuid") String subpopulationGuid, @Body WithdrawalBody withdrawal);

  /**
   * @return Response code <b>200</b> w/ message explaining instructions on how the user should
   * proceed
   */
  @Headers("Content-Type: application/json")
  @POST("v3/auth/resendEmailVerification")
  Observable<DataResponse> resendEmailVerification(@Body EmailBody body);

  /**
   * @return Response code 200 w/ message telling user has been signed out
   */
  @POST("v3/auth/signOut")
  Observable<Response> signOut();

  @POST("v3/users/self/dataSharing")
  Observable<Response<BridgeMessageResponse>> dataSharing(
      @Body SharingOptionBody body);

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
