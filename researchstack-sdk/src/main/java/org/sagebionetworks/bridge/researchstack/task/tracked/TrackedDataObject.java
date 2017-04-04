package org.sagebionetworks.bridge.researchstack.task.tracked;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

/**
 * Created by TheMDP on 3/21/17.
 */

public class TrackedDataObject implements Serializable {

    // Default implementation for serialization/deserialization
    public TrackedDataObject() {
        super();
    }

    @SerializedName("identifier")
    private String identifier;

    /**
     * Is this data object being tracked with follow-up questions?
     */
    @SerializedName("tracking")
    private boolean tracking;

    /**
     * Frequency of taking/doing (if applicable)
     */
    @SerializedName("frequency")
    private int frequency;

    /**
     * Whether or not the frequency range should be used. Default = false
     */
    @SerializedName("usesFrequencyRange")
    private boolean usesFrequencyRange;

    /**
     * Localized text to display as the full descriptor. Default = identifier.
     */
    @SerializedName("text")
    private String text;

    /**
     * Localized shortened text to display when used in a sentence. Default = identifier.
     */
    @SerializedName("shortText")
    private String shortText;

    public boolean isTracking() {
        return tracking;
    }

    public void setTracking(boolean tracking) {
        this.tracking = tracking;
    }

    public int getFrequency() {
        return frequency;
    }

    public void setFrequency(int frequency) {
        this.frequency = frequency;
    }

    public boolean usesFrequencyRange() {
        return usesFrequencyRange;
    }

    public void setUsesFrequencyRange(boolean usesFrequencyRange) {
        this.usesFrequencyRange = usesFrequencyRange;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getShortText() {
        return shortText;
    }

    public void setShortText(String shortText) {
        this.shortText = shortText;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }
}
