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

package org.sagebionetworks.bridge.android.manager.dao;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.gson.reflect.TypeToken;

import org.sagebionetworks.bridge.rest.model.ScheduledActivity;
import org.sagebionetworks.bridge.rest.model.ScheduledActivityListV4;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by TheMDP on 12/29/17.
 */

public class ActivityListDAO extends SharedPreferencesJsonDAO {

    private static final String LOG_TAG = ActivityListDAO.class.getCanonicalName();

    private static final TypeToken<List<ScheduledActivity>> ACTIVITY_LIST_TYPE =
            new TypeToken<List<ScheduledActivity>>(){};

    private static final String ACTIVITIES_KEY = "ACTIVITIES";

    public ActivityListDAO(@NonNull Context applicationContext, @NonNull String prefsKey) {
        super(applicationContext, prefsKey);
    }

    public void clear() {
        sharedPreferences.edit().clear().commit();
    }

    /**
     * Call to remove the activity from the cache
     * @param activity to remove
     */
    public void removeActivity(@Nullable ScheduledActivity activity) {
        if (activity == null) {
            return; // nothing to remove
        }

        List<ScheduledActivity> activityList = getActivityList();
        // Find the corresponding activity
        ScheduledActivity activityToUpdate = findActivity(activityList, activity.getGuid());
        // If the activity already exists, remove it
        if (activityToUpdate != null) {
            activityList.remove(activityToUpdate);
        }
        // Cache the new activity list
        if (activityList != null) {
            cacheActivityList(activityList);
        }
    }

    /**
     * Call to update a list of activities in the DAO
     * @param activitiesToUpdate in the DAO
     */
    public void updateActivityList(@Nullable ScheduledActivityListV4 activitiesToUpdate) {
        if (activitiesToUpdate == null) {
            return; // no need to update any activities
        }
        updateActivityList(activitiesToUpdate.getItems());
    }

    /**
     * Should be called when an activity is complete or when client data changes
     * @param activityToUpdate in the DAO
     */
    public void updateActivity(@Nullable ScheduledActivity activityToUpdate) {
        if (activityToUpdate == null) {
            return; // no need to update any activities
        }
        updateActivityList(Collections.singletonList(activityToUpdate));
    }

    /**
     * Call to update a list of activities in the DAO
     * @param activitiesToUpdate in the DAO
     */
    public void updateActivityList(@Nullable List<ScheduledActivity> activitiesToUpdate) {
        if (activitiesToUpdate == null || activitiesToUpdate.isEmpty()) {
            return; // no activities to update
        }

        List<ScheduledActivity> activityList = getActivityList();
        if (activityList == null) {
            activityList = new ArrayList<>();
        }

        for (ScheduledActivity activity : activitiesToUpdate) {
            // Find the corresponding activity
            ScheduledActivity activityToUpdate = findActivity(activityList, activity.getGuid());
            // If the activity already exists, replace it
            if (activityToUpdate != null) {
                activityList.remove(activityToUpdate);
            }
            activityList.add(activity);
        }

        // Cache the new activity list
        cacheActivityList(activityList);
    }

    /**
     * @param onceActivityList the map of activities, they must all be activities scheduled once
     *                         with a null expiredOn date
     */
    protected void cacheActivityList(@NonNull List<ScheduledActivity> onceActivityList) {
        setValue(ACTIVITIES_KEY, onceActivityList, ACTIVITY_LIST_TYPE);
    }

    /**
     * @return the list of activities scheduled once
     */
    public @Nullable List<ScheduledActivity> getActivityList() {
        // TODO: mdephillips 1/13/18
        // this may fill up with many activities and parsing the json may take awhile
        // should this be asynchronous?
        return getValue(ACTIVITIES_KEY, ACTIVITY_LIST_TYPE);
    }

    /**
     * @param guid of the activity to get
     * @return the activity in the DAO with this guid
     */
    public @Nullable ScheduledActivity getActivity(String guid) {
        return findActivity(getActivityList(), guid);
    }

    /**
     * @return true if the scheduled once activities are already cached, false otherwise
     */
    public boolean hasActivityList() {
        return sharedPreferences.contains(ACTIVITIES_KEY);
    }

    /**
     * @param activityList to search
     * @param guid of the activity to try and find
     * @return an activity with that guid, or null if none were found
     */
    protected ScheduledActivity findActivity(@Nullable List<ScheduledActivity> activityList,
                                             @Nullable String guid) {

        if (guid == null || activityList == null) {
            return null;
        }
        for(ScheduledActivity activity : activityList) {
            if (guid.equals(activity.getGuid())) {
                return activity;
            }
        }
        return null;
    }
}
