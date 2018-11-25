package org.sagebionetworks.research.sageresearch_app_sdk.archive;

import android.support.annotation.NonNull;

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

        if (!(result instanceof TaskResult)) {
            LOGGER.error("Result " + result.getIdentifier() + " must be a TaskResult.");
            return builder.build();
        }
        TaskResult taskResult = (TaskResult)result;

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

        // Create the archives for the result list archive factories
        if (abstractResultArchiveFactory instanceof AbstractResultListArchiveFactory) {
            AbstractResultListArchiveFactory resultListArchiveFactory =
                    (AbstractResultListArchiveFactory)abstractResultArchiveFactory;
            builder.addAll(resultListArchiveFactory.toArchiveFiles(
                    new ImmutableList.Builder<Result>()
                        .addAll(stepHistory)
                        .addAll(asyncResults)
                        .build()));
        }

        return builder.build();
    }
}
