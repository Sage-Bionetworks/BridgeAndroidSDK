package org.sagebionetworks.bridge.android;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Response;

@SuppressWarnings("unchecked")
public class BridgeApiTestUtils {
    /**
     * Mocks a Bridge API call object consistent with
     * {@link org.sagebionetworks.bridge.android.util.retrofit.RxUtils#toBodySingle}.
     */
    public static <T> Call<T> mockCallWithValue(T value) throws IOException {
        Call<T> mockCall = mock(Call.class);
        when(mockCall.clone()).thenReturn(mockCall);
        when(mockCall.execute()).thenReturn(Response.success(value));
        return mockCall;
    }

    /** Same as mockCallWithValue, except with an exception. */
    public static <T> Call<T> mockCallWithException(Class<? extends Throwable> throwableType) throws IOException {
        Call<T> mockCall = mock(Call.class);
        when(mockCall.clone()).thenReturn(mockCall);
        when(mockCall.execute()).thenThrow(throwableType);
        return mockCall;
    }
}
