package org.sagebionetworks.research.sageresearch_app_sdk.inject;

import android.arch.persistence.room.Room;
import android.content.Context;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import org.sagebionetworks.bridge.android.BridgeConfig;
import org.sagebionetworks.bridge.android.di.BridgeApplicationScope;
import org.sagebionetworks.bridge.android.manager.ActivityManager;
import org.sagebionetworks.bridge.android.manager.AuthenticationManager;
import org.sagebionetworks.bridge.android.manager.ParticipantRecordManager;
import org.sagebionetworks.bridge.android.manager.SurveyManager;
import org.sagebionetworks.bridge.android.manager.UploadManager;
import org.sagebionetworks.research.presentation.perform_task.TaskResultProcessingManager.TaskResultProcessor;
import org.sagebionetworks.research.sageresearch.dao.room.ResearchDatabase;
import org.sagebionetworks.research.sageresearch.dao.room.ScheduledActivityEntityDao;
import org.sagebionetworks.research.sageresearch.viewmodel.ScheduleRepository;
import org.sagebionetworks.research.sageresearch.viewmodel.ScheduledActivityTaskResultProcessor;
import org.sagebionetworks.research.sageresearch.viewmodel.ScheduledRepositorySyncStateDao;
import org.sagebionetworks.research.sageresearch_app_sdk.TaskResultUploader;
import org.sagebionetworks.research.sageresearch_app_sdk.archive.AbstractResultArchiveFactory;
import org.sagebionetworks.research.sageresearch_app_sdk.archive.AbstractResultArchiveFactory.ResultArchiveFactory;
import org.sagebionetworks.research.sageresearch_app_sdk.archive.SageResearchResultArchiveFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ElementsIntoSet;

@Module(includes = {})
public abstract class SageResearchAppSDKModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(SageResearchAppSDKModule.class);

    private static final String RESEARCH_DB_FILENAME = "org.sagebionetworks.research.ResearchDatabase";

    @Provides
    static AbstractResultArchiveFactory provideAbstractResultArchiveFactory(
            ImmutableList<ResultArchiveFactory> resultArchiveFactories) {
        return new SageResearchResultArchiveFactory(resultArchiveFactories);
    }

    /**
     * @return list of processors to receive final task results
     */
    @Provides
    @ElementsIntoSet
    @BridgeApplicationScope
    static Set<TaskResultProcessor> provideTaskResultProcessors(
            TaskResultUploader taskResultUploader,
            ScheduledActivityTaskResultProcessor scheduledActivityTaskResultProcessor) {
        LOGGER.debug("Providing TaskResultProcessors");

        return Sets.newHashSet(taskResultUploader, scheduledActivityTaskResultProcessor);
    }

    @Provides
    @BridgeApplicationScope
    static ResearchDatabase provideResearchDatabase(Context context) {
        LOGGER.debug("Providing ResearchDatabase");
        return Room.databaseBuilder(context, ResearchDatabase.class, RESEARCH_DB_FILENAME)
                .build();
    }

    @Provides
    static ScheduledActivityEntityDao provideScheduledActivityDao(ResearchDatabase researchDatabase) {
        return researchDatabase.scheduleDao();
    }

    @Provides
    @BridgeApplicationScope
    static ScheduleRepository provideScheduleRepository(ScheduledActivityEntityDao scheduledActivityEntityDao,
            ScheduledRepositorySyncStateDao scheduledRepositorySyncStateDao, SurveyManager surveyManager,
            ActivityManager activityManager, ParticipantRecordManager participantRecordManager,
            AuthenticationManager authManager, UploadManager uploadManager, BridgeConfig bridgeConfig) {
        LOGGER.debug("Providing ScheduleRepository");
        return new ScheduleRepository(scheduledActivityEntityDao, scheduledRepositorySyncStateDao,
                surveyManager, activityManager, participantRecordManager, authManager, uploadManager, bridgeConfig);
    }
}
