package org.sagebionetworks.bridge.researchstack;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

import org.researchstack.skin.DataProvider;

import retrofit2.Call;
import retrofit2.Response;
import retrofit2.adapter.rxjava.HttpException;
import rx.Observable;

/**
 * Created by liujoshua on 10/24/16.
 */

public class ApiUtils {
  private ApiUtils() {}

  public static <T> Observable<T> toObservable(Call<T> call) {
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


  public static void handleError(Context context, int responseCode) {
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
}
