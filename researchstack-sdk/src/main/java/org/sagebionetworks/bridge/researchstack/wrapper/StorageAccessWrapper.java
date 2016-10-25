package org.sagebionetworks.bridge.researchstack.wrapper;

import android.content.Context;

import org.researchstack.backbone.StorageAccess;
import org.researchstack.backbone.storage.database.AppDatabase;
import org.researchstack.backbone.storage.file.FileAccess;
import org.researchstack.backbone.storage.file.PinCodeConfig;

/**
 * Wrap @see StorageAccess to help with writing unit tests.
 * <p>
 * StorageAccess declares and initializes static variables which depend on Android classes. This
 * causes an exception to be thrown when the class is referenced, making it unmockable.
 */
public class StorageAccessWrapper {

  private final StorageAccess storageAccess;

  public StorageAccessWrapper() {
    this(StorageAccess.getInstance());
  }

  public StorageAccessWrapper(StorageAccess storageAccess) {
    this.storageAccess = storageAccess;
  }

  /*@see StorageAccess#getFileAccess */
  public FileAccess getFileAccess() {
    return storageAccess.getFileAccess();
  }

  /*@see StorageAccess#hasPinCode(Context) */
  public boolean hasPinCode(Context context) {
    return storageAccess.hasPinCode(context);
  }

  /*@see StorageAccess#getAppDatabase */
  public AppDatabase getAppDatabase() {
    return storageAccess.getAppDatabase();
  }

  /*@see StorageAccess#getPinCodeConfig */
  public PinCodeConfig getPinCodeConfig() {
    return storageAccess.getPinCodeConfig();
  }
}
