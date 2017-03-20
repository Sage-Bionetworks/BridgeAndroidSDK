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
import rx.Completable;
import rx.Observable;
import rx.Single;

import static com.google.common.base.Preconditions.checkState;

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

    /**
     * This triggers an attempt to upload all the previously queued upload files
     * You must call queueUpload before this method will have anything to operate on
     *
     * @return Observable with information on if the upload was successful or not
     */
    public Observable<UploadValidationStatus> uploadToS3() {
        Observable<String> filenameObservable = Observable.from(uploadDAO
                .listUploadFilenames());

        return filenameObservable
                .flatMap(filename -> uploadToS3(filename).toObservable());
    }

    /**
     * Uploads a previously queued upload.
     *
     * @param filename filename to upload
     * @return Single of current upload validation status, which could be null if pending upload not found
     */
    public Single<UploadValidationStatus> uploadToS3(String filename) {
        UploadFile uploadFile = uploadDAO.getUploadFile(filename);
        if (uploadFile == null) {
            LOG.warn("Could not find uploadFile for filename: " + filename);

            return Single.just(null);
        }

        return getUploadSession(uploadFile)
                .flatMap(uploadSession -> getUploadValidationStatus(uploadSession.getId()))
                .flatMap((uploadValidationStatus) -> {
                    if (uploadValidationStatus == null) {
                        LOG.info("Could not find uploadValidationStatus for filename: " + filename);
                        return Single.just(null);
                    }

                    if (UploadStatus.SUCCEEDED == uploadValidationStatus.getStatus()) {
                        LOG.info("Cleaning up successful upload with filename: " + filename
                                + ", id: " + uploadValidationStatus.getId());
                        uploadDAO.removeUploadAndSession(filename);
                        return Single.just(null);
                    } else if (UploadStatus.VALIDATION_FAILED == uploadValidationStatus.getStatus()) {
                        LOG.info("Cleaning up validation-failed upload with filename: " + filename
                                + ", id: " + uploadValidationStatus.getId());
                        uploadDAO.removeUploadAndSession(filename);
                        return Single.just(null);
                    }

                    return getUploadSession(uploadFile)
                            .flatMapCompletable(uploadSession -> uploadToS3(uploadFile, uploadSession))
                            .andThen(getUploadValidationStatus((uploadValidationStatus.getId())));
                }).doOnSuccess(uploadValidationStatus -> {
                    LOG.debug("uploadToS3 succeded, uploadValidationStatus: " + uploadValidationStatus);
                    if (uploadValidationStatus == null) {
                        LOG.warn("null uploadValidationStatus");
                    }

                }).onErrorReturn(throwable -> {
                    LOG.warn("Failed attempt to upload pending file: " + filename, throwable);
                    if (isPermanentFailure(uploadFile, throwable)) {
                        uploadDAO.removeUploadAndSession(filename);
                    }
                    return null;
                });
    }

    // determines whether an upload failure is permanent, i.e. no point in retrying
    boolean isPermanentFailure(UploadFile uploadFile, Throwable throwable) {
        if (throwable instanceof IllegalStateException) {
            // some pre-condition failed
            return true;
        }
        return false;
    }

    Single<UploadValidationStatus> getUploadValidationStatus(String uploadId) {
        if (uploadId == null) {
            LOG.warn("Cannot retrieve validation status for null uploadId");
            return Single.just(null);
        }
        return RxUtils.toBodySingle(api.getUploadStatus(uploadId));
    }

    Completable uploadToS3(UploadFile uploadFile, UploadSession session) {
        File file = getFile(uploadFile.filename);
        checkState(file.exists(), "Non-existent file: " + file.getAbsolutePath());

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
                        })
                ).toCompletable();
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
        File file = getFile(filename);

        ByteSink sink = Files.asByteSink(file, FileWriteMode.APPEND);

        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            LOG.error("MD5 digest not found", e);
            throw e;
        }

        LOG.debug("Writing archive with filename: " + filename + ", with contents: " + archive);

        OutputStream os = sink.openBufferedStream();
        try {
            DigestOutputStream md5OutStream = new DigestOutputStream(os, md5);

            OutputStream encryptedOutputStream = encryptor.encrypt(md5OutStream);
            try {
                archive.writeTo(encryptedOutputStream);
            } finally {
                encryptedOutputStream.close();
            }
        } catch (CMSException e) {
            e.printStackTrace();
        } finally {
            os.close();
        }

        String md5Hash = BaseEncoding.base64().encode(md5.digest());

        UploadFile uploadFile = new UploadFile();
        uploadFile.filename = filename;
        uploadFile.contentType = CONTENT_TYPE_DATA_ARCHIVE;
        uploadFile.fileLength = file.length();
        uploadFile.md5Hash = md5Hash;

        uploadDAO.putUploadFile(filename, uploadFile);

        return uploadFile;
    }

    private File getFile(String filename) {
        return new File(BridgeManagerProvider.getInstance()
                .getApplicationContext().getFilesDir().getAbsolutePath() + File.separator + filename);
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
