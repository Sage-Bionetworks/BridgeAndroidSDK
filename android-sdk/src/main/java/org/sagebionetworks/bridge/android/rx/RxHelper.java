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
 * Created by jyliu on 1/19/2017.
 */
@AnyThread
public class RxHelper {
    public RxHelper() {

    }
    @NonNull
    public <T> Single<T> toBodySingle(@NonNull Call<T> call) {
        checkNotNull(call);

        return Observable
                .create(new BodyOnSubscribe<T>(new CallOnSubscribe<T>(call)))
                .toSingle()
                .compose(subscribeOnIo());
    }

    @NonNull
    public <T> Single.Transformer<T, T> subscribeOnIo() {
        return single -> single.subscribeOn(Schedulers.io());
    }

    @NonNull
    public <T> Single.Transformer<T, T> observeOnMain() {
        return single -> single.observeOn(AndroidSchedulers.mainThread());
    }
}
