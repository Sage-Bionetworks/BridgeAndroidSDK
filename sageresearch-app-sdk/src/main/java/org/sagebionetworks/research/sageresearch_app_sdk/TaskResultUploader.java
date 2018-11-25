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

import static com.google.common.base.Preconditions.checkNotNull;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.common.collect.ImmutableList;

import org.sagebionetworks.bridge.android.BridgeConfig;
import org.sagebionetworks.bridge.android.manager.AuthenticationManager;
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
import org.sagebionetworks.bridge.rest.model.UserSessionInfo;
import org.sagebionetworks.research.domain.result.interfaces.TaskResult;
import org.sagebionetworks.research.presentation.perform_task.TaskResultProcessingManager.TaskResultProcessor;
import org.sagebionetworks.research.sageresearch.dao.room.EntityTypeConverters;
import org.sagebionetworks.research.sageresearch.dao.room.ScheduleRepository;
import org.sagebionetworks.research.sageresearch.dao.room.ScheduledActivityEntity;
import org.sagebionetworks.research.sageresearch_app_sdk.archive.AbstractResultArchiveFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import io.reactivex.Completable;
import io.reactivex.CompletableSource;
import io.reactivex.Single;
import io.reactivex.functions.Function;
import io.reactivex.subjects.CompletableSubject;

public class TaskResultUploader implements TaskResultProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskResultUploader.class);

    private final BridgeConfig bridgeConfig;

    private AbstractResultArchiveFactory abstractResultArchiveFactory;

    private UploadManager uploadManager;

    private AuthenticationManager authManager;

    private ScheduleRepository scheduleRepo;

    @Inject
    public TaskResultUploader(@NonNull final BridgeConfig bridgeConfig,
            @NonNull final AbstractResultArchiveFactory abstractResultArchiveFactory,
            @NonNull final UploadManager uploadManager,
            @NonNull final AuthenticationManager authManager,
            @NonNull final ScheduleRepository scheduleRepo) {
        this.bridgeConfig = checkNotNull(bridgeConfig);
        this.abstractResultArchiveFactory = checkNotNull(abstractResultArchiveFactory);
        this.uploadManager = checkNotNull(uploadManager);
        this.scheduleRepo = checkNotNull(scheduleRepo);
        this.authManager = checkNotNull(authManager);
    }

    @Override
    public Completable processTaskResult(final TaskResult taskResult) {
        LOGGER.info("Uploading task result: {}", taskResult);

        SchemaKey sk = bridgeConfig.getTaskToSchemaMap().get(taskResult.getIdentifier());

        if (sk == null) {
            LOGGER.warn("No schema found for task " + taskResult.getIdentifier() +  ". Revision 1 will be used.");
            sk = new SchemaKey(taskResult.getIdentifier(), 1);
        }

        Archive.Builder builder = Archive.Builder.forActivity(sk.getId(), sk.getRevision());
        String appVersionString = String.format(Locale.ENGLISH, "version %s, build %d",
                bridgeConfig.getAppVersionName(),
                bridgeConfig.getAppVersion());

        builder.withAppVersionName(appVersionString)
                .withPhoneInfo(bridgeConfig.getDeviceName());

        for (ArchiveFile resultArchiveFile : abstractResultArchiveFactory.toArchiveFiles(taskResult)) {
            builder.addDataFile(resultArchiveFile);
        }

        String archiveFilename = sk.getId() + sk.getRevision() + taskResult.getTaskUUID();

        Completable uploadToS3Completable = Completable.fromAction(() ->
                uploadManager.queueUpload(archiveFilename, builder.build())
                        .flatMapCompletable(uploadFile ->
                                uploadManager.processUploadFile(uploadFile)));

        return metadataCompletable(builder, taskResult)
                .concatWith(uploadToS3Completable);
    }

    /**
     * Builds a metadata file from the TaskResult info.
     * @param builder to add the metadata file to.
     * @param taskResult for the task, used to find the associated schedule.
     * @return a completable that, when invoked, will add the metadata file to the builder.
     */
    @NonNull
    protected Completable metadataCompletable(@NonNull Archive.Builder builder, @NonNull TaskResult taskResult) {
        return scheduleRepo.findSchedule(taskResult.getTaskUUID())
                .map(scheduledActivityEntity -> {
                    ScheduledActivity sa = new ScheduledActivity();
                    if (scheduledActivityEntity != null) {
                        sa = EntityTypeConverters.bridgeMetaDataSchedule(scheduledActivityEntity);
                    }
                    JsonArchiveFile metadataFile = ArchiveUtil.createMetaDataFile(
                            sa, getUserDataGroups(), getUserExternalId());
                    builder.addDataFile(metadataFile);
                    return Completable.complete();
                })
                .onErrorReturn(throwable -> {
                    ScheduledActivity sa = new ScheduledActivity();
                    sa.activity(new Activity()
                            .task(new TaskReference().identifier(taskResult.getIdentifier())));
                    JsonArchiveFile metadataFile = ArchiveUtil.createMetaDataFile(
                            sa, getUserDataGroups(), getUserExternalId());
                    builder.addDataFile(metadataFile);
                    return Completable.complete();
                })
                .flatMapCompletable(completable -> Completable.complete());
    }

    /**
     * @return the current status of the user's data groups, empty list of user is not signed in
     */
    @NonNull
    ImmutableList<String> getUserDataGroups() {
        UserSessionInfo sessionInfo = authManager.getUserSessionInfo();
        if (sessionInfo == null || sessionInfo.getDataGroups() == null) {
            return ImmutableList.of();
        }
        return ImmutableList.copyOf(sessionInfo.getDataGroups());
    }

    /**
     * @return the current status of the user's data groups, empty list of user is not signed in
     */
    @Nullable
    String getUserExternalId() {
        UserSessionInfo sessionInfo = authManager.getUserSessionInfo();
        if (sessionInfo == null) {
            return null;
        }
        return sessionInfo.getExternalId();
    }
}
