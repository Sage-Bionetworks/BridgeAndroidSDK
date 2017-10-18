package org.sagebionetworks.bridge.android.manager;

import android.support.annotation.NonNull;

import org.sagebionetworks.bridge.android.rx.RxHelper;
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

public class ScheduledActivityManager {
    private static final Logger LOG = LoggerFactory.getLogger(ScheduledActivityManager.class);

    @NonNull
    private final ForConsentedUsersApi api;
    @NonNull
    private final RxHelper rxHelper;


    public ScheduledActivityManager(@NonNull AuthenticationManager authenticationManager,
                                    @NonNull RxHelper rxHelper) {
        checkNotNull(authenticationManager);
        checkNotNull(rxHelper);

        this.api = authenticationManager.getApi();
        this.rxHelper = rxHelper;
    }


    public Single<ScheduledActivityList> getScheduledActivities(String offset, int daysAhead, int
            minimumPerSchedule) {
        return rxHelper.toBodySingle(
                api.getScheduledActivities(offset, daysAhead, minimumPerSchedule)
        ).doOnSuccess(
                scheduleActivityList -> {
                    LOG.debug("Got scheduled activity list: " + scheduleActivityList);
                });
    }

    public Single<ScheduledActivityList> getScheduledActivities(int daysAhead, int
            minimumPerSchedule) {
        return getScheduledActivities(getTimezoneOffset(), daysAhead, minimumPerSchedule);
    }

    public Completable updateScheduledActivities(@NonNull List<ScheduledActivity>
                                                         scheduledActivities) {

        checkNotNull(scheduledActivities);

        return rxHelper.toBodySingle(api.updateScheduledActivities(scheduledActivities))
                .toCompletable();
    }

    private String getTimezoneOffset() {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"),
                Locale.getDefault());
        Date currentLocalTime = calendar.getTime();
        DateFormat date = new SimpleDateFormat("Z");
        String localTime = date.format(currentLocalTime);
        String offset = localTime.substring(0, 3) + ":" + localTime.substring(3, 5);

        return offset;
    }


}
