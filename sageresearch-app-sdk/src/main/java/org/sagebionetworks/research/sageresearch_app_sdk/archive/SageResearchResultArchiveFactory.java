package org.sagebionetworks.research.sageresearch_app_sdk.archive;

import android.support.annotation.NonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.sagebionetworks.bridge.data.ArchiveFile;
import org.sagebionetworks.research.domain.result.interfaces.Result;
import org.sagebionetworks.research.sageresearch_app_sdk.archive.AbstractResultArchiveFactory.ResultArchiveFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

public class SageResearchResultArchiveFactory implements AbstractResultListArchiveFactory {
    private static Logger LOGGER = LoggerFactory.getLogger(SageResearchResultArchiveFactory.class);

    private final ImmutableList<ResultArchiveFactory> archiveFactories;

    @Inject
    public SageResearchResultArchiveFactory(ImmutableList<ResultArchiveFactory> archiveFactories) {
        this.archiveFactories = new ImmutableList.Builder<ResultArchiveFactory>()
                .add(new TaskResultArchiveFactory(this))
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

    @NonNull
    @Override
    public ImmutableSet<? extends ArchiveFile> toArchiveFiles(@NonNull final ImmutableList<Result> resultList) {
        ImmutableSet.Builder<ArchiveFile> builder = new ImmutableSet.Builder<>();
        for (ResultArchiveFactory archiveFactory: archiveFactories) {
            // Check both interface options for archives
            if (archiveFactory instanceof AbstractResultListArchiveFactory) {
                AbstractResultListArchiveFactory factory =
                        (AbstractResultListArchiveFactory)archiveFactory;
                builder.addAll(factory.toArchiveFiles(resultList));
            } else if (archiveFactory instanceof AbstractResultListArchiveFactory.ResultListArchiveFactory) {
                AbstractResultListArchiveFactory.ResultListArchiveFactory factory =
                        (AbstractResultListArchiveFactory.ResultListArchiveFactory)archiveFactory;
                builder.addAll(factory.toArchiveFiles(resultList));
            }
        }
        return builder.build();
    }
}
