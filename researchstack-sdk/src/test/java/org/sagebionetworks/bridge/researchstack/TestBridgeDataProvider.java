package org.sagebionetworks.bridge.researchstack;

import android.content.Context;

import org.researchstack.backbone.ResourcePathManager;
import org.researchstack.backbone.result.TaskResult;
import org.researchstack.skin.AppPrefs;
import org.sagebionetworks.bridge.android.manager.BridgeManagerProvider;
import org.sagebionetworks.bridge.researchstack.wrapper.StorageAccessWrapper;
import org.sagebionetworks.bridge.rest.ApiClientProvider;

public class TestBridgeDataProvider extends BridgeDataProvider {
    public static final String BASE_URL = "http://sagebase.org/test/url/";
    public static final String STUDY_ID = "study-id";
    public static final String STUDY_NAME = "study-name";
    public static final String USER_AGENT = "user-agent";
    public static final int APP_VERSION = 1;

    public final ResourcePathManager.Resource PUBLIC_KEY_RES;
    public final ResourcePathManager.Resource TASKS_AND_SCHEDULES;

    public TestBridgeDataProvider(ResourcePathManager.Resource publicKey, ResourcePathManager
                                          .Resource tasksAndSchedules, ApiClientProvider
                                          apiClientProvider, AppPrefs appPrefs,
                                  StorageAccessWrapper storageAccess, UserLocalStorage
                                          userLocalStorage, ConsentLocalStorage
                                          consentLocalStorage, TaskHelper taskHelper,
                                  UploadHandler uploadHandler) {
        super(BASE_URL, STUDY_ID, USER_AGENT, publicKey,
              apiClientProvider, appPrefs,
              storageAccess, userLocalStorage, consentLocalStorage, taskHelper, uploadHandler);
        this.PUBLIC_KEY_RES = publicKey;
        this.TASKS_AND_SCHEDULES = tasksAndSchedules;
    }

    @Override
    public void processInitialTaskResult(Context context, TaskResult taskResult) {
        // handle result from initial task (save profile info to disk, upload to your server, etc)
    }
}
