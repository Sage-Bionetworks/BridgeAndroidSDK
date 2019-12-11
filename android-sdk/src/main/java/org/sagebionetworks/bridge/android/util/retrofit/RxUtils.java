package org.sagebionetworks.bridge.android.util.retrofit;

import static com.google.common.base.Preconditions.checkNotNull;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

import retrofit2.Call;
import rx.Observable;
import rx.Single;
import rx.schedulers.Schedulers;

/**
 * Created by jyliu on 1/19/2017.
 */
@AnyThread
public class RxUtils {
    /**
     * Converts a Retrofit Call to a single, subscribes on an IO thread, so the network call is
     * performed on an IO thread.
     *
     * @param call call to get response of
     * @param <T> response type
     * @return a response single
     */
    @NonNull
    public static <T> Single<T> toBodySingle(@NonNull Call<T> call) {
        checkNotNull(call);
        return Observable.create(new BodyOnSubscribe<>(new CallOnSubscribe<>(call)))
                .subscribeOn(Schedulers.io()).toSingle();
    }
}
