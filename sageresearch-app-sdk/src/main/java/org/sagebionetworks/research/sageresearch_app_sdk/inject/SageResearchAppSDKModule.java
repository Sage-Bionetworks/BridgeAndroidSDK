package org.sagebionetworks.research.sageresearch_app_sdk.inject;

import com.google.common.collect.ImmutableList;

import org.sagebionetworks.research.presentation.perform_task.TaskResultProcessingManager.TaskResultProcessor;
import org.sagebionetworks.research.sageresearch_app_sdk.TaskResultUploader;
import org.sagebionetworks.research.sageresearch_app_sdk.archive.AbstractResultArchiveFactory;
import org.sagebionetworks.research.sageresearch_app_sdk.archive.AbstractResultArchiveFactory.ResultArchiveFactory;
import org.sagebionetworks.research.sageresearch_app_sdk.archive.SageResearchResultArchiveFactory;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;

@Module
public abstract class SageResearchAppSDKModule {
    @Provides
    static AbstractResultArchiveFactory provideAbstractResultArchiveFactory(
            ImmutableList<ResultArchiveFactory> resultArchiveFactories) {
        return new SageResearchResultArchiveFactory(resultArchiveFactories);
    }

    /**
     * @return list of processors to receive final task results
     */
    @Binds
    @IntoSet
    abstract TaskResultProcessor provideTaskResultProcessors(TaskResultUploader taskResultUploader);
}
