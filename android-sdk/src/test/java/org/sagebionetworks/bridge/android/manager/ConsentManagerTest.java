package org.sagebionetworks.bridge.android.manager;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import rx.Completable;
import rx.Single;
import rx.schedulers.Schedulers;

import static org.junit.Assert.*;

/**
 * Created by jyliu on 2/2/2017.
 */
public class ConsentManagerTest {
    private static final Logger LOG = LoggerFactory.getLogger(ConsentManagerTest.class);

    @Test
    public void test() {

        Completable c = Completable.complete();
        LOG.debug("Setting doOnComplete");
        c = c.observeOn(Schedulers.newThread())
                .doOnCompleted(() -> {
            LOG.debug("First oncomplete");
        }).doOnCompleted(() -> {
            LOG.debug("OnComplete: Before delay");
            Single.just(1).delay(2, TimeUnit.SECONDS).toBlocking().value();
            throw new NullPointerException();
//            LOG.debug("OnComplete: After delay");
        }).onErrorComplete();

        LOG.debug("Before delay");
        Single.just(1).delay(2, TimeUnit.SECONDS).toBlocking().value();
        LOG.debug("After delay");

        c.observeOn(Schedulers.newThread()).subscribeOn(Schedulers.immediate()).doOnError((e) -> {
            LOG.info("error",e);
        }).await();

        LOG.debug("After await");

        Single.just(1).delay(2, TimeUnit.SECONDS).toBlocking().value();
    }
}