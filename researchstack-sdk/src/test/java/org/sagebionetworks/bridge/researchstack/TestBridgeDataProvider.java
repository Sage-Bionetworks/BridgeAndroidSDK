package org.sagebionetworks.bridge.researchstack;

import android.content.Context;

import org.researchstack.backbone.ResourcePathManager;
import org.researchstack.backbone.result.TaskResult;
import org.researchstack.skin.AppPrefs;
import org.sagebionetworks.bridge.researchstack.wrapper.StorageAccessWrapper;


public class TestBridgeDataProvider extends BridgeDataProvider {
    public static final String BASE_URL = "http://sagebase.org/test/url/";
    public static final String STUDY_ID = "study-id";
    public static final String STUDY_NAME = "study-name";
    public static final String USER_AGENT = "user-agent";
    public static final int APP_VERSION = 1;

    public final ResourcePathManager.Resource PUBLIC_KEY_RES;
    public final ResourcePathManager.Resource TASKS_AND_SCHEDULES;

    public TestBridgeDataProvider(ResourcePathManager.Resource publicKey, ResourcePathManager.Resource tasksAndSchedules, BridgeService bridgeService, AppPrefs appPrefs, StorageAccessWrapper storageAccess, UserLocalStorage userLocalStorage, ConsentLocalStorage consentLocalStorage) {
        super(BASE_URL, STUDY_ID, USER_AGENT, bridgeService, appPrefs, storageAccess, userLocalStorage, consentLocalStorage);
        this.PUBLIC_KEY_RES = publicKey;
        this.TASKS_AND_SCHEDULES = tasksAndSchedules;
    }

    @Override
    public void processInitialTaskResult(Context context, TaskResult taskResult) {
        // handle result from initial task (save profile info to disk, upload to your server, etc)
    }

    @Override
    protected ResourcePathManager.Resource getPublicKeyResId() {
        return PUBLIC_KEY_RES;
    }

    @Override
    protected ResourcePathManager.Resource getTasksAndSchedules() {
        return TASKS_AND_SCHEDULES;
    }
}
