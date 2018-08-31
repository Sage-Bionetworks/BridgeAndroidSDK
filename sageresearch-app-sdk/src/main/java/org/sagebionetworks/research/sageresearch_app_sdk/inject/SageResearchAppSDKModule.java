package org.sagebionetworks.research.sageresearch_app_sdk.inject;

import com.google.common.collect.ImmutableList;

import org.sagebionetworks.research.presentation.perform_task.TaskResultProcessingManager.TaskResultProcessor;
import org.sagebionetworks.research.sageresearch_app_sdk.TaskResultUploader;
import org.sagebionetworks.research.sageresearch_app_sdk.archive.AbstractResultArchiveFactory;
import org.sagebionetworks.research.sageresearch_app_sdk.archive.AbstractResultArchiveFactory.ResultArchiveFactory;
import org.sagebionetworks.research.sageresearch_app_sdk.archive.SageResearchResultArchiveFactory;

import java.util.Collections;
import java.util.List;

import dagger.Module;
import dagger.Provides;

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
    @Provides
    static List<TaskResultProcessor> provideTaskResultProcessors(TaskResultUploader taskResultUploader) {
        return Collections.<TaskResultProcessor>singletonList(taskResultUploader);
    }
}
