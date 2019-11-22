package org.sagebionetworks.research.sageresearch_app_sdk.archive;

import androidx.annotation.NonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.sagebionetworks.bridge.data.ArchiveFile;
import org.sagebionetworks.research.domain.result.interfaces.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

public class SageResearchResultArchiveFactory implements AbstractResultArchiveFactory {
    private static Logger LOGGER = LoggerFactory.getLogger(SageResearchResultArchiveFactory.class);

    private final ImmutableList<ResultArchiveFactory> archiveFactories;

    /**
     * @param taskResultArchiveFactory every sage result archive factory needs a root task result archive factory
     *                                 to handle the root TaskResult that is created from completing a task.
     * @param archiveFactories here we can customize the list of archive factories to control how a custom result
     *                         is archived and uploaded to bridge.
     */
    @Inject
    public SageResearchResultArchiveFactory(
            TaskResultArchiveFactory taskResultArchiveFactory,
            ImmutableList<ResultArchiveFactory> archiveFactories) {

        taskResultArchiveFactory.setAbstractResultArchiveFactory(this);
        this.archiveFactories = new ImmutableList.Builder<ResultArchiveFactory>()
                .add(taskResultArchiveFactory)
                .addAll(archiveFactories)
                .build();
    }

    @NonNull
    @Override
    public ImmutableSet<? extends ArchiveFile> toArchiveFiles(@NonNull final Result result) {
        for (ResultArchiveFactory resultArchiveFactory : archiveFactories) {
            if (resultArchiveFactory.isSupported(result)) {
                LOGGER.debug("Result archive factory found for result: {}", result);

                return resultArchiveFactory.toArchiveFiles(result);
            }
        }
        LOGGER.warn("Result archive factory not found for result: {}, skipping", result);
        return ImmutableSet.of();
    }
}
