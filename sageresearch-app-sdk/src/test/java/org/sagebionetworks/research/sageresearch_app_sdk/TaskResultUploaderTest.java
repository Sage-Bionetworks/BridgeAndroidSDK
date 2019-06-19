/*
 * BSD 3-Clause License
 *
 * Copyright 2018  Sage Bionetworks. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1.  Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2.  Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3.  Neither the name of the copyright holder(s) nor the names of any contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission. No license is granted to the trademarks of
 * the copyright holders even if such marks are included in this software.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.sagebionetworks.research.sageresearch_app_sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sagebionetworks.bridge.android.BridgeConfig;
import org.sagebionetworks.bridge.android.manager.AuthenticationManager;
import org.sagebionetworks.bridge.android.manager.UploadManager;
import org.sagebionetworks.bridge.android.manager.UploadManager.UploadFile;
import org.sagebionetworks.bridge.android.manager.upload.SchemaKey;
import org.sagebionetworks.bridge.data.Archive;
import org.sagebionetworks.bridge.rest.model.ScheduledActivity;
import org.sagebionetworks.research.domain.result.interfaces.TaskResult;
import org.sagebionetworks.research.sageresearch.dao.room.ScheduleRepository;
import org.sagebionetworks.research.sageresearch_app_sdk.archive.AbstractResultArchiveFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.reactivex.Single;

public class TaskResultUploaderTest {

    @Mock
    BridgeConfig bridgeConfig;

    @Mock
    AbstractResultArchiveFactory abstractResultArchiveFactory;

    @Mock
    UploadManager uploadManager;

    @Mock
    AuthenticationManager authManager;

    @Mock
    ScheduleRepository scheduleRepo;

    TaskResultUploader taskResultUploader;

    @Before
    public void beforeTest() {
        MockitoAnnotations.initMocks(this);

        taskResultUploader = new TaskResultUploader(bridgeConfig, abstractResultArchiveFactory, uploadManager,
                authManager, scheduleRepo);
    }

    @Test
    public void processTaskResult() {
        TaskResultUploader spyTaskResultUploader = spy(taskResultUploader);

        String taskIdentifier = "taskId";
        UUID taskUUID = UUID.randomUUID();
        int schemaVersion = 4;

        TaskResult taskResult = mock(TaskResult.class);
        when(taskResult.getIdentifier()).thenReturn(taskIdentifier);
        when(taskResult.getTaskUUID()).thenReturn(taskUUID);

        SchemaKey schemaKey = mock(SchemaKey.class);
        when(schemaKey.getId()).thenReturn(taskIdentifier);
        when(schemaKey.getRevision()).thenReturn(schemaVersion);

        Map<String, SchemaKey> taskToSchema = new HashMap<>();
        taskToSchema.put(taskIdentifier, schemaKey);

        when(bridgeConfig.getTaskToSchemaMap()).thenReturn(taskToSchema);

        Archive archive = mock(Archive.class);

        UploadFile uploadFile = mock(UploadFile.class);
        when(uploadManager.queueUpload(any(), eq(archive))).thenReturn(rx.Single.just(uploadFile));
        when(uploadManager.processUploadFile(uploadFile)).thenReturn(rx.Completable.complete());

        doReturn(Single.just(archive)).when(spyTaskResultUploader).archiveSingle(schemaKey, taskResult);

        spyTaskResultUploader.processTaskResult(taskResult).blockingAwait();

        verify(uploadManager).queueUpload(any(), eq(archive));
        verify(uploadManager).processUploadFile(uploadFile);
    }

    @Test
    public void testCreateScheduledActivityForMetadata_NullScheduledActivityEntity() {
        String taskIdentifier = "taskIdentifier";

        TaskResult taskResult = mock(TaskResult.class);
        when(taskResult.getIdentifier()).thenReturn(taskIdentifier);

        ScheduledActivity scheduledActivity = taskResultUploader.createScheduledActivityForMetadata(taskResult, null);

        assertNotNull(scheduledActivity.getActivity());
        assertNotNull(scheduledActivity.getActivity().getTask());
        assertEquals(taskIdentifier, scheduledActivity.getActivity().getTask().getIdentifier());
    }
}