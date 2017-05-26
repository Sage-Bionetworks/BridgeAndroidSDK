package org.sagebionetworks.bridge.android.manager;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Created by jameskizer on 5/26/17.
 */

public class UploadFileDbHelper extends SQLiteOpenHelper {

    private static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + UploadFileContract.UploadFileSchema.TABLE_NAME + " (" +
                    UploadFileContract.UploadFileSchema._ID + " INTEGER PRIMARY KEY," +
                    UploadFileContract.UploadFileSchema.COLUMN_NAME_FILENAME + " TEXT," +
                    UploadFileContract.UploadFileSchema.COLUMN_NAME_CONTENT_TYPE + " TEXT," +
                    UploadFileContract.UploadFileSchema.COLUMN_NAME_FILE_LENGTH + " INTEGER," +
                    UploadFileContract.UploadFileSchema.COLUMN_NAME_MD5_HASH + " TEXT)";

    private static final String SQL_DELETE_ENTRIES =
            "DROP TABLE IF EXISTS " + UploadFileContract.UploadFileSchema.TABLE_NAME;

    // If you change the database schema, you must increment the database version.
    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "UploadFile.db";

    public UploadFileDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_ENTRIES);
    }
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        //we shouldn't delete any cached data on upgrade, but might need to migrate
        //cross that bridge when we come to it
        db.execSQL(SQL_DELETE_ENTRIES);
        onCreate(db);
    }
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }
}
