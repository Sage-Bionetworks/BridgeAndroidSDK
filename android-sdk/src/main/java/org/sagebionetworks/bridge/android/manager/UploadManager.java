package org.sagebionetworks.bridge.android.manager;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteSink;
import com.google.common.io.FileWriteMode;
import com.google.common.io.Files;

import org.sagebionetworks.bridge.android.data.Archive;
import org.sagebionetworks.bridge.android.data.StudyUploadEncryptor;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import retrofit2.Retrofit;
import rx.Single;
import rx.functions.Action1;

import static android.os.AsyncTask.THREAD_POOL_EXECUTOR;

/**
 * Created by jyliu on 1/30/2017.
 */

public class UploadManager {
    private static final Logger LOG = LoggerFactory.getLogger(UploadManager.class);
    private static final String CONTENT_TYPE_DATA_ARCHIVE = "application/zip";

    private final ForConsentedUsersApi api;
    private final StudyUploadEncryptor encryptor;
    private final OkHttpClient s3OkhttpClient;
    private final UploadFileDbHelper mDbHelper;
    private Object uploadLock = new Object();
    boolean isUploading = false;

    private ExecutorService archivePool;
    private ExecutorService uploadPool;

    public UploadManager(AuthenticationManager authenticationManager, StudyUploadEncryptor
            encryptor, OkHttpClient s3OkhttpClient, UploadFileDbHelper dbHelper) {
        this.api = authenticationManager.getApi();
        this.encryptor = encryptor;
        this.s3OkhttpClient = s3OkhttpClient;
        this.mDbHelper = dbHelper;

        this.archivePool = Executors.newFixedThreadPool(1);
        this.uploadPool = Executors.newFixedThreadPool(1);

        this.startUpload();
    }

    public Single<UploadValidationStatus> getStatus(String uploadId) {
        return RxUtils.toBodySingle(api.getUploadStatus(uploadId));
    }

    @NonNull
    public Single<UploadValidationStatus> upload(String filename, Archive archive) {
        Single<UploadFile> uploadFileSingle = Single
                .fromCallable(() -> persist(filename, archive)).cache();

        Single<UploadSession> uploadSessionSingle = uploadFileSingle
                .flatMap(this::requestUploadSession);

        return Single.zip(uploadFileSingle, uploadSessionSingle, this::uploadToS3).flatMap(i -> i);
    }

    @NonNull
    Single<UploadValidationStatus> uploadToS3(UploadFile uploadFile, UploadSession session) {

        File file = getFile(uploadFile.filename);

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
                    LOG.info("Upload succeeded for upload file :" + uploadFile.filename);
                }).doOnError(t -> {
                    LOG.info("Couldn't upload to s3", t);
                }).andThen(
                        RxUtils.toBodySingle(api.completeUploadSession(session.getId())
                        ).onErrorReturn((t) -> {
                            LOG.info("Failed to call upload complete, server will recover", t);
                            return session;
                        })).toCompletable()
                .andThen(getStatus(session.getId()));
    }

    Single<UploadSession> requestUploadSession(UploadFile uploadFile) {
        return RxUtils.toBodySingle(
                api.requestUploadSession(
                        new UploadRequest()
                                .name(uploadFile.filename)
                                .contentType(uploadFile.contentType)
                                .contentLength(uploadFile.fileLength)
                                .contentMd5(uploadFile.md5Hash)))
                .doOnSuccess((uploadSession) -> {
                    // TODO: insert upload session to DB, link to file metadata
                    LOG.info("Received upload session with id: " + uploadSession.getId());
                });
    }

    UploadFile persist(String filename, Archive archive) throws NoSuchAlgorithmException, IOException {
        File file = getFile(filename);

        ByteSink sink = Files.asByteSink(file, FileWriteMode.APPEND);

        MessageDigest md5 = null;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            LOG.error("MD5 digest not found", e);
            throw e;
        }

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
                .client(s3OkhttpClient).build();

        return retrofit.create(S3Service.class);
    }


    public void addArchive(String filename, Archive archive) {

        LOG.info("Adding archive for " + filename);

        UploadFile uploadFile;
        try {
            uploadFile = persist(filename, archive);
        } catch (Exception e) {
            e.printStackTrace();
            return;
//            throw e;
        }

        //start async task here

        class ArchiveTask extends AsyncTask<Void, Void, Void> {

            @Override
            protected Void doInBackground(Void... params) {

                //add upload file to Db
                SQLiteDatabase db = mDbHelper.getWritableDatabase();

                ContentValues values = new ContentValues();
                values.put(UploadFileContract.UploadFileSchema.COLUMN_NAME_FILENAME, uploadFile.filename);
                values.put(UploadFileContract.UploadFileSchema.COLUMN_NAME_CONTENT_TYPE, uploadFile.contentType);
                values.put(UploadFileContract.UploadFileSchema.COLUMN_NAME_FILE_LENGTH, uploadFile.fileLength);
                values.put(UploadFileContract.UploadFileSchema.COLUMN_NAME_MD5_HASH, uploadFile.md5Hash);

                long newRowId = db.insert(UploadFileContract.UploadFileSchema.TABLE_NAME, null, values);

                LOG.info("Upload file archived for " + uploadFile.filename);

                return null;

            }

            protected void onPostExecute(Void result) {
                startUpload();
            }
        }

        new ArchiveTask().executeOnExecutor(this.archivePool);
    }


    public void startUpload() {
        //start async task here
        class UploadTask extends AsyncTask<Void, Void, Void> {

            @Override
            protected Void doInBackground(Void... params) {

                UploadManager.this.tryToUpload();

                return null;

            }
        }

        new UploadTask().executeOnExecutor(this.uploadPool);
    }

    private void tryToUpload() {

//        assert(this.isSignedIn());

        synchronized (this.uploadLock) {

            if (this.isUploading) {
                return;
            }

            this.isUploading = true;

            //get readable database
            SQLiteDatabase db = mDbHelper.getReadableDatabase();
            String[] projection = {
                    UploadFileContract.UploadFileSchema.COLUMN_NAME_FILENAME,
                    UploadFileContract.UploadFileSchema.COLUMN_NAME_CONTENT_TYPE,
                    UploadFileContract.UploadFileSchema.COLUMN_NAME_FILE_LENGTH,
                    UploadFileContract.UploadFileSchema.COLUMN_NAME_MD5_HASH
            };

            String sortOrder = UploadFileContract.UploadFileSchema._ID + " ASC";

            Cursor cursor = db.query(
                    UploadFileContract.UploadFileSchema.TABLE_NAME,
                    projection,
                    null, //selection
                    null, //selectionArgs
                    null, //groupBy
                    null, //having
                    sortOrder,
                    "1"
            );

            //get entry
            if (!cursor.moveToNext()) {
                this.isUploading = false;
                return;
            }
            //create UploadFile

            UploadFile uploadFile = new UploadFile();
            uploadFile.filename = cursor.getString(cursor.getColumnIndexOrThrow(UploadFileContract.UploadFileSchema.COLUMN_NAME_FILENAME));
            uploadFile.contentType = cursor.getString(cursor.getColumnIndexOrThrow(UploadFileContract.UploadFileSchema.COLUMN_NAME_CONTENT_TYPE));
            uploadFile.fileLength = cursor.getLong(cursor.getColumnIndexOrThrow(UploadFileContract.UploadFileSchema.COLUMN_NAME_FILE_LENGTH));
            uploadFile.md5Hash = cursor.getString(cursor.getColumnIndexOrThrow(UploadFileContract.UploadFileSchema.COLUMN_NAME_MD5_HASH));

            LOG.info("starting upload for upload file :" + uploadFile.filename);

            Single<UploadFile> uploadFileSingle = Single
                    .fromCallable(() -> uploadFile).cache();

            Single<UploadSession> uploadSessionSingle = uploadFileSingle
                    .flatMap(this::requestUploadSession);

            Single<UploadValidationStatus> validationStatusSingle = Single.zip(uploadFileSingle, uploadSessionSingle, this::uploadToS3).flatMap(i -> i);

            validationStatusSingle.subscribe(new Action1<UploadValidationStatus>() {
                @Override
                public void call(UploadValidationStatus uploadValidationStatus) {
                    //if status is good, remove

                    //add upload file to Db
                    SQLiteDatabase db = mDbHelper.getWritableDatabase();

                    String selection = UploadFileContract.UploadFileSchema.COLUMN_NAME_FILENAME + " LIKE ?";
                    String[] selectionArgs = { uploadFile.filename };
                    db.delete(UploadFileContract.UploadFileSchema.TABLE_NAME, selection, selectionArgs);

                    //delete archive file
                    File file = getFile(uploadFile.filename);
                    boolean deleted = file.delete();

                    LOG.info("Completing upload for upload file :" + uploadFile.filename);

                    synchronized (uploadLock) {
                        isUploading = false;
                    }

                    UploadManager.this.startUpload();
                }
            },
            new Action1<Throwable>() {
                @Override
                public void call(Throwable error) {

                    //we need to better understand the errors
                    //that can happen
                    error.printStackTrace();

                    //we want to retry uploads, but the risk is that if there is some kind of
                    //malformed datapoint, it shouldn;t block the rest of the uploads

                    synchronized (uploadLock) {
                        isUploading = false;
                    }

                }
            });

        }
    }

    void finishUpload(UploadFile uploadFile) {

    }

}
