package org.sagebionetworks.bridge.android.di;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

import javax.inject.Scope;

/**
 * Scope to a Bridge application. Parent scope of {@link BridgeStudyScope}
 */
@Documented
@Retention(RUNTIME)
@Scope
public @interface BridgeApplicationScope {}
