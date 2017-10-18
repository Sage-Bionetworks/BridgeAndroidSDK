package org.sagebionetworks.bridge.android.rx;

import android.support.annotation.NonNull;

import rx.Single;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

/**
 * Created by jyliu on 9/6/2017.
 */

public class MockRxHelper extends RxHelper {
    @Override
    @NonNull
    public <T> Single.Transformer<T, T> subscribeOnIo() {
        return single -> single;
    }

    @Override
    @NonNull
    public <T> Single.Transformer<T, T> observeOnMain() {
        return single -> single;
    }
}
