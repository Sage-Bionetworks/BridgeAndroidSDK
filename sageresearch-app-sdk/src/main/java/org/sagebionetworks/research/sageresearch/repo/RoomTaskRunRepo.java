package org.sagebionetworks.research.sageresearch.repo;

import static com.google.common.base.Preconditions.checkNotNull;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.gson.Gson;

import org.sagebionetworks.research.domain.result.interfaces.TaskResult;
import org.sagebionetworks.research.sageresearch.dao.room.ScheduledActivityEntity;
import org.sagebionetworks.research.sageresearch.dao.room.TaskRunDAO;
import org.sagebionetworks.research.sageresearch.dao.room.TaskRunEntity;
import org.sagebionetworks.research.sageresearch.dao.room.TaskRunScheduledActivityJoin;

import java.util.UUID;

import javax.inject.Inject;

import io.reactivex.Completable;
import io.reactivex.Maybe;

public class RoomTaskRunRepo implements TaskRunRepository {
    @NonNull
    private final Gson gson;

    @NonNull
    private final TaskRunDAO taskRunDAO;

    @Inject
    public RoomTaskRunRepo(@NonNull TaskRunDAO taskRunDAO, @NonNull Gson gson) {
        this.taskRunDAO = checkNotNull(taskRunDAO);
        this.gson = checkNotNull(gson);
    }

    @NonNull
    @Override
    public Maybe<TaskResult> getTaskResult(@NonNull final UUID taskRunUUID) {
        checkNotNull(taskRunUUID);

        return taskRunDAO.getTaskRun(taskRunUUID)
                .map(tre -> Optional.fromNullable(tre.getTaskResult()))
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    @NonNull
    @Override
    public Maybe<ScheduledActivityEntity> getTaskRunScheduledActivity(@NonNull final UUID taskRunUUID) {
        checkNotNull(taskRunUUID);

        return taskRunDAO.getScheduledActivityForTaskRun(taskRunUUID);
    }

    @NonNull
    @Override
    public Completable insertTaskRunScheduledActivity(@NonNull final UUID taskRunUUID,
            @NonNull final String scheduledActivityGuid) {
        checkNotNull(taskRunUUID);
        checkNotNull(scheduledActivityGuid);

        return Completable.fromAction(() ->
                taskRunDAO.insert(new TaskRunScheduledActivityJoin(taskRunUUID, scheduledActivityGuid)));
    }

    @NonNull
    @Override
    public Completable upsertTaskRun(@NonNull final UUID taskRunUUID, @Nullable final TaskResult taskResult) {
        checkNotNull(taskRunUUID);

        TaskRunEntity tre = new TaskRunEntity(taskRunUUID);
        tre.setTaskResult(taskResult);
        return Completable.fromAction(() -> taskRunDAO.insert(tre));
    }
}
