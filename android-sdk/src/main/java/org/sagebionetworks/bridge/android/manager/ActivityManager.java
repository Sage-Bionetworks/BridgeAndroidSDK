package org.sagebionetworks.bridge.android.manager;

import android.support.annotation.NonNull;

import org.sagebionetworks.bridge.rest.api.ForConsentedUsersApi;
import org.sagebionetworks.bridge.rest.model.ScheduledActivity;
import org.sagebionetworks.bridge.rest.model.ScheduledActivityList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import rx.Completable;
import rx.Single;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.android.util.retrofit.RxUtils.toBodySingle;


public class ActivityManager {
    private static final Logger LOG = LoggerFactory.getLogger(ActivityManager.class);

    @NonNull
    private final ForConsentedUsersApi api;


    public ActivityManager(@NonNull AuthenticationManager authenticationManager) {
        checkNotNull(authenticationManager);

        this.api = authenticationManager.getApi();
    }


    public Single<ScheduledActivityList> getActivities(String offset, int daysAhead, int minimumPerSchedule) {
        return toBodySingle(api.getScheduledActivities(offset, daysAhead, minimumPerSchedule)).doOnSuccess(
                scheduleActivityList -> {
                    LOG.debug("Got scheduled activity list");
                });

    }

    public Completable updateActivities(@NonNull List<ScheduledActivity> scheduledActivities) {

        checkNotNull(scheduledActivities);

        return toBodySingle(api.updateScheduledActivities(scheduledActivities)).toCompletable();
    }


}
