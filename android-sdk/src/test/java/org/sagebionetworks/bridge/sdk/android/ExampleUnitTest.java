package org.sagebionetworks.bridge.sdk.android;

import android.util.Log;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;

import static org.junit.Assert.assertEquals;

/**
 * To work on unit tests, switch the Test Artifact in the Build Variants view.
 */
public class ExampleUnitTest {
  @Test
  public void addition_isCorrect() throws Exception {

    Logger log = LoggerFactory.getLogger(ExampleUnitTest.class);
    log.error("ERROR ME");
    assertEquals(4, 2 + 2);
  }
}