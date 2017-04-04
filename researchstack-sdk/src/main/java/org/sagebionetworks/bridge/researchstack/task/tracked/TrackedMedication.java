package org.sagebionetworks.bridge.researchstack.task.tracked;

import android.text.TextUtils;

import com.google.gson.annotations.SerializedName;

/**
 * Created by TheMDP on 3/22/17.
 */

public class TrackedMedication extends TrackedDataObject {

    // Default implementation for serialization/deserialization
    public TrackedMedication() {
        super();
    }

    @SerializedName("name")
    private String name;

    @SerializedName("detail")
    private String detail;

    @SerializedName("brand")
    private String brand;

    @SerializedName("injection")
    private boolean injection;

    @Override
    public boolean usesFrequencyRange() {
        return !injection;
    }

    public String defaultIdentifierIfNull() {
        return (brand != null) ? brand : name;
    }

    public String getText() {
        StringBuilder result = new StringBuilder(name);
        if (TextUtils.isEmpty(detail)) {
            result.append(" ").append(detail);
        }
        if (TextUtils.isEmpty(brand)) {
            result.append(" ").append(brand);
        }
        return result.toString();
    }

    public String getShortText() {
        return defaultIdentifierIfNull();
    }
}
