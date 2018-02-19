/*
 *    Copyright 2018 Sage Bionetworks
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package org.sagebionetworks.bridge.android.util;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimaps;

import org.sagebionetworks.bridge.rest.model.ScheduledActivity;
import org.sagebionetworks.bridge.rest.model.TaskReference;

import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;


/**
 * Created by liujoshua on 2/7/2018.
 */

@SuppressWarnings("Convert2Lambda")
public class ScheduledActivityUtil {

    /**
     * Function that returns the schedule plan guid for a scheduled activity.
     */
    public static final Function<ScheduledActivity, String> TO_SCHEDULE_PLAN_GUID =
            new Function<ScheduledActivity, String>() {
                @NonNull
                @Override
                public String apply(@NonNull ScheduledActivity scheduledActivity) {
                    checkNotNull(scheduledActivity);

                    return scheduledActivity.getSchedulePlanGuid();
                }
            };

    /**
     * Function that returns the task identifier for a scheduled activity, or null if the
     * activity is a survey.
     */
    public static final Function<ScheduledActivity, String> TO_TASK_IDENTIFIER =
            new Function<ScheduledActivity, String>() {
                @Nullable
                @Override
                public String apply(@NonNull ScheduledActivity scheduledActivity) {
                    checkNotNull(scheduledActivity);
                    Verify.verify(scheduledActivity.getActivity() != null);

                    TaskReference task = scheduledActivity.getActivity().getTask();
                    if (task == null) {
                        return null;
                    }

                    String taskId = task.getIdentifier();
                    Verify.verify(taskId != null);
                    return taskId;
                }
            };

    /**
     * Groups scheduled activities by schedule plan guid, maintaining order of scheduled activities.
     * @param activities scheduled activities
     * @return scheduled activities grouped by schedule plan guid
     */
    @NonNull
    public static ImmutableMultimap<String, ScheduledActivity> groupBySchedulePlan(
            @NonNull List<ScheduledActivity> activities) {
        checkNotNull(activities);

        return ImmutableMultimap.copyOf(
                Multimaps.index(activities.iterator(), TO_SCHEDULE_PLAN_GUID)
        );
    }

    // TODO: tests for iOS equivalents
//    @VisibleForTesting
//    static final Predicate<ScheduledActivity> HAS_FINISHED_ON =
//            new Predicate<ScheduledActivity>() {
//                @Override
//                public boolean apply(@NonNull ScheduledActivity scheduledActivity) {
//                    checkNotNull(scheduledActivity);
//
//                    return scheduledActivity.getFinishedOn() != null;
//                }
//            };
//
//    public static final Predicate<ScheduledActivity> IS_STARTED =
//            new Predicate<ScheduledActivity>() {
//                @Override
//                public boolean apply(@NonNull ScheduledActivity scheduledActivity) {
//                    checkNotNull(scheduledActivity);
//
//                    return scheduledActivity.getStartedOn() != null;
//                }
//            };
//
//    public static final Predicate<ScheduledActivity> IS_FINISHED =
//            new Predicate<ScheduledActivity>() {
//                @Override
//                public boolean apply(@NonNull ScheduledActivity scheduledActivity) {
//                    return and(IS_STARTED, HAS_FINISHED_ON).apply(scheduledActivity);
//                }
//            };
//
//    public static final Predicate<ScheduledActivity> IS_DELETED =
//            new Predicate<ScheduledActivity>() {
//                @Override
//                public boolean apply(@NonNull ScheduledActivity scheduledActivity) {
//                    return and(not(IS_STARTED), HAS_FINISHED_ON).apply(scheduledActivity);
//                }
//            };
//
//
//    public static final Predicate<ScheduledActivity> IS_PERSISTENT =
//            new Predicate<ScheduledActivity>() {
//                @Override
//                public boolean apply(@NonNull ScheduledActivity scheduledActivity) {
//                    checkNotNull(scheduledActivity);
//
//                    return scheduledActivity.getPersistent();
//                }
//            };
//
//
//    @NonNull
//    public static Predicate<ScheduledActivity> scheduledBefore(@NonNull LocalDate date) {
//        checkNotNull(date);
//
//        return new Predicate<ScheduledActivity>() {
//            @Override
//            public boolean apply(@NonNull ScheduledActivity scheduledActivity) {
//                checkNotNull(scheduledActivity);
//
//                DateTime scheduledOn = scheduledActivity.getScheduledOn();
//                verify(scheduledOn != null);
//
//                return scheduledOn.isBefore(date.toDateTimeAtStartOfDay());
//            }
//        };
//    }
//
//    @NonNull
//    public static Predicate<ScheduledActivity> scheduledOn(@NonNull LocalDate date) {
//        checkNotNull(date);
//
//        return new Predicate<ScheduledActivity>() {
//            @Override
//            public boolean apply(@NonNull ScheduledActivity scheduledActivity) {
//                checkNotNull(scheduledActivity);
//
//                DateTime scheduledOn = scheduledActivity.getScheduledOn();
//                verify(scheduledOn != null);
//
//                return intervalForDate(date).contains(scheduledOn);
//            }
//        };
//    }
//
//    @NonNull
//    public static Predicate<ScheduledActivity> expiresOn(@NonNull LocalDate date) {
//        checkNotNull(date);
//
//        return new Predicate<ScheduledActivity>() {
//            @Override
//            public boolean apply(@NonNull ScheduledActivity scheduledActivity) {
//                checkNotNull(scheduledActivity);
//
//                DateTime expiresOn = scheduledActivity.getExpiresOn();
//                if (expiresOn == null) {
//                    return false;
//                }
//
//                return intervalForDate(date).contains(expiresOn);
//            }
//        };
//    }
//
//    @NonNull
//    public static Predicate<ScheduledActivity> expiresAfter(@NonNull LocalDate date) {
//        checkNotNull(date);
//
//        return new Predicate<ScheduledActivity>() {
//            @Override
//            public boolean apply(@NonNull ScheduledActivity scheduledActivity) {
//                checkNotNull(scheduledActivity);
//
//                DateTime expiresOn = scheduledActivity.getExpiresOn();
//                if (expiresOn == null) {
//                    return true;
//                }
//
//                return intervalForDate(date).isBefore(expiresOn);
//            }
//        };
//    }
//
//    private static Interval intervalForDate(@NonNull LocalDate date) {
//        return new Interval(
//                date.toDateTimeAtStartOfDay(),
//                date.plusDays(1).toDateTimeAtStartOfDay()
//        );
//    }
//
//
//    @NonNull
//    public static Predicate<ScheduledActivity> finishedOn(@NonNull LocalDate date) {
//        checkNotNull(date);
//
//        return new Predicate<ScheduledActivity>() {
//            @Override
//            public boolean apply(@NonNull ScheduledActivity scheduledActivity) {
//
//                return IS_FINISHED.apply(scheduledActivity)
//                        && new Interval(
//                        date.toDateTimeAtStartOfDay(),
//                        date.plusDays(1).toDateTimeAtStartOfDay()
//                ).contains(scheduledActivity.getFinishedOn());
//            }
//        };
//    }
//
//    @NonNull
//    public static Predicate<ScheduledActivity> expiredUnfinishedOnOrFinishedOn(@NonNull LocalDate
//                                                                                           date) {
//        // iOS also checks activity was scheduled this date or before, but this should suffice
//        return or(
//                and(not(IS_FINISHED), expiresOn(date)), // expired finished
//                finishedOn(date)
//        );
//    }
//
//    @NonNull
//    public static Predicate<ScheduledActivity> scheduledOnOrBeforeOrFinishedOn(@NonNull LocalDate
//                                                                                           date) {
//        return and(
//                scheduledBefore(date.plusDays(1)),
//                or(
//                        not(IS_FINISHED),
//                        finishedOn(date)
//                ),
//                expiresAfter(date.minusDays(1))
//        );
//    }
//
//    @NonNull
//    public static Predicate<ScheduledActivity> isAvailableOn(@NonNull LocalDate date) {
//        return and(
//                scheduledBefore(date.plusDays(1)),
//                not(IS_FINISHED),
//                or(expiresAfter(date.minusDays(1)))
//        );
//    }
//
//
//    @NonNull
//    public static Optional<ScheduledActivity> findActivityByTaskId(
//            @NonNull List<ScheduledActivity> activities, @NonNull String taskId) {
//        checkNotNull(activities);
//        checkNotNull(taskId);
//
//        return Iterables.tryFind(activities, activity ->
//                taskId.equals(TO_TASK_IDENTIFIER.apply(activity)));
//    }
//
//    private ScheduledActivityUtil() {
//        // prevent instantiation of static util class
//    }
}
