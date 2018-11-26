package org.sagebionetworks.research.sageresearch_app_sdk.archive;

import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.sagebionetworks.bridge.data.ArchiveFile;
import org.sagebionetworks.research.domain.result.interfaces.Result;
import org.sagebionetworks.research.domain.result.interfaces.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class TaskResultArchiveFactory implements AbstractResultArchiveFactory.ResultArchiveFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskResultArchiveFactory.class);

    @Nullable AbstractResultArchiveFactory abstractResultArchiveFactory;
    void setAbstractResultArchiveFactory(AbstractResultArchiveFactory abstractResultArchiveFactory) {
        this.abstractResultArchiveFactory = abstractResultArchiveFactory;
    }

    public TaskResultArchiveFactory() {
        // default constructor
    }

    @Override
    public boolean isSupported(@NonNull final Result result) {
        return result instanceof TaskResult;
    }

    @NonNull
    @Override
    public ImmutableSet<ArchiveFile> toArchiveFiles(@NonNull final Result result) {
        ImmutableSet.Builder<ArchiveFile> builder = new ImmutableSet.Builder<>();

        if (!(result instanceof TaskResult)) {
            LOGGER.error("Result " + result.getIdentifier() + " must be a TaskResult.");
            return builder.build();
        }
        TaskResult taskResult = (TaskResult)result;

        addArchives(builder, taskResult);
        return builder.build();
    }

    /**
     * Can be overridden by sub-classes to add custom archives.
     * @param builder to add the archives to.
     * @param taskResult contains results that should be converted into archives.
     */
    @CallSuper
    protected void addArchives(
            @NonNull ImmutableSet.Builder<ArchiveFile> builder,
            @NonNull final TaskResult taskResult) {

        if (abstractResultArchiveFactory == null) {
            LOGGER.error("abstractResultArchiveFactory is null.  "
                    + "The ArchiveFactory that owns this class must set abstractResultArchiveFactory");
            return;
        }

        // Create archives for the step history
        List<Result> stepHistory = taskResult.getStepHistory();
        for (Result stepResult: stepHistory) {
            builder.add(abstractResultArchiveFactory.toArchiveFiles(stepResult).toArray(new ArchiveFile[]{}));
        }

        // Create the archives for the async results
        List<Result> asyncResults = taskResult.getAsyncResults();
        for (Result asynResult : asyncResults) {
            builder.add(abstractResultArchiveFactory.toArchiveFiles(asynResult).toArray(new ArchiveFile[]{}));
        }
    }
}
