package org.sagebionetworks.research.sageresearch_app_sdk.inject;

import static com.google.common.base.Preconditions.checkArgument;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import android.arch.lifecycle.ViewModel;
import android.arch.lifecycle.ViewModelProvider;
import android.arch.persistence.room.Room;
import android.content.Context;
import android.support.annotation.NonNull;

import com.google.common.collect.ImmutableList;

import org.sagebionetworks.research.presentation.perform_task.TaskResultProcessingManager.TaskResultProcessor;
import org.sagebionetworks.research.sageresearch.dao.room.ResearchDatabase;
import org.sagebionetworks.research.sageresearch.dao.room.ScheduledActivityEntityDao;
import org.sagebionetworks.research.sageresearch.viewmodel.ScheduleRepository;
import org.sagebionetworks.research.sageresearch.viewmodel.ScheduleViewModel;
import org.sagebionetworks.research.sageresearch.viewmodel.ScheduledActivityTaskResultProcessor;
import org.sagebionetworks.research.sageresearch_app_sdk.TaskResultUploader;
import org.sagebionetworks.research.sageresearch_app_sdk.archive.AbstractResultArchiveFactory;
import org.sagebionetworks.research.sageresearch_app_sdk.archive.AbstractResultArchiveFactory.ResultArchiveFactory;
import org.sagebionetworks.research.sageresearch_app_sdk.archive.SageResearchResultArchiveFactory;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.util.Arrays;
import java.util.List;

import javax.inject.Named;
import javax.inject.Qualifier;

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

    @Qualifier
    @Documented
    @Retention(RUNTIME)
    public @interface ResearchDatabaseFilename {
    }

    @Provides
    static ResearchDatabase provideResearchDatabase(Context context,
            @ResearchDatabaseFilename String databaseFilename) {
        return Room.databaseBuilder(context, ResearchDatabase.class, databaseFilename)
                .build();
    }

    @Provides
    static ScheduledActivityEntityDao provideScheduledActivityDao(ResearchDatabase researchDatabase) {
        return researchDatabase.scheduleDao();
    }
}
