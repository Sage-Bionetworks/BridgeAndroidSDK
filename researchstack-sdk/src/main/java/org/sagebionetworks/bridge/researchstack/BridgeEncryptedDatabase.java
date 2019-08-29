package org.sagebionetworks.bridge.researchstack;

import android.content.Context;

import net.sqlcipher.database.SQLiteDatabase;

import org.sagebionetworks.researchstack.backbone.storage.database.sqlite.SqlCipherDatabaseHelper;
import org.sagebionetworks.researchstack.backbone.storage.database.sqlite.UpdatablePassphraseProvider;

public class BridgeEncryptedDatabase extends SqlCipherDatabaseHelper  {
  public BridgeEncryptedDatabase(Context context, String name,
      SQLiteDatabase.CursorFactory cursorFactory, int version,
      UpdatablePassphraseProvider passphraseProvider) {
    super(context, name, cursorFactory, version, passphraseProvider);
  }

  @Override
  public void onCreate(SQLiteDatabase sqLiteDatabase) {
    super.onCreate(sqLiteDatabase);
  }

  @Override
  public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
    super.onUpgrade(sqLiteDatabase, oldVersion, newVersion);
    // handle future db upgrades here
  }
}
