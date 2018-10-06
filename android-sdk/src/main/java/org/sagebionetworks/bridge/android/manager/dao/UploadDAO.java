package org.sagebionetworks.bridge.android.manager.dao;

import android.content.Context;
import androidx.annotation.AnyThread;

import com.google.common.collect.Sets;

import org.sagebionetworks.bridge.android.manager.UploadManager;
import org.sagebionetworks.bridge.rest.model.UploadSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

import javax.inject.Inject;

/**
 * Created by jyliu on 3/10/2017.
 */
@AnyThread
public class UploadDAO extends SharedPreferencesJsonDAO {
    private static final Logger logger = LoggerFactory.getLogger(UploadDAO.class);

    private static final String PREFERENCES_FILE  = "uploads";
    private static final String UPLOAD_FILE_PREFIX = "uploadFile-";
    private static final String UPLOAD_SESSION_PREFIX = "uploadSession-";

    @Inject
    public UploadDAO(Context applicationContext) {
        super(applicationContext, PREFERENCES_FILE);
    }

    public Set<String> listUploadFilenames() {
        Set<String> filenames = Sets.newHashSet();

        for (String key : sharedPreferences.getAll().keySet()) {
            if (key.startsWith(UPLOAD_FILE_PREFIX)) {
                String filename = key.substring(UPLOAD_FILE_PREFIX.length());
                filenames.add(filename);
            }
        }

        logger.debug("listUploadFilenames called, found: " + filenames);

        return filenames;
    }

    public void putUploadFile(String filename, UploadManager.UploadFile uploadFile) {
        setValue(UPLOAD_FILE_PREFIX + filename, uploadFile, UploadManager.UploadFile.class);
    }

    public UploadManager.UploadFile getUploadFile(String filename) {
        return getValue(UPLOAD_FILE_PREFIX + filename, UploadManager.UploadFile.class);
    }

    public void putUploadSession(String filename, UploadSession uploadSession) {
        setValue(UPLOAD_SESSION_PREFIX + filename, uploadSession, UploadSession.class);
    }

    public UploadSession getUploadSession(String filename) {
        return getValue(UPLOAD_SESSION_PREFIX + filename, UploadSession.class);
    }

    public void removeUploadAndSession(String filename) {
        removeValue(UPLOAD_FILE_PREFIX + filename);
        removeValue(UPLOAD_SESSION_PREFIX + filename);
    }
}
