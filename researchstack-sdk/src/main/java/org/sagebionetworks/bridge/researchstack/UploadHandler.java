package org.sagebionetworks.bridge.researchstack;

import android.content.Context;
import android.support.annotation.NonNull;
import android.widget.Toast;

import org.researchstack.backbone.ResourceManager;
import org.sagebionetworks.bridge.researchstack.upload.BridgeDataArchive;
import org.sagebionetworks.bridge.researchstack.upload.BridgeDataInput;
import org.sagebionetworks.bridge.researchstack.upload.UploadQueue;
import org.sagebionetworks.bridge.researchstack.upload.UploadRequest;
import org.sagebionetworks.bridge.researchstack.wrapper.StorageAccessWrapper;
import org.sagebionetworks.bridge.rest.api.ForConsentedUsersApi;

import org.sagebionetworks.bridge.rest.model.UploadSession;
import org.sagebionetworks.bridge.rest.model.UploadValidationStatus;
import org.sagebionetworks.bridge.researchstack.upload.Info;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import rx.Observable;
import rx.schedulers.Schedulers;

public class UploadHandler {
  private static final Logger logger = LoggerFactory.getLogger(UploadHandler.class);

  private final StorageAccessWrapper storageAccess;
  private final Context applicationContext;
  private final ResourceManager.Resource publicKey;

  public UploadHandler(Context applicationContext,
      StorageAccessWrapper storageAccess, ResourceManager.Resource publicKey) {
    this.storageAccess = storageAccess;
    this.applicationContext = applicationContext.getApplicationContext();
    this.publicKey = publicKey;
  }

  public void uploadBridgeData(ForConsentedUsersApi forConsentedUsersApi, Info info,
      BridgeDataInput... dataFiles) {
    uploadBridgeData(forConsentedUsersApi, info, Arrays.asList(dataFiles));
  }

  // figure out what directory to save files in and where to put this method
  public static File getFilesDir(Context context) {
    return new File(context.getFilesDir() + "/upload_request/");
  }

  public void uploadBridgeData(ForConsentedUsersApi bridforConsentedUsersApieService, Info info,
      List<BridgeDataInput> dataFiles) {
    try {
      BridgeDataArchive archive = new BridgeDataArchive(info);
      archive.start(getFilesDir(applicationContext));

      for (BridgeDataInput dataFile : dataFiles) {
        archive.addFile(applicationContext, dataFile);
      }

      UploadRequest request =
          archive.finishAndEncrypt(applicationContext, publicKey,
              getFilesDir(applicationContext));

      ((UploadQueue) storageAccess.getAppDatabase()).saveUploadRequest(
          request);
      uploadPendingFiles(bridforConsentedUsersApieService);
    } catch (IOException e) {
      throw new RuntimeException("Error encrypting initial task data", e);
    }
  }

  public void uploadPendingFiles(ForConsentedUsersApi forConsentedUsersApi) {
    List<UploadRequest> uploadRequests =
        ((UploadQueue) storageAccess.getAppDatabase()).loadUploadRequests();

    // There is an issue here, being that this will loop through the upload requests and upload
    // a zip async. The service cannot handle more than two async calls so any other requested
    // async calls fail due to SockTimeoutException
    for (UploadRequest uploadRequest : uploadRequests) {
      if (uploadRequest.bridgeId == null) {
        logger.debug(
            "Starting upload for request: " + uploadRequest.name);
        uploadFile(forConsentedUsersApi, uploadRequest);
      } else {
        logger.debug(
            "Bridge ID found, confirming upload for: " + uploadRequest.name);
        confirmUpload(forConsentedUsersApi, uploadRequest);
      }
    }
  }

  protected void uploadFile(ForConsentedUsersApi forConsentedUsersApi, UploadRequest request) {
    org.sagebionetworks.bridge.rest.model.UploadRequest uploadRequest = new org.sagebionetworks.bridge.rest.model.UploadRequest();
    uploadRequest.name(request.name)
            .contentLength(request.contentLength)
            .contentMd5(request.contentMd5)
            .contentType(request.contentType);

    ApiUtils.toResponseObservable(forConsentedUsersApi.requestUploadSession(uploadRequest))
    .flatMap(response -> {
      if (response.isSuccessful()) {
        return uploadToS3(request, response.body());
      } else {
        ApiUtils.handleError(applicationContext, response.code());
        throw new RuntimeException(response.message());
      }
    }).flatMap(id -> {
      logger.debug("Notifying bridge of s3 upload: " + id);

      // Updating request entry with Bridge ID for saving on success
      request.bridgeId = id;

      return ApiUtils.toResponseObservable(forConsentedUsersApi.completeUploadSession(id));
    }).subscribeOn(Schedulers.io()).subscribe(completeResponse -> {
      if (completeResponse.isSuccessful()) {
        logger.debug("Notified bridge of s3 upload, need to confirm");
        // update UploadRequest in DB with id for later confirmation
        ((UploadQueue) storageAccess.getAppDatabase()).saveUploadRequest(
            request);
      } else {
        ApiUtils.handleError(applicationContext, completeResponse.code());
      }
    }, error -> {
      error.printStackTrace();
      logger.error("Error uploading file to S3, will try again", error);
    });
  }

  @NonNull
  private Observable<String> uploadToS3(UploadRequest request,
      UploadSession uploadSession) {
    // retrofit doesn't like making requests outside of your api, use okhttp to make the call
    return Observable.create(subscriber -> {
      // Request will fail without Content-MD5, Content-Type, and Content-Length
      logger.debug("Uploading to S3");
      RequestBody body = RequestBody.create(MediaType.parse(request.contentType),
          new File(getFilesDir(applicationContext), request.name));
      Request awsRequest = new Request.Builder().url(uploadSession.getUrl())
          .put(body)
          .header("Content-MD5", request.contentMd5)
          .build();

      Response response = null;
      try {
        response = new OkHttpClient().newCall(awsRequest).execute();

        if (response.isSuccessful()) {
          logger.debug("Successful s3 upload");
          subscriber.onNext(uploadSession.getId());
        } else {
          ApiUtils.handleError(applicationContext, response.code());
          throw new RuntimeException("Response unsuccessful, code: " + response.code());
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
  }

  private void confirmUpload(ForConsentedUsersApi forConsentedUsersApi, UploadRequest request) {
    ApiUtils.toResponseObservable(forConsentedUsersApi.getUploadStatus(request.bridgeId))
        .subscribeOn(Schedulers.io())
        .subscribe(response -> {
          if (response.isSuccessful()) {
            UploadValidationStatus uploadStatus = response.body();

            logger.debug("Received validation status from Bridge(" +
                uploadStatus.getStatus() + ")");

            switch (uploadStatus.getStatus()) {
              case UNKNOWN:
              case VALIDATION_FAILED:
                String errorText =
                    "ERROR: Bridge validation of file upload failed for: " + request.name;
                Toast.makeText(applicationContext, errorText, Toast.LENGTH_SHORT).show();
                logger.error(errorText);
                // figure out what to actually do on unrecoverable, from a user perspective
                deleteUploadRequest(request);
                break;

              case REQUESTED:
                logger.error(
                    "Status is still 'requested' for some reason, will retry upload later");
                // removing bridge id so upload is retried later
                request.bridgeId = null;
                ((UploadQueue) storageAccess
                    .getAppDatabase()).saveUploadRequest(request);
                break;

              case SUCCEEDED:
                logger.debug(
                    "Status is 'success', removing request locally");
                deleteUploadRequest(request);
                break;

              case VALIDATION_IN_PROGRESS:
              default:
                logger.debug(
                    "Status is pending, will retry confirmation later");
                // No action necessary
                break;
            }
          } else {
            ApiUtils.handleError(applicationContext, response.code());
          }
        }, error -> {
          error.printStackTrace();
          logger.error(
              "Error connecting to Bridge server, will try again later");
        });
  }

  private void deleteUploadRequest(UploadRequest request) {
    ((UploadQueue) storageAccess.getAppDatabase()).deleteUploadRequest(
        request);

    File file = new File(getFilesDir(applicationContext), request.name);
    if (file.exists() && file.delete()) {
      logger.debug("Deleted file: " + file.getName());
    } else {
      logger.debug("Could not delete file: " + request.name);
    }
  }
}