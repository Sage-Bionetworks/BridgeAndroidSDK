package org.sagebionetworks.bridge.researchstack.task.tracked;

import com.google.gson.annotations.SerializedName;

import org.sagebionetworks.researchstack.backbone.model.taskitem.TaskItem;

import java.util.List;

/**
 * Created by TheMDP on 3/26/17.
 */

public class  TrackedTaskItem <T extends TrackedDataObject> extends TaskItem {
    @SerializedName("items")
    private List<T> items;

    /* Default constructor needed for serialization/deserialization of object */
    public TrackedTaskItem() {
        super();
    }

    public List<T> getItems() {
        return items;
    }

    public void setItems(List<T> items) {
        this.items = items;
    }
}
