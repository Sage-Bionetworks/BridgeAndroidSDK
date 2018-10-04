package org.sagebionetworks.research.sageresearch_app_sdk.inject;

import com.google.common.collect.ImmutableList;

import org.sagebionetworks.research.presentation.perform_task.TaskResultProcessingManager.TaskResultProcessor;
import org.sagebionetworks.research.sageresearch.viewmodel.ScheduledActivityTaskResultProcessor;
import org.sagebionetworks.research.sageresearch_app_sdk.TaskResultUploader;
import org.sagebionetworks.research.sageresearch_app_sdk.archive.AbstractResultArchiveFactory;
import org.sagebionetworks.research.sageresearch_app_sdk.archive.AbstractResultArchiveFactory.ResultArchiveFactory;
import org.sagebionetworks.research.sageresearch_app_sdk.archive.SageResearchResultArchiveFactory;

import java.util.Arrays;
import java.util.List;

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

    @IntoSet
    @Binds
    abstract TaskResultProcessor provideScheduledActivityTaskResultProcessor(
            ScheduledActivityTaskResultProcessor taskResultProcessor);

    /**
     * @return list of processors to receive final task results
     */
    @Provides
    static List<TaskResultProcessor> provideTaskResultProcessors(
            TaskResultUploader taskResultUploader,
            ScheduledActivityTaskResultProcessor scheduledActivityTaskResultProcessor) {
        return Arrays.asList(taskResultUploader, scheduledActivityTaskResultProcessor);
    }
}
