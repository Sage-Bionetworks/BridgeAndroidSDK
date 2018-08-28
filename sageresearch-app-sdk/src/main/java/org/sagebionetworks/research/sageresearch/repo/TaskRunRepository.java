package org.sagebionetworks.research.sageresearch.repo;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.sagebionetworks.research.domain.result.interfaces.TaskResult;
import org.sagebionetworks.research.sageresearch.dao.room.ScheduledActivityEntity;

import java.util.UUID;

import io.reactivex.Completable;
import io.reactivex.Maybe;

public interface TaskRunRepository {
    @NonNull
    Maybe<TaskResult> getTaskResult(@NonNull UUID taskRunUUID);

    @NonNull
    Maybe<ScheduledActivityEntity> getTaskRunScheduledActivity(@NonNull UUID taskRunUUID);

    @NonNull
    Completable insertTaskRunScheduledActivity(@NonNull UUID taskRunUUID, @NonNull String scheduledActivityGuid);

    @NonNull
    Completable upsertTaskRun(@NonNull UUID taskRunUUID, @Nullable TaskResult taskResult);
}
