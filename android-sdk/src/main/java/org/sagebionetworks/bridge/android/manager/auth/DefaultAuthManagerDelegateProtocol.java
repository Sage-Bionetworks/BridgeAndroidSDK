package org.sagebionetworks.bridge.android.manager.auth;

import android.content.Context;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.sagebionetworks.bridge.rest.model.UserSessionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by jyliu on 1/20/2017.
 */
@AnyThread
class DefaultAuthManagerDelegateProtocol implements AuthManager.AuthManagerDelegateProtocol {
    private static final Logger logger = LoggerFactory.getLogger
            (DefaultAuthManagerDelegateProtocol.class);

    public DefaultAuthManagerDelegateProtocol(@NonNull Context context) {
        checkNotNull(context);
    }

    // TODO: store these in a secure, persisted manner
    @Nullable
    private UserSessionInfo userSessionInfo;
    @Nullable
    private String email;
    @Nullable
    private String password;

    @Nullable
    @Override
    public UserSessionInfo getUserSessionInfo() {
        logger.info("getUserSessionInfo called, found: " + userSessionInfo);

        return userSessionInfo;
    }

    @Override
    public void setUserSessionInfo(@Nullable UserSessionInfo userSessionInfo) {
        logger.info("setUserSessionInfo called with: " + userSessionInfo);

        this.userSessionInfo = userSessionInfo;
    }

    @Nullable
    @Override
    public String getEmail() {
        logger.info("getEmail called, found: " + email);

        return email;
    }

    @Override
    public void setEmail(@Nullable String email) {
        logger.info("setEmail called with: " + email);

        this.email = email;
    }

    @Nullable
    @Override
    public String getPassword() {
        logger.info("getPassword called, found: " + password);

        return password;
    }

    @Override
    public void setPassword(@Nullable String password) {
        logger.info("setPassword called with: " + password);

        this.password = password;
    }
}
