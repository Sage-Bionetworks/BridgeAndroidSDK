package org.sagebionetworks.bridge.android.manager;

import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;

import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteSink;
import com.google.common.io.FileWriteMode;
import com.google.common.io.Files;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.android.data.Archive;
import org.sagebionetworks.bridge.android.data.StudyUploadEncryptor;
import org.sagebionetworks.bridge.android.manager.dao.UploadDAO;
import org.sagebionetworks.bridge.android.manager.upload.S3Service;
import org.sagebionetworks.bridge.android.util.retrofit.RxUtils;
import org.sagebionetworks.bridge.rest.api.ForConsentedUsersApi;
import org.sagebionetworks.bridge.rest.model.UploadRequest;
import org.sagebionetworks.bridge.rest.model.UploadSession;
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
import rx.schedulers.Schedulers;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by jyliu on 1/30/2017.
 */
public class UploadManager {
    private static final Logger LOG = LoggerFactory.getLogger(UploadManager.class);
    private static final String CONTENT_TYPE_DATA_ARCHIVE = "application/zip";

    // minimum number of minutes from now an expiration should be
    private static final int UPLOAD_EXPIRY_WINDOW_MINUTES = 30;

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
        public DateTime createdOn;
    }

    /**
     * Persists the archive on disk and add it to the queue of pending uploads.
     *
     * @param filename filename for the archive
     * @param archive  archive to be queued
     * @return information about file produced from the archive
     */
    @NonNull
    public Single<UploadFile> queueUpload(String filename, Archive archive) {
        return Single.fromCallable(() -> persist(filename, archive));
    }

    /**
     * This triggers an attempt to upload all the previously queued upload files
     * You must call queueUpload before this method will have anything to operate on
     *
     * @return Observable with information on if the upload was successful or not
     */
    @NonNull
    public Completable processUploadFiles() {
        Observable<String> filenameObservable = Observable.from(uploadDAO
                .listUploadFilenames());

        return filenameObservable
                .map(filename -> uploadDAO.getUploadFile(filename))
                .flatMap(uploadFile -> processUploadFile(uploadFile).toObservable())
                .toCompletable();
    }

    /**
     * Performs next upload step for a file.
     *
     * @param uploadFile file to upload
     * @return completable of next upload step
     */
    public Completable processUploadFile(UploadFile uploadFile) {
        Single<UploadSession> cachedSessionSingle =
                Single.just(uploadDAO.getUploadSession(uploadFile.filename));

        return Single.zip(
                Single.just(uploadFile),
                cachedSessionSingle,
                this::processUpload
        ).flatMapCompletable(i -> i);
    }

    /**
     * Performs the next upload step, retrieving an UploadSession from local cache or from Bridge.
     *
     * @param uploadFile    file to upload
     * @param cachedSession locally cached upload session
     * @return completion of next upload step
     */
    Completable processUpload(UploadFile uploadFile, UploadSession cachedSession) {
        checkNotNull(uploadFile, "uploadFile cannot be null");

        Single<UploadSession> sessionSingle;
        if (cachedSession != null) {
            // we don't renew expired sessions, because we don't yet know the upload status
            // successfully completed uploads do not need a new session
            sessionSingle = Single.just(cachedSession);
        } else {
            sessionSingle = getUploadSession(uploadFile).cache();
        }

        Single<UploadValidationStatus> statusSingle = sessionSingle
                .flatMap(uploadSession -> getUploadValidationStatus(uploadSession.getId()));

        return Single.zip(
                Single.just(uploadFile),
                sessionSingle,
                statusSingle,
                this::processUpload
        ).flatMapCompletable(i -> i);
    }

    /**
     * Performs the next upload step, according to the UploadValidationStatus. The normal
     * transition of UploadValidationStatus is from REQUESTED to VALIDATION_IN_PROGRESS to
     * REQUESTED.
     *
     * @param uploadFile             file being uploaded
     * @param uploadSession          pre-signed upload session
     * @param uploadValidationStatus upload validation status from Bridge
     * @return completion of next upload step
     */
    Completable processUpload(UploadFile uploadFile, UploadSession uploadSession, UploadValidationStatus uploadValidationStatus) {
        checkNotNull(uploadFile, "uploadFile cannot be null");
        checkNotNull(uploadSession, "uploadSession cannot be null");

        switch (uploadValidationStatus.getStatus()) {
            case REQUESTED:
                return uploadToS3(uploadFile, uploadSession);
            case SUCCEEDED:
            case DUPLICATE:
                return dequeueUpload(uploadFile.filename);
            case VALIDATION_IN_PROGRESS:
                LOG.debug("Validation in progress for filename: " + uploadFile.filename +
                        ", uploadId" + uploadSession.getId());
                break;
            case VALIDATION_FAILED:
                LOG.debug("Validation failed for filename: " + uploadFile.filename +
                        ", uploadId" + uploadSession.getId());
                break;
            case UNKNOWN:
            default:
                LOG.warn("Unknown status for uploadId: " + uploadValidationStatus.getId());
        }
        // return a successful completion when there's nothing to do
        // leaves the files queued, in case there's future recovery options (e.g. next
        // release), will add removal for permanent failures
        return Completable.complete();
    }

    /**
     * Deletes a file from disk and removes it from the upload queue.
     *
     * @param filename the file's name
     * @return dequeueing completable
     */
    Completable dequeueUpload(String filename) {
        if (getFile(filename).delete()) {
            LOG.warn("Successfully deleted upload file: " + filename + ", "
                    + "removing upload from queue");
            return Completable.fromAction(() -> uploadDAO.removeUploadAndSession(filename));
        } else {
            LOG.warn("Failed to delete upload file: " + filename);
            return Completable.error(new IOException("Failed to delete upload file: " + filename));
        }
    }

    /**
     * @param uploadId Bridge upload identifier
     * @return upload validation status single
     */
    @NonNull
    Single<UploadValidationStatus> getUploadValidationStatus(String uploadId) {
        if (uploadId == null) {
            LOG.warn("Cannot retrieve validation status for null uploadId");
            return Single.just(null);
        }
        return RxUtils.toBodySingle(api.getUploadStatus(uploadId));
    }

    @NonNull
    Completable uploadToS3(UploadFile uploadFile, UploadSession session) {
        File file = getFile(uploadFile.filename);
        checkArgument(file.exists(), "Non-existent file: " + file.getAbsolutePath());

        Single<UploadSession> sessionSingle = Single.just(session)
                .flatMap(uploadSession -> {
                    DateTime desiredMinimumExpiration = DateTime.now()
                            .plusMinutes(UPLOAD_EXPIRY_WINDOW_MINUTES);

                    if (uploadSession.getExpires().isBefore(desiredMinimumExpiration)) {
                        // get a fresh upload session
                        return getUploadSession(uploadFile);
                    }
                    // reuse current upload session
                    return Single.just(session);
                });

        RequestBody requestBody = RequestBody.create(MediaType.parse(uploadFile.contentType), file);

        return sessionSingle.flatMap(freshSession -> RxUtils.toBodySingle(
                getS3Service(freshSession)
                        .uploadToS3(
                                session.getUrl(),
                                requestBody,
                                uploadFile.md5Hash,
                                uploadFile.contentType)))
                .doOnSuccess(aVoid -> {
                    LOG.info("S3 upload succeeded for id: " + session.getId());

                    // call upload complete on a computation thread
                    RxUtils.toBodySingle(api.completeUploadSession(session.getId()))
                            .doOnSuccess(val -> {
                                LOG.info("Call to upload complete succeeded");
                            })
                            .onErrorReturn((t) -> {
                                LOG.info("Failed to call upload complete, server will recover", t);
                                return session;
                            })
                            .subscribeOn(Schedulers.computation())
                            .subscribe();

                }).toCompletable();
    }

    @NonNull
    Single<UploadSession> getUploadSession(UploadFile uploadFile) {
        return RxUtils.toBodySingle(
                api.requestUploadSession(
                        new UploadRequest()
                                .name(uploadFile.filename)
                                .contentType(uploadFile.contentType)
                                .contentLength(uploadFile.fileLength)
                                .contentMd5(uploadFile.md5Hash)))
                .doOnSuccess((uploadSession) -> {
                    LOG.info("Received processUploadFiles session with id: " + uploadSession.getId());
                    uploadDAO.putUploadSession(uploadFile.filename, uploadSession);
                });
    }

    @WorkerThread
    @NonNull
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
        uploadFile.createdOn = DateTime.now();

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
