package org.sagebionetworks.bridge.researchstack;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;

import org.researchstack.backbone.DataProvider;
import org.researchstack.backbone.DataResponse;
import org.sagebionetworks.bridge.rest.model.Message;

import retrofit2.Call;
import retrofit2.Response;
import retrofit2.adapter.rxjava.HttpException;
import rx.Observable;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by liujoshua on 10/24/16.
 */

public class ApiUtils {
    public static final Observable<DataResponse> SUCCESS_DATA_RESPONSE = Observable.just(new DataResponse(true, "success"))
            .cache();

    private ApiUtils() {
    }

    @NonNull
    public static <T> Observable<Response<T>> toResponseObservable(@NonNull Call<T> call) {
        return Observable.fromCallable(() -> call.clone().execute());
    }

    @NonNull
    public static <T> Observable<T> toBodyObservable(@NonNull Call<T> call) {

        return Observable.fromCallable(() -> {
            // Since Call is a one-shot type, clone it for each new caller.
            Response<T> response = call.clone().execute();
            if (response.isSuccessful()) {
                return response.body();
            }
            throw new HttpException(response);
        });
        // TODO: make it so this call will not exception during unit tests from calling Android APIs
        // .compose(ObservableUtils.applyDefault());
    }

    public static void handleError(@NonNull Context context, int responseCode) {
        checkNotNull(context);

        String intentAction = null;

        switch (responseCode) {
            // Not signed in.
            case 401:
                intentAction = DataProvider.ERROR_NOT_AUTHENTICATED;
                break;

            // Not Consented
            case 412:
                intentAction = DataProvider.ERROR_CONSENT_REQUIRED;
                break;
        }

        if (intentAction != null) {
            LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(intentAction));
        }
    }

    @NonNull
    public static DataResponse toDataResponse(@NonNull Message message) {
        return new DataResponse(true, message.getMessage());
    }

    @NonNull
    public static Observable<DataResponse> toDataResponseObservable(@NonNull Message message) {
        return Observable.just(toDataResponse(message));
    }

}
