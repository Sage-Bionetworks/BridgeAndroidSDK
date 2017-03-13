package org.sagebionetworks.bridge.android.manager;

import android.support.annotation.WorkerThread;

import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteSink;
import com.google.common.io.FileWriteMode;
import com.google.common.io.Files;

import org.sagebionetworks.bridge.android.data.Archive;
import org.sagebionetworks.bridge.android.data.StudyUploadEncryptor;
import org.sagebionetworks.bridge.android.manager.dao.UploadDAO;
import org.sagebionetworks.bridge.android.manager.upload.S3Service;
import org.sagebionetworks.bridge.android.util.retrofit.RxUtils;
import org.sagebionetworks.bridge.rest.api.ForConsentedUsersApi;
import org.sagebionetworks.bridge.rest.model.UploadRequest;
import org.sagebionetworks.bridge.rest.model.UploadSession;
import org.sagebionetworks.bridge.rest.model.UploadStatus;
import org.sagebionetworks.bridge.rest.model.UploadValidationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.cms.CMSException;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import retrofit2.Retrofit;
import rx.Observable;
import rx.Single;

/**
 * Created by jyliu on 1/30/2017.
 */

public class UploadManager {
    private static final Logger LOG = LoggerFactory.getLogger(UploadManager.class);
    private static final String CONTENT_TYPE_DATA_ARCHIVE = "application/zip";

    private final ForConsentedUsersApi api;
    private final StudyUploadEncryptor encryptor;
    private final UploadDAO uploadDAO;
    private final OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false).build();

    public UploadManager(AuthenticationManager authenticationManager, StudyUploadEncryptor
            encryptor, UploadDAO uploadDAO) {
        this.api = authenticationManager.getApi();
        this.encryptor = encryptor;
        this.uploadDAO = uploadDAO;
    }

    public static class UploadFile {
        public String filename;
        public String contentType;
        public long fileLength;
        public String md5Hash;
    }

    /**
     * Persists the archive on disk and add it to the queue of pending uploads.
     *
     * @param filename filename for the archive
     * @param archive  archive to be queued
     * @return information about file that produced by the archive
     */
    public Single<UploadFile> queueUpload(String filename, Archive archive) {
        return Single.fromCallable(() -> persist(filename, archive));
    }

    public Observable<UploadValidationStatus> uploadToS3() {
        Observable<String> filenameObservable = Observable.from((String[]) uploadDAO
                .listUploadFilenames().toArray());

        return filenameObservable
                .flatMap(filename -> uploadToS3(filename).toObservable());

//                .map(filename -> uploadDAO.getUploadFile(filename))
//                .flatMap(filename);
//

//                .flatMap(uploadFile -> getUploadSession(uploadFile).toObservable())
//                .flatMap(uploadSession -> getUploadValidationStatus(uploadSession.getId()).toObservable())
//                .withLatestFrom(filenameObservable,
//                        (uploadValidationStatus, filename) ->
//                                (new Object[]{uploadValidationStatus, filename}))
//                .filter(data ->
//                        UploadStatus.SUCCEEDED != ((UploadValidationStatus) data[0]).getStatus())
//                .map(data -> ((UploadValidationStatus) data[0]));
    }

    /**
     * Uploads a previously queued upload.
     *
     * @param filename filename to upload
     * @return Single of current upload validation status, which could be null if pending upload not found
     */
    public Single<UploadValidationStatus> uploadToS3(String filename) {
        UploadFile uploadFile = uploadDAO.getUploadFile(filename);

        String uploadId = uploadDAO.getUploadSession(filename).getId();

        return getUploadValidationStatus(uploadId)
                .flatMap((uploadValidationStatus) -> {
                    if (uploadValidationStatus == null) {
                        LOG.info("Could not find pending upload session for filename: " + filename);
                        return null;
                    }
                    if (UploadStatus.SUCCEEDED == uploadValidationStatus.getStatus()) {
                        LOG.info("Cleaning up successful upload with filename: " + filename
                                + ", id: " + uploadValidationStatus.getId());

                        uploadDAO.removeUploadAndSession(filename);
                        return Single.just(uploadValidationStatus);
                    }
                    return getUploadSession(uploadFile)
                            .flatMap(uploadSession -> uploadToS3(uploadFile, uploadSession))
                            .flatMap(uploadSession -> getUploadValidationStatus(uploadId));
                });
    }

    Single<UploadValidationStatus> getUploadValidationStatus(String uploadId) {
        return RxUtils.toBodySingle(api.getUploadStatus(uploadId));
    }

    Single<UploadSession> uploadToS3(UploadFile uploadFile, UploadSession session) {

        File file = new File(uploadFile.filename);
        RequestBody requestBody = RequestBody.create(MediaType.parse(uploadFile.contentType), file);

        return RxUtils.toBodySingle(
                getS3Service(session).uploadToS3(
                        session.getUrl(),
                        requestBody,
                        uploadFile.md5Hash,
                        uploadFile.contentType))
                .toCompletable()
                .doOnCompleted(() -> {
                    // TODO: update upload status in DB, delete file
                    LOG.info("Upload succeeded for id: " + session.getId());
                }).andThen(
                        RxUtils.toBodySingle(api.completeUploadSession(session.getId())
                        ).onErrorReturn((t) -> {
                            LOG.info("Failed to call upload complete, server will recover", t);
                            return session;
                        }));
    }

    Single<UploadSession> getUploadSession(UploadFile uploadFile) {
        UploadSession cachedUploadSession = uploadDAO.getUploadSession(uploadFile.filename);
        if (cachedUploadSession != null && cachedUploadSession.getExpires().isAfterNow()) {
            return Single.just(cachedUploadSession);
        }

        return RxUtils.toBodySingle(
                api.requestUploadSession(
                        new UploadRequest()
                                .name(uploadFile.filename)
                                .contentType(uploadFile.contentType)
                                .contentLength(uploadFile.fileLength)
                                .contentMd5(uploadFile.md5Hash)))
                .doOnSuccess((uploadSession) -> {
                    LOG.info("Received uploadToS3 session with id: " + uploadSession.getId());
                    uploadDAO.putUploadSession(uploadFile.filename, uploadSession);
                });
    }

    @WorkerThread
    UploadFile persist(String filename, Archive archive) throws IOException,
            CMSException, NoSuchAlgorithmException {
        File file = new File(filename);

        ByteSink sink = Files.asByteSink(file, FileWriteMode.APPEND);

        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            LOG.error("MD5 digest not found", e);
            throw e;
        }

        DigestOutputStream md5OutStream = new DigestOutputStream(sink.openBufferedStream(), md5);

        OutputStream encryptedOutputStream = encryptor.encrypt(md5OutStream);

        archive.writeTo(encryptedOutputStream);

        String md5Hash = BaseEncoding.base64().encode(md5.digest());

        //TODO: write file metadata to db

        UploadFile uploadFile = new UploadFile();
        uploadFile.filename = filename;
        uploadFile.contentType = CONTENT_TYPE_DATA_ARCHIVE;
        uploadFile.fileLength = file.length();
        uploadFile.md5Hash = md5Hash;

        uploadDAO.putUploadFile(filename, uploadFile);

        return uploadFile;
    }

    private S3Service getS3Service(UploadSession uploadSession) {
        URI uri = URI.create(uploadSession.getUrl());
        String baseUrl = uri.getScheme() + "://" + uri.getHost() + "/";

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient).build();

        return retrofit.create(S3Service.class);
    }
}
