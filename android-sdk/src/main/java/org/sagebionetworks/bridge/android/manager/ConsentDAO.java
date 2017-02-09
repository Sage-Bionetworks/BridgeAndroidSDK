package org.sagebionetworks.bridge.android.manager;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.sagebionetworks.bridge.rest.model.ConsentSignature;

import java.util.Set;

/**
 * Created by jyliu on 2/8/2017.
 */
public interface ConsentDAO {
    @NonNull
    Set<String> list();

    @Nullable
    ConsentSignature get(@NonNull String subpopulationGuid);

    void put(@NonNull String subpopulationGuid,
                          @NonNull ConsentSignature consentSignature);

    void remove(@NonNull String subpopulationGuid);
}
