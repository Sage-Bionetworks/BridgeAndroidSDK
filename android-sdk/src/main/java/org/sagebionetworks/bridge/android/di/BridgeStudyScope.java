package org.sagebionetworks.bridge.android.di;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

import javax.inject.Scope;

/**
 * Scope to a Bridge study. Parent scope of {@link BridgeStudyParticipantScope}
 */
@Documented
@Retention(RUNTIME)
@Scope
public @interface BridgeStudyScope {
}
