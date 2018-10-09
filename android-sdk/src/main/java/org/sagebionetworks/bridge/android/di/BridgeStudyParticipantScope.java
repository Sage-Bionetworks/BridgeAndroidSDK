package org.sagebionetworks.bridge.android.di;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

import javax.inject.Scope;

/**
 * Scope to a Bridge study participant.
 */
@Documented
@Retention(RUNTIME)
@Scope
public @interface BridgeStudyParticipantScope {}
