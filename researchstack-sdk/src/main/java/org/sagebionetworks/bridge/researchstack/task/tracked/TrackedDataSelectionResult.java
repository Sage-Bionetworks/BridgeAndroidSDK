package org.sagebionetworks.bridge.researchstack.task.tracked;

import com.google.gson.annotations.SerializedName;

import org.sagebionetworks.researchstack.backbone.answerformat.AnswerFormat;
import org.sagebionetworks.researchstack.backbone.result.Result;
import org.sagebionetworks.researchstack.backbone.ui.ViewTaskActivity;
import org.sagebionetworks.researchstack.backbone.ui.step.layout.StepLayout;
import org.sagebionetworks.bridge.researchstack.survey.SurveyAnswer;

import java.util.List;

/**
 * Created by TheMDP on 3/23/17.
 */

public class TrackedDataSelectionResult extends Result {

    @SerializedName("questionType")
    private String questionType = AnswerFormat.Type.MultipleChoice.name();

    @SerializedName("items")
    private List<? extends TrackedDataObject> selectedItems;

    /**
     * Returns an initialized result using the specified identifier.
     * <p>
     * Typically, objects such as {@link ViewTaskActivity} and {@link
     * StepLayout} instantiate result (and Result
     * subclass) objects; you seldom need to instantiate a result object in your code.
     *
     * @param identifier The unique identifier of the result.
     */
    public TrackedDataSelectionResult(String identifier) {
        super(identifier);
    }

    public List<? extends TrackedDataObject> getSelectedItems() {
        return selectedItems;
    }

    public void setSelectedItems(List<? extends TrackedDataObject> selectedItems) {
        this.selectedItems = selectedItems;
    }
}
