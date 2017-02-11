package org.sagebionetworks.bridge.researchstack;

import android.content.Context;

import org.researchstack.backbone.model.User;
import org.sagebionetworks.bridge.android.manager.dao.SharedPreferencesJsonDAO;

/**
 * Created by jyliu on 2/9/2017.
 */

public class ResearchStackDAO extends SharedPreferencesJsonDAO {
    private static final String PREFERENCES_FILE = "researchstack-plaintext";

    private static final String USER_KEY = "user";

    public ResearchStackDAO(Context applicationContext) {
        super(applicationContext, PREFERENCES_FILE);
    }

    public void setUser(User user) {
        setValue(USER_KEY, user, User.class);
    }

    public User getUser() {
        return getValue(USER_KEY, User.class);
    }
}
