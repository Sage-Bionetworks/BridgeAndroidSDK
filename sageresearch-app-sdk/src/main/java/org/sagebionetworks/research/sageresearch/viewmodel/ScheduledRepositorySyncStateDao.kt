package org.sagebionetworks.research.sageresearch.viewmodel;

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.support.annotation.VisibleForTesting
import org.joda.time.DateTime
import javax.inject.Inject

open class ScheduledRepositorySyncStateDao @Inject constructor(context: Context) {

    private val lastQueryDateKey = "lastQueryEndDate"

    /**
     * Used to store helpful data about the state of the ScheduleRepository
     */
    @VisibleForTesting
    var prefs: SharedPreferences =
            context.getSharedPreferences("ScheduleRepository", Context.MODE_PRIVATE)
        private set


    open var lastQueryEndDate: DateTime?
        get() {
            return prefs.getString(lastQueryDateKey, null)?.let {
                DateTime.parse(it)
            }
        }
        // Suppress any warnings because we need this operation to take place immediately
        @SuppressLint("ApplySharedPref")
        set(value) {
            value?.let {
                prefs.edit().putString(lastQueryDateKey, it.toString()).commit()
            } ?: run {
                prefs.edit().remove(lastQueryDateKey).commit()
            }
        }
}
