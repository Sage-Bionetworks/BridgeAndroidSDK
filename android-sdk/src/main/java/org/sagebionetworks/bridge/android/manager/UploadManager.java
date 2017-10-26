package org.sagebionetworks.bridge.android.manager;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;

import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteSink;
import com.google.common.io.FileWriteMode;
import com.google.common.io.Files;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.android.manager.dao.UploadDAO;
import org.sagebionetworks.bridge.android.manager.upload.S3Service;
import org.sagebionetworks.bridge.android.util.retrofit.RxUtils;
import org.sagebionetworks.bridge.data.AndroidStudyUploadEncryptor;
import org.sagebionetworks.bridge.data.Archive;
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
import java.util.Set;
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
 * Manages upload of Archive files to Bridge for processing.
 * <p>
 * To upload an Archive for processing, an UploadSession is requested from Bridge. This UploadSession
 * contains a pre-signed URL to which the Archive should be uploaded via a PUT request. After upload,
 * optionally call to notify Bridge of the completion of upload, if this call is not made, Bridge
 * will be notified later by a server-side background process.
 * <p>
 * When Bridge is notified of the completion of an upload, it will run validation on the Archive.
 * The validation includes steps such as decryption, unzipping, parsing JSON files, and schema
 * validation in order to prepare the file for upload.
 * <p>
 * The UploadValidationStatus can be retrieved from Bridge to determine how much progress has been
 * made, e.g. whether the file has been uploaded, whether the validation succeeded, failed or a
 * duplicate archive was detected.
 */
public class UploadManager implements AuthenticationManager.AuthenticationEventListener {
    private static final Logger LOG = LoggerFactory.getLogger(UploadManager.class);
    private static final String CONTENT_TYPE_DATA_ARCHIVE = "application/zip";

    // minimum number of minutes from now an expiration should be
    private static final int UPLOAD_EXPIRY_WINDOW_MINUTES = 30;

    private final ForConsentedUsersApi api;
    private final AndroidStudyUploadEncryptor encryptor;
    private final UploadDAO uploadDAO;
    private final OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false).build();

    public UploadManager(AuthenticationManager authenticationManager, AndroidStudyUploadEncryptor
            encryptor, UploadDAO uploadDAO) {
        this.api = authenticationManager.getApi();
        authenticationManager.addEventListener(this);
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
     * Persists the archive on disk and add it to the queue of pending uploads, runs in an IO thread.
     *
     * @param filename filename for the archive
     * @param archive  archive to be queued
     * @return information about file produced from the archive
     */
    @NonNull
    public Single<UploadFile> queueUpload(String filename, Archive archive) {
        LOG.debug("Queueing archive: " + archive);
        return Single.fromCallable(() -> persist(filename, archive))
                .subscribeOn(Schedulers.io());
    }

    /**
     * Clears all queued uploads
     */
    Completable clearUploads() {
        return Completable.merge(
                getUploadFilenames()
                        .map(this::dequeueUpload));
    }

    @Override
    public void onSignedOut(String email) {
        clearUploads().subscribe();
    }

    @Override
    public void onSignedIn(String email) {

    }

    /**
     * This triggers an attempt to upload all the previously queued upload files. Call queueUpload
     * to add UploadFiles for this method to operate on.
     * <p>
     * Retrieves cached UploadSession (if one exists) for each UploadFile and perform the next step
     * in the upload flow by calling this{@link #processUploadForCachedSession(UploadFile, UploadSession)}.
     *
     * @return Observable with information on if the upload was successful or not
     */
    @NonNull
    public Completable processUploadFiles() {
        return Completable.mergeDelayError(
                getUploadFilenames()
                        .map(uploadDAO::getUploadFile)
                        .map(uploadFile ->
                                processUploadForCachedSession(
                                        uploadFile,
                                        uploadDAO.getUploadSession(uploadFile.filename)
                                )));
    }

    /**
     * @return observable of queued filenames
     */
    Observable<String> getUploadFilenames() {
        Set<String> filenames = uploadDAO.listUploadFilenames();

        return Observable.from(filenames.toArray(new String[filenames.size()]));
    }

    /**
     * Retrieves cached UploadSession (if one exists) for an UploadFile and calls the next step
     * of the upload flow: {@link #processUploadForCachedSession(UploadFile, UploadSession)}.
     *
     * @param uploadFile file to upload
     * @return completable
     */
    @NonNull
    public Completable processUploadFile(@NonNull UploadFile uploadFile) {
        checkNotNull(uploadFile);

        Single<UploadSession> cachedSessionSingle =
                Single.just(uploadDAO.getUploadSession(uploadFile.filename));

        return Single.zip(
                Single.just(uploadFile),
                cachedSessionSingle,
                this::processUploadForCachedSession
        ).flatMapCompletable(i -> i);
    }

    /**
     * Uses the provided cached UploadSession or retrieves one from Bridge to retrieve the status
     * of the upload and calls the next step of the upload flow:
     * {@link #processUploadForValidationStatus(UploadFile, UploadSession, UploadValidationStatus)}
     *
     * @param uploadFile    file to upload
     * @param cachedSession locally cached upload session, or null if not in cache
     * @return completion of next upload step
     */
    @NonNull
    Completable processUploadForCachedSession(@NonNull UploadFile uploadFile,
                                              @Nullable UploadSession cachedSession) {
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
                this::processUploadForValidationStatus
        ).flatMapCompletable(i -> i);
    }

    /**
     * Performs the next upload step, according to the UploadValidationStatus. The normal
     * transition of UploadValidationStatus is from REQUESTED to VALIDATION_IN_PROGRESS to
     * SUCCEEDED.
     *
     * @param uploadFile             file being uploaded
     * @param uploadSession          pre-signed upload session
     * @param uploadValidationStatus upload validation status from Bridge
     * @return completion of next upload step
     */
    @NonNull
    Completable processUploadForValidationStatus(@NonNull UploadFile uploadFile,
                                                 @NonNull UploadSession uploadSession,
                                                 @NonNull UploadValidationStatus uploadValidationStatus) {
        checkNotNull(uploadFile, "uploadFile cannot be null");
        checkNotNull(uploadSession, "uploadSession cannot be null");
        checkNotNull(uploadValidationStatus, "uploadValidationSession cannot be null");

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
    @NonNull
    Completable dequeueUpload(@NonNull String filename) {
        checkNotNull(filename, "filename required");

        return Completable.fromAction(() -> {
            if (getFile(filename).delete()) {
                LOG.warn("Successfully deleted upload file: " + filename + ", "
                        + "removing upload from queue");
            } else {
                LOG.warn("Failed to delete upload file: " + filename);
            }
        }).doOnCompleted(() -> uploadDAO.removeUploadAndSession(filename))
                .subscribeOn(Schedulers.io());
    }

    /**
     * @param uploadId Bridge upload identifier
     * @return upload validation status single
     */
    @NonNull
    Single<UploadValidationStatus> getUploadValidationStatus(@NonNull String uploadId) {
        checkNotNull(uploadId, "uploadId required");

        return RxUtils.toBodySingle(api.getUploadStatus(uploadId));
    }

    /**
     * Uses the pre-signed URL in UploadSession to upload the file to S3. If the provided pre-signed
     * URL has expired or is nearing expiry, request a new UploadSession before uploading the file.
     *
     * @param uploadFile file being uploaded
     * @param session    pre-signed upload session
     * @return completable
     */
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
                                freshSession.getUrl(),
                                requestBody,
                                uploadFile.md5Hash,
                                uploadFile.contentType)))
                .doOnSuccess(aVoid -> {
                    LOG.info("S3 upload succeeded for id: " + session.getId());

                    // call upload complete on a computation thread
                    RxUtils.toBodySingle(api.completeUploadSession(session.getId(), false))
                            .doOnSuccess(val -> {
                                LOG.info("Call to upload complete succeeded");
                            })
                            .onErrorReturn((t) -> {
                                LOG.info("Failed to call upload complete, server will recover", t);
                                return null; // return doesn't matter, becomes completable
                            })
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

    File getFile(String filename) {
        return new File(BridgeManagerProvider.getInstance()
                .getApplicationContext().getFilesDir().getAbsolutePath() + File.separator + filename);
    }

    S3Service getS3Service(UploadSession uploadSession) {
        URI uri = URI.create(uploadSession.getUrl());
        String baseUrl = uri.getScheme() + "://" + uri.getHost() + "/";

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient).build();

        return retrofit.create(S3Service.class);
    }
}
