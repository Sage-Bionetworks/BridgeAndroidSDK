package org.sagebionetworks.bridge.android.manager;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sagebionetworks.bridge.android.manager.dao.UploadDAO;
import org.sagebionetworks.bridge.android.manager.upload.S3Service;
import org.sagebionetworks.bridge.data.AndroidStudyUploadEncryptor;
import org.sagebionetworks.bridge.rest.api.ForConsentedUsersApi;
import org.sagebionetworks.bridge.rest.model.UploadSession;
import org.sagebionetworks.bridge.rest.model.UploadValidationStatus;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import retrofit2.Call;
import retrofit2.Response;
import rx.Completable;
import rx.Single;
import rx.schedulers.TestScheduler;
import rx.subjects.TestSubject;

import static junit.framework.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.rest.model.UploadStatus.DUPLICATE;
import static org.sagebionetworks.bridge.rest.model.UploadStatus.REQUESTED;
import static org.sagebionetworks.bridge.rest.model.UploadStatus.SUCCEEDED;

/**
 * Created by jyliu on 3/22/2017.
 */
@SuppressWarnings("rawtypes")
public class UploadManagerTest {

    private static final String FILENAME = "archive.zip";
    private static final String UPLOAD_ID = "uploadId";
    private static final String UPLOAD_CONTENT_TYPE = "application/zip";
    private static final String UPLOAD_URL = "url";
    private static final String UPLOAD_MD5 = "hash";

    @Mock
    private ForConsentedUsersApi api;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private AndroidStudyUploadEncryptor studyUploadEncryptor;
    @Mock
    private UploadDAO uploadDAO;
    @Mock
    private File archive;
    @Mock
    private S3Service s3Service;

    private UploadManager spyUploadManager;

    private UploadManager.UploadFile uploadFile;
    private UploadSession uploadSession;
    private UploadValidationStatus uploadValidationStatus;


    @Before
    public void setupTest() {
        MockitoAnnotations.initMocks(this);

        when(authenticationManager.getAuthStateReference()).thenReturn(new AtomicReference<>(
                new AuthenticationManager.AuthStateHolder(api,null)));

        spyUploadManager = spy(new UploadManager(authenticationManager, studyUploadEncryptor, uploadDAO));

        uploadFile = new UploadManager.UploadFile();
        uploadFile.filename = FILENAME;
        uploadFile.contentType = UPLOAD_CONTENT_TYPE;
        uploadFile.md5Hash = UPLOAD_MD5;

        uploadSession = new UploadSession()
                .id(UPLOAD_ID)
                .url(UPLOAD_URL);

        uploadValidationStatus = new UploadValidationStatus()
                .id(UPLOAD_ID);

        when(archive.exists()).thenReturn(true);
    }

    @Test
    public void onSignedOut() throws Exception {
        TestScheduler scheduler = new TestScheduler();
        TestSubject subject = TestSubject.create(scheduler);


        Completable completable = subject.toCompletable();
        doReturn(completable).when(spyUploadManager).clearUploads();

        spyUploadManager.onSignedOut("email");

        verify(spyUploadManager).clearUploads();
        // verify something has subscribed to the clearUploads completable
        assertTrue(subject.hasObservers());

        subject.onCompleted();
        scheduler.triggerActions();
    }

    @Test
    public void testProcessUploadFile_NoCachedSession() throws Exception {
        when(uploadDAO.getUploadSession(FILENAME)).thenReturn(null);
        doReturn(Completable.complete()).when(spyUploadManager).processUploadForCachedSession(uploadFile, null);

        Completable completable = spyUploadManager.processUploadFile(uploadFile);
        completable.test().awaitTerminalEvent().assertCompleted();

        verify(uploadDAO).getUploadSession(FILENAME);
        verify(spyUploadManager).processUploadForCachedSession(uploadFile, null);
    }

    @Test
    public void testProcessUploadFile_HasCachedSession() throws Exception {
        when(uploadDAO.getUploadSession(FILENAME)).thenReturn(uploadSession);
        doReturn(Completable.complete()).when(spyUploadManager).processUploadForCachedSession(uploadFile, uploadSession);

        Completable completable = spyUploadManager.processUploadFile(uploadFile);
        completable.test().awaitTerminalEvent().assertCompleted();

        verify(uploadDAO).getUploadSession(FILENAME);
        verify(spyUploadManager).processUploadForCachedSession(uploadFile, uploadSession);
    }

    @Test
    public void testProcessUploadForCachedSession_NoCachedSession() throws Exception {
        doReturn(Single.just(uploadSession)).when(spyUploadManager).getUploadSession(uploadFile);
        doReturn(Single.just(uploadValidationStatus)).when(spyUploadManager).getUploadValidationStatus(UPLOAD_ID);
        doReturn(Completable.complete()).when(spyUploadManager).processUploadForValidationStatus(uploadFile, uploadSession, uploadValidationStatus);

        Completable completable = spyUploadManager.processUploadForCachedSession(uploadFile, null);
        completable.test().awaitTerminalEvent().assertCompleted();

        verify(spyUploadManager).getUploadSession(uploadFile);
        verify(spyUploadManager).getUploadValidationStatus(UPLOAD_ID);
        verify(spyUploadManager).processUploadForValidationStatus(uploadFile, uploadSession, uploadValidationStatus);
    }

    @Test
    public void testProcessUploadForCachedSession_HasCachedSession() throws Exception {
        doReturn(Single.just(uploadValidationStatus)).when(spyUploadManager).getUploadValidationStatus(UPLOAD_ID);
        doReturn(Completable.complete()).when(spyUploadManager)
                .processUploadForValidationStatus(uploadFile, uploadSession, uploadValidationStatus);

        Completable completable = spyUploadManager
                .processUploadForCachedSession(uploadFile, uploadSession);
        completable.test().awaitTerminalEvent().assertCompleted();

        verify(spyUploadManager).getUploadValidationStatus(UPLOAD_ID);
        verify(spyUploadManager).processUploadForValidationStatus(uploadFile, uploadSession, uploadValidationStatus);
    }

    @Test
    public void testProcessUploadForValidationStatus_Succeeeded() {
        uploadValidationStatus.status(SUCCEEDED);

        doReturn(Completable.complete()).when(spyUploadManager).dequeueUpload(FILENAME);

        Completable completable = spyUploadManager
                .processUploadForValidationStatus(uploadFile, uploadSession, uploadValidationStatus);
        completable.test().awaitTerminalEvent().assertCompleted();

        verify(spyUploadManager).dequeueUpload(FILENAME);
    }

    @Test
    public void testProcessUploadForValidationStatus_Duplicate() {
        uploadValidationStatus.status(DUPLICATE);

        doReturn(Completable.complete()).when(spyUploadManager).dequeueUpload(FILENAME);

        Completable completable = spyUploadManager
                .processUploadForValidationStatus(uploadFile, uploadSession, uploadValidationStatus);
        completable.test().awaitTerminalEvent().assertCompleted();

        verify(spyUploadManager).dequeueUpload(FILENAME);
    }

    @Test
    public void testProcessUploadForValidationStatus_Requested() {
        uploadValidationStatus.status(REQUESTED);

        doReturn(Completable.complete()).when(spyUploadManager).uploadToS3(uploadFile, uploadSession);

        Completable completable = spyUploadManager
                .processUploadForValidationStatus(uploadFile, uploadSession, uploadValidationStatus);
        completable.test().awaitTerminalEvent().assertCompleted();

        verify(spyUploadManager).uploadToS3(uploadFile, uploadSession);
    }

    @Test
    public void testUploadToS3_ExpiredSession() throws IOException {
        uploadSession.setExpires(DateTime.now());

        Call successCall = mock(Call.class);
        when(successCall.clone()).thenReturn(successCall);
        when(successCall.execute()).thenReturn(Response.success(null));

        UploadSession freshSession = new UploadSession()
                .id(UPLOAD_ID)
                .url(UPLOAD_URL);

        doReturn(Single.just(freshSession)).when(spyUploadManager).getUploadSession(uploadFile);
        doReturn(archive).when(spyUploadManager).getFile(FILENAME);
        doReturn(s3Service).when(spyUploadManager).getS3Service(freshSession);
        doReturn(successCall).when(s3Service).uploadToS3(eq(UPLOAD_URL), any(), eq(UPLOAD_MD5), eq(UPLOAD_CONTENT_TYPE));

        doReturn(successCall).when(api).completeUploadSession(eq(UPLOAD_ID), any());

        Completable completable = spyUploadManager.uploadToS3(uploadFile, uploadSession);
        completable.test().awaitTerminalEvent().assertCompleted();

        verify(spyUploadManager).getUploadSession(uploadFile);
        verify(spyUploadManager).getS3Service(freshSession);
        verify(s3Service).uploadToS3(eq(UPLOAD_URL), any(), eq(UPLOAD_MD5), any());
        verify(api).completeUploadSession(UPLOAD_ID, false);
    }
}