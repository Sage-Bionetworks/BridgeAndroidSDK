package org.sagebionetworks.research.sageresearch.inject;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import android.arch.persistence.room.Room;
import android.content.Context;

import org.sagebionetworks.research.sageresearch.dao.room.ResearchDatabase;
import org.sagebionetworks.research.sageresearch.dao.room.RoomScheduledActivityDao;
import org.sagebionetworks.research.sageresearch.dao.room.TaskRunDAO;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

import javax.inject.Qualifier;

import dagger.Module;
import dagger.Provides;

@Module
public class SageResearchModule {
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
    static RoomScheduledActivityDao provideScheduledActivityDao(ResearchDatabase researchDatabase) {
        return researchDatabase.activitiesDao();
    }

    @Provides
    static TaskRunDAO provideTaskRunDAO(ResearchDatabase researchDatabase) {
        return researchDatabase.taskRunsDao();
    }
}
