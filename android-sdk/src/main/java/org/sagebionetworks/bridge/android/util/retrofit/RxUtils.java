package org.sagebionetworks.bridge.android.util.retrofit;

import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;

import retrofit2.Call;
import rx.Observable;
import rx.Single;
import rx.schedulers.Schedulers;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by jyliu on 1/19/2017.
 */
@AnyThread
public class RxUtils {
    @NonNull
    public static <T> Single<T> toBodySingle(@NonNull Call<T> call) {
        checkNotNull(call);

        return Observable.create(new BodyOnSubscribe<>(new CallOnSubscribe<>(call))).toSingle()
                .subscribeOn(Schedulers.io());
    }

}
