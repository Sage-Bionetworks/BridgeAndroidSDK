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

import android.content.Context;

import com.google.common.collect.ImmutableList;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.subjects.CompletableSubject;
import rx.functions.Action1;

import org.sagebionetworks.bridge.android.BridgeConfig;
import org.sagebionetworks.bridge.android.manager.UploadManager;
import org.sagebionetworks.bridge.android.manager.dao.AccountDAO;
import org.sagebionetworks.bridge.android.manager.upload.ArchiveUtil;
import org.sagebionetworks.bridge.android.manager.upload.SchemaKey;
import org.sagebionetworks.bridge.data.Archive;
import org.sagebionetworks.bridge.data.ArchiveFile;
import org.sagebionetworks.bridge.data.JsonArchiveFile;
import org.sagebionetworks.bridge.rest.model.Activity;
import org.sagebionetworks.bridge.rest.model.ScheduledActivity;
import org.sagebionetworks.bridge.rest.model.TaskReference;
import org.sagebionetworks.research.domain.result.interfaces.TaskResult;
import org.sagebionetworks.research.presentation.perform_task.TaskResultProcessingManager.TaskResultProcessor;
import org.sagebionetworks.research.sageresearch.dao.room.ScheduledActivityEntity;
import org.sagebionetworks.research.sageresearch.viewmodel.ScheduleRepository;
import org.sagebionetworks.research.sageresearch_app_sdk.archive.AbstractResultArchiveFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Locale;

public class TaskResultUploader implements TaskResultProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskResultUploader.class);
    private final BridgeConfig bridgeConfig;
    private AbstractResultArchiveFactory abstractResultArchiveFactory;
    private AccountDAO accountDAO;
    private UploadManager uploadManager;
    private ScheduleRepository scheduleRepo;

    @Inject
    public TaskResultUploader(final BridgeConfig bridgeConfig,
                              final AbstractResultArchiveFactory abstractResultArchiveFactory,
                              final UploadManager uploadManager,
                              final AccountDAO accountDAO,
                              Context context) {
        this.bridgeConfig = bridgeConfig;
        this.abstractResultArchiveFactory = abstractResultArchiveFactory;
        this.uploadManager = uploadManager;
        this.accountDAO = accountDAO;
        this.scheduleRepo = ScheduleRepository.Companion.getInstance(context);
    }

    @Override
    public Completable processTaskResult(final TaskResult taskResult) {
        LOGGER.info("Processing task result: {}", taskResult);

        // TODO: mdephillips 10/2/18 convert to a completable and integrate into this flow to create metadata
        scheduleRepo.findSchedule(taskResult.getTaskUUID(), scheduledActivityEntity -> {

        }, throwable -> {
            // still upload to s3, but we will not be able to build the metadata file
        });

        // TODO: wrap operations in completable
        SchemaKey sk = bridgeConfig.getTaskToSchemaMap().get(taskResult.getIdentifier());

        if (sk == null) {
            LOGGER.info("No schema key found for task with identifier: {}, skipping upload",
                    taskResult.getIdentifier());
            // TODO: use taskId/1 as default for schema/revision?
            return Completable.complete();
        }

        Archive.Builder builder = Archive.Builder.forActivity(sk.getId(), sk.getRevision());
        String appVersionString = String.format(Locale.ENGLISH, "version %s, build %d",
                bridgeConfig.getAppVersionName(),
                bridgeConfig.getAppVersion());

        builder.withAppVersionName(appVersionString)
                .withPhoneInfo(bridgeConfig.getDeviceName());

        //TODO: load scheduled activity
        ScheduledActivity sa = new ScheduledActivity();
        sa.activity(new Activity()
                .task(new TaskReference().identifier(taskResult.getIdentifier())));
        JsonArchiveFile metadataFile = ArchiveUtil
                .createMetaDataFile(sa, ImmutableList.copyOf(accountDAO.getDataGroups()));

        builder.addDataFile(metadataFile);

        for (ArchiveFile resultArchiveFile : abstractResultArchiveFactory.toArchiveFiles(taskResult)) {
            builder.addDataFile(resultArchiveFile);
        }

        String archiveFilename = sk.getId() + sk.getRevision() + taskResult.getTaskUUID();

        CompletableSubject completableSubject = CompletableSubject.create();

        // convert from io.reactivex to io.reactivex.rxjava2
        return Completable.fromAction(() ->
                uploadManager.queueUpload(archiveFilename, builder.build())
                        .flatMapCompletable(uploadFile -> {
                            return uploadManager.processUploadFile(uploadFile);
                        }).await()
        );
    }
}
