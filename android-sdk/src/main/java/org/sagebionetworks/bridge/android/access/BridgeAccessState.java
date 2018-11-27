package org.sagebionetworks.bridge.android.access;

/**
 * Represents authentication and authorization state.
 * <p>
 * App version unsupported Requires authentication. Requires consent. Access Granted.
 */
public enum BridgeAccessState {
    REQUIRES_APP_UPGRADE,
    REQUIRES_AUTHENTICATION,
    REQUIRES_CONSENT,
    ACCESS_GRANTED
}
