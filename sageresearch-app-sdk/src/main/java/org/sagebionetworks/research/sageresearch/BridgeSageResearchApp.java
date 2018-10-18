package org.sagebionetworks.research.sageresearch;

import com.jakewharton.threetenabp.AndroidThreeTen;

import org.sagebionetworks.bridge.android.BridgeApplication;

public class BridgeSageResearchApp extends BridgeApplication {
    @Override
    public void onCreate() {
        AndroidThreeTen.init(this);

        super.onCreate();
    }
}
