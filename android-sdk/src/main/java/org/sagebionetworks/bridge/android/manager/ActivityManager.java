package org.sagebionetworks.bridge.android.manager;

import android.support.annotation.NonNull;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.rest.api.ForConsentedUsersApi;
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
import java.util.concurrent.atomic.AtomicReference;

import retrofit2.http.Query;
import rx.Completable;
import rx.Observable;
import rx.Single;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.android.util.retrofit.RxUtils.toBodySingle;


public class ActivityManager {
    private static final Logger LOG = LoggerFactory.getLogger(ActivityManager.class);

    @NonNull
    private final AtomicReference<ForConsentedUsersApi> apiAtomicReference;


    public ActivityManager(@NonNull AuthenticationManager authenticationManager) {
        checkNotNull(authenticationManager);

        this.apiAtomicReference = authenticationManager.getApiReference();
    }

    /**
     * @param startTime start time for the activity list
     * @param endTime end time for the activity list
     * @return schedule activity list
     */
    public Single<ScheduledActivityListV4> getActivites(DateTime startTime, DateTime endTime) {
        return toBodySingle(apiAtomicReference.get()
                .getScheduledActivitiesByDateRange(startTime, endTime)).doOnSuccess(
                scheduleActivityList -> {
                    LOG.debug("Got scheduled activity list");
                });
    }

    public Single<ScheduledActivityList> getActivities(String offset, int daysAhead, int minimumPerSchedule) {
        return toBodySingle(apiAtomicReference.get()
                .getScheduledActivities(offset, daysAhead, minimumPerSchedule)).doOnSuccess(
                scheduleActivityList -> {
                    LOG.debug("Got scheduled activity list");
                });

    }

    public Single<ScheduledActivityList> getActivities(int daysAhead, int minimumPerSchedule) {
        return getActivities(getTimezoneOffset(), daysAhead, minimumPerSchedule);
    }

    public Completable updateActivities(@NonNull List<ScheduledActivity> scheduledActivities) {

        checkNotNull(scheduledActivities);

        return toBodySingle(apiAtomicReference.get()
                .updateScheduledActivities(scheduledActivities)).toCompletable();
    }

    public Observable<Message> updateActivity(@NonNull ScheduledActivity scheduledActivity) {

        checkNotNull(scheduledActivity);

        return toBodySingle(apiAtomicReference.get()
                .updateScheduledActivities(Collections.singletonList(scheduledActivity))).toObservable();
    }

    private String getTimezoneOffset() {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"),
                Locale.getDefault());
        Date currentLocalTime = calendar.getTime();
        DateFormat date = new SimpleDateFormat("Z");
        String localTime = date.format(currentLocalTime);
        String offset = localTime.substring(0, 3) + ":"+ localTime.substring(3, 5);

        return offset;
    }
}
