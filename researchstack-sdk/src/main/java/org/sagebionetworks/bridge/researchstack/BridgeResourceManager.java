package org.sagebionetworks.bridge.researchstack;

import org.sagebionetworks.researchstack.backbone.ResourceManager;
import org.sagebionetworks.researchstack.backbone.ResourcePathManager;


/**
 * Created by liujoshua on 10/24/16.
 */

public abstract class BridgeResourceManager extends ResourceManager {

  protected abstract ResourcePathManager.Resource getPublicKeyResId();
}
