package org.sagebionetworks.bridge.researchsuite.sdk.data;

import android.arch.persistence.room.Room;
import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(AndroidJUnit4.class)
public class TaskDAOTest {

    private TaskDAO taskDAO;
    private ActivityDatabase db;

    @Before
    public void createDb() {
        Context context = InstrumentationRegistry.getTargetContext();
        db = Room.inMemoryDatabaseBuilder(context, ActivityDatabase.class).build();
        taskDAO = db.getTaskDAO();
    }

    @Test
    public void getById_upsert_delete() throws Exception {
        String id = "id1";

        TaskEntity task = new TaskEntity();
        task.setIdentifier(id);

        TaskEntity result = taskDAO.getById(id);
        assertNull(result);

        taskDAO.upsert(task);
        result = taskDAO.getById(id);

        assertEquals(result, task);

        taskDAO.delete(task);
        result = taskDAO.getById(id);

        assertNull(result);
    }
}