package org.sagebionetworks.bridge.android.rx;

import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;

import retrofit2.Call;
import rx.Observable;
import rx.Single;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A helper for working with between Retrofit Calls and RxJava Singles. Non static for dependency
 * injection for testing purposes.
 */
@AnyThread
public class RxHelper {
    public RxHelper() {
    }

    /**
     * Converts a Call into a Single configured to subscribe on the IO thread.
     *
     * @param call retrofit call
     * @param <T>  call response type
     * @return a Single containing the call's response
     */
    @NonNull
    public <T> Single<T> toBodySingle(@NonNull Call<T> call) {
        checkNotNull(call);

        return Observable
                .create(new BodyOnSubscribe<T>(new CallOnSubscribe<T>(call)))
                .toSingle()
                .compose(subscribeOnIo());
    }

    /**
     * Transforms a Single to subscribe on the IO thread. Note that the first Subscribe thread
     * set on a Single takes precedence over subsequent assignment of subscribe threads.
     *
     * @param <T> response type
     * @return single that will subscribe on IO thread
     */
    @NonNull
    public <T> Single.Transformer<T, T> subscribeOnIo() {
        return single -> single.subscribeOn(Schedulers.io());
    }

    /**
     * @param <T> response type
     * @return Single that performs subsequent observation on the main thread
     */
    @NonNull
    public <T> Single.Transformer<T, T> observeOnMain() {
        return single -> single.observeOn(AndroidSchedulers.mainThread());
    }
}
