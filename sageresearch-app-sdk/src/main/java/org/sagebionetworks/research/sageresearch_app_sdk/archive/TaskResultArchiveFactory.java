package org.sagebionetworks.research.sageresearch_app_sdk.archive;

import androidx.annotation.NonNull;

import com.google.common.collect.ImmutableSet;

import org.sagebionetworks.bridge.data.ArchiveFile;
import org.sagebionetworks.research.domain.result.interfaces.Result;
import org.sagebionetworks.research.domain.result.interfaces.TaskResult;

public class TaskResultArchiveFactory implements AbstractResultArchiveFactory.ResultArchiveFactory {

    AbstractResultArchiveFactory abstractResultArchiveFactory;

    // cannot inject since this is a circular dependency
    public TaskResultArchiveFactory(AbstractResultArchiveFactory abstractResultArchiveFactory) {
        this.abstractResultArchiveFactory = abstractResultArchiveFactory;
    }

    @Override
    public boolean isSupported(@NonNull final Result result) {
        return result instanceof TaskResult;
    }

    @NonNull
    @Override
    public ImmutableSet<ArchiveFile> toArchiveFiles(@NonNull final Result result) {
        ImmutableSet.Builder<ArchiveFile> builder = new ImmutableSet.Builder<>();
        for (Result stepResult : ((TaskResult) result).getStepHistory()) {
            builder.add(abstractResultArchiveFactory.toArchiveFiles(stepResult).toArray(new ArchiveFile[]{}));
        }
        for (Result asynResult : ((TaskResult) result).getAsyncResults()) {
            builder.add(abstractResultArchiveFactory.toArchiveFiles(asynResult).toArray(new ArchiveFile[]{}));
        }
        return builder.build();
    }
}
