package org.sagebionetworks.bridge.researchstack.task.tracked;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import org.researchstack.backbone.step.Step;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by TheMDP on 3/21/17.
 *
 * A TrackedStepHolder interface is a special Step that is a part of a TrackedDataObjectCollection
 * A tracked step can be any type that is included in the usual Steps, but also has a trackingType
 */

public class TrackedStepHolder implements Serializable {

    String TRACKING_TYPE_GSON = "trackingType";

    /**
     * The type of tracking that the root step should use
     */
    @SerializedName("trackingType")
    private Type trackingType;

    @SerializedName("trackEach")
    private boolean trackEach;

    @SerializedName("textFormat")
    private String textFormat;

    /**
     * The root step is the actual step that is displayed in the step layout,
     * The TrackedStepHolder is a separate data model to keep track of information on top of those steps
     */
    private Step rootStep;

    /**
     * @param rootStep The root step is the actual step that is displayed in the step layout
     * @param trackingType the tracking type for the step
     */
    public TrackedStepHolder(Step rootStep, Type trackingType) {
        this.rootStep = rootStep;
        this.trackingType = trackingType;
    }

    public Type getTrackingType() {
        return trackingType;
    }

    public void setTrackingType(Type trackingType) {
        this.trackingType = trackingType;
    }

    public Step getRootStep() {
        return rootStep;
    }

    public void setRootStep(Step rootStep) {
        this.rootStep = rootStep;
    }

    public boolean trackEach() {
        return trackEach;
    }

    public void setTrackEach(boolean trackEach) {
        this.trackEach = trackEach;
    }

    public String getTextFormat() {
        return textFormat;
    }

    public void setTextFormat(String textFormat) {
        this.textFormat = textFormat;
    }

    enum Type {
        @SerializedName("introduction")
        INTRODUCTION,
        @SerializedName("changed")
        CHANGED,
        @SerializedName("completion")
        COMPLETION,
        @SerializedName("activity")
        ACTIVITY,
        @SerializedName("selection")
        SELECTION,
        @SerializedName("frequency")
        FREQUENCY;

        public static Type fromIdentifier(String identifier) {
            Gson gson = new Gson();
            return gson.fromJson(identifier, Type.class);
        }

        public String getName() {
            Gson gson = new Gson();
            return gson.toJson(this);
        }
    }

    enum TypeIncludes {

        STAND_ALONE_SURVEY(Arrays.asList(
                Type.INTRODUCTION,
                Type.SELECTION,
                Type.FREQUENCY,
                Type.COMPLETION)),

        ACTIVITY_ONLY(Arrays.asList(
                Type.ACTIVITY)),

        SURVEY_AND_ACTIVITY(Arrays.asList(
                Type.INTRODUCTION,
                Type.SELECTION,
                Type.FREQUENCY,
                Type.ACTIVITY)),

        CHANGED_AND_ACTIVITY(Arrays.asList(
                Type.CHANGED,
                Type.SELECTION,
                Type.FREQUENCY,
                Type.ACTIVITY)),

        CHANGED_ONLY(Arrays.asList(
                Type.CHANGED)),

        NONE(new ArrayList<>());

        private List<Type> typeList;
        private Type nextStepIfNoChange;

        TypeIncludes(List<Type> typeList) {
            if (typeList.contains(Type.CHANGED) && !typeList.contains(Type.ACTIVITY)) {
                typeList = Arrays.asList(
                        Type.CHANGED,
                        Type.SELECTION,
                        Type.FREQUENCY,
                        Type.ACTIVITY);
                nextStepIfNoChange = Type.COMPLETION;
            }
            else {
                this.typeList = typeList;
                nextStepIfNoChange = Type.ACTIVITY;
            }
        }

        public boolean includeSurvey() {
            return typeList.contains(Type.INTRODUCTION) || typeList.contains(Type.CHANGED);
        }

        public boolean shouldInclude(Type type) {
            return typeList.contains(type);
        }

        public List<Type> getTypeList() {
            return typeList;
        }

        public Type getNextStepIfNoChange() {
            return nextStepIfNoChange;
        }
    }
}
