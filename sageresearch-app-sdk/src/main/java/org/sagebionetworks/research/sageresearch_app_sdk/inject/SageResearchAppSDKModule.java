package org.sagebionetworks.research.sageresearch_app_sdk.inject;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import android.arch.persistence.room.Room;
import android.content.Context;

import com.google.common.collect.ImmutableList;

import org.sagebionetworks.research.presentation.perform_task.TaskResultProcessingManager.TaskResultProcessor;
import org.sagebionetworks.research.sageresearch.dao.room.ResearchDatabase;
import org.sagebionetworks.research.sageresearch.dao.room.ScheduledActivityEntityDao;
import org.sagebionetworks.research.sageresearch.viewmodel.ScheduledActivityTaskResultProcessor;
import org.sagebionetworks.research.sageresearch_app_sdk.TaskResultUploader;
import org.sagebionetworks.research.sageresearch_app_sdk.archive.AbstractResultArchiveFactory;
import org.sagebionetworks.research.sageresearch_app_sdk.archive.AbstractResultArchiveFactory.ResultArchiveFactory;
import org.sagebionetworks.research.sageresearch_app_sdk.archive.SageResearchResultArchiveFactory;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.util.Arrays;
import java.util.List;

import javax.inject.Qualifier;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;

@Module
public abstract class SageResearchAppSDKModule {
    private static final String RESEARCH_DB_FILENAME = "org.sagebionetworks.research.ResearchDatabase";
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

    @Qualifier
    @Documented
    @Retention(RUNTIME)
    public @interface ResearchDatabaseFilename {
    }

    @Provides
    static ResearchDatabase provideResearchDatabase(Context context) {
        return Room.databaseBuilder(context, ResearchDatabase.class, RESEARCH_DB_FILENAME)
                .build();
    }

    @Provides
    static ScheduledActivityEntityDao provideScheduledActivityDao(ResearchDatabase researchDatabase) {
        return researchDatabase.scheduleDao();
    }
}
