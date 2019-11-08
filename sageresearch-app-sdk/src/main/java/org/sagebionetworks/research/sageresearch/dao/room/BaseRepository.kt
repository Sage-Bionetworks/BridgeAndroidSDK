package org.sagebionetworks.research.sageresearch.dao.room

import android.support.annotation.VisibleForTesting
import hu.akarnokd.rxjava.interop.RxJavaInterop
import io.reactivex.Completable
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.slf4j.LoggerFactory

abstract class BaseRepository {

    protected val logger = LoggerFactory.getLogger(this::class.java)

    protected val compositeDispose = CompositeDisposable()
    /**
     * Subscribes to the completable using the CompositeDisposable
     * This is open for unit testing purposes to to run a blockingGet() instead of an asynchronous subscribe
     */
    @VisibleForTesting
    protected open fun subscribeCompletable(completable: Completable, successMsg: String, errorMsg: String) {
        compositeDispose.add(completable
                .subscribeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    logger.info(successMsg)
                }, {
                    logger.warn("$errorMsg ${it.localizedMessage}")
                }))
    }

    /**
     * @property asyncScheduler for performing network and database operations
     */
    @VisibleForTesting
    protected open val asyncScheduler: Scheduler
        get() = Schedulers.io()
    @VisibleForTesting
    protected open val asyncSchedulerV1: rx.Scheduler get() = rx.schedulers.Schedulers.io()

    /**
     * toV2SingleAsync makes sure that the v1 Single happens on the async scheduler
     */
    protected fun <T>toV2SingleAsync(single: rx.Single<T>): Single<T> {
        return RxJavaInterop.toV2Single(single.observeOn(asyncSchedulerV1))
    }
}