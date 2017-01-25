package org.sagebionetworks.bridge.researchstack;

import org.researchstack.backbone.ResourcePathManager;
import org.researchstack.backbone.ResourceManager;

/**
 * Created by liujoshua on 10/24/16.
 */

public abstract class BridgeResourceManager extends ResourceManager {

  protected abstract ResourcePathManager.Resource getPublicKeyResId();
}
