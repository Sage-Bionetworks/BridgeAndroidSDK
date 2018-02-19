package org.sagebionetworks.bridge.android.manager;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.android.manager.dao.ActivityListDAO;
import org.sagebionetworks.bridge.rest.model.Message;
import org.sagebionetworks.bridge.rest.model.ScheduledActivity;
import org.sagebionetworks.bridge.rest.model.ScheduledActivityList;
import org.sagebionetworks.bridge.rest.model.ScheduledActivityListV4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import rx.Completable;
import rx.Observable;
import rx.Single;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.android.util.retrofit.RxUtils.toBodySingle;


public class ActivityManager {
    private static final Logger LOG = LoggerFactory.getLogger(ActivityManager.class);

    @NonNull
    private final AtomicReference<AuthenticationManager.AuthStateHolder>
            authStateHolderAtomicReference;

    private static final String ACTIVITY_LIST_SHARED_PREFS_KEY = "ActivityListDAO";
    private final ActivityListDAO activityListDAO;

    public ActivityManager(@NonNull AuthenticationManager authenticationManager,
                           @NonNull Context appContext) {

        checkNotNull(authenticationManager);
        checkNotNull(appContext);

        this.authStateHolderAtomicReference = authenticationManager.getAuthStateReference();
        activityListDAO = new ActivityListDAO(appContext, ACTIVITY_LIST_SHARED_PREFS_KEY);
    }

    /**
     * @param startTime start time for the activity list
     * @param endTime end time for the activity list
     * @return schedule activity list
     */
    public Single<ScheduledActivityListV4> getActivities(DateTime startTime, DateTime endTime) {
        return toBodySingle(authStateHolderAtomicReference.get().forConsentedUsersApi
                .getScheduledActivitiesByDateRange(startTime, endTime)).doOnSuccess(
                scheduleActivityList -> {
                    LOG.debug("Got scheduled activity list");
                    activityListDAO.updateActivityList(scheduleActivityList);
                });
    }

    public Completable updateActivities(@NonNull List<ScheduledActivity> scheduledActivities) {

        checkNotNull(scheduledActivities);

        activityListDAO.updateActivityList(scheduledActivities);
        
        return toBodySingle(authStateHolderAtomicReference.get().forConsentedUsersApi
                .updateScheduledActivities(scheduledActivities)).toCompletable();
    }

    public Observable<Message> updateActivity(@NonNull ScheduledActivity scheduledActivity) {

        checkNotNull(scheduledActivity);

        activityListDAO.updateActivityList(Collections.singletonList(scheduledActivity));

        return toBodySingle(authStateHolderAtomicReference.get().forConsentedUsersApi
                .updateScheduledActivities(Collections.singletonList(scheduledActivity)))
                .toObservable();
    }

    public @Nullable ScheduledActivity getLocalActivity(String guid) {
        return activityListDAO.getActivity(guid);
    }

    public void clearDAO() {
        activityListDAO.clear();
    }

}
