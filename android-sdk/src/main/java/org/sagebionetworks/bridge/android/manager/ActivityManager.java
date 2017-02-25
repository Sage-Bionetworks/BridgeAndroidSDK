package org.sagebionetworks.bridge.android.manager;

import android.support.annotation.NonNull;

import org.sagebionetworks.bridge.rest.api.ForConsentedUsersApi;
import org.sagebionetworks.bridge.rest.model.ScheduledActivity;
import org.sagebionetworks.bridge.rest.model.ScheduledActivityList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

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


    public Single<ScheduledActivityList> getActivities(int daysAhead, int minimumPerSchedule) {
        return toBodySingle(api.getScheduledActivities(getTimezoneOffset(), daysAhead, minimumPerSchedule)).doOnSuccess(
                scheduleActivityList -> {
                    LOG.debug("Got scheduled activity list");
                });

    }

    public Completable updateActivities(@NonNull List<ScheduledActivity> scheduledActivities) {

        checkNotNull(scheduledActivities);

        return toBodySingle(api.updateScheduledActivities(scheduledActivities)).toCompletable();
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
