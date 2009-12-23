package org.yaxim.androidclient.util;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class DataBaseHelper extends SQLiteOpenHelper {

	static final String TAG = "DataBaseHelper";
	static final String DATABASE_NAME = "yaxim.db";
	static final int DATABASE_VERSION = 1;
	static final String ACCOUNTS = "accounts";
	static final String ROSTER = "roster";
	static final String CHATS = "chats";
	
	public DataBaseHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {

		Log.d(TAG, "created new database");

		db.execSQL("CREATE TABLE " + ACCOUNTS + " ("
				+ "id INTEGER PRIMARY KEY AUTOINCREMENT," + "jabber_id TEXT,"
				+ "password TEXT," + "ressource TEXT,"
				+ "default_priority INTEGER," + "away_priority INTEGER,"
				+ "status_message TEXT," + "autoconnect BOOLEAN" + ");");

		db.execSQL("CREATE TABLE " + ROSTER + " ("
				+ "id INTEGER PRIMARY KEY AUTOINCREMENT,"
				+ "account_id INTEGER," + "jabberid TEXT,"
				+ "screen_name TEXT," + "status_mode INTEGER"
				+ "status_message TEXT," + "group TEXT"
				+ " FOREIGN KEY (account_id) REFERENCES " + ACCOUNTS + "(id)"
				+ ");");

		db.execSQL("CREATE TABLE " + CHATS + " ("
				+ "id INTEGER PRIMARY KEY AUTOINCREMENT,"
				+ "roster_id INTEGER," + "message TEXT," + "time TIMESTAMP,"
				+ "read BOOLEAN," + "from_user BOOLEAN"
				+ " FOREIGN KEY (contact_id) REFERENCES " + ROSTER + "(id)"
				+ ");");

	}


	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
				+ newVersion + ". All data will be deleted!");

		db.execSQL("DROP TABLE IF EXISTS " + ACCOUNTS);
		db.execSQL("DROP TABLE IF EXISTS " + ROSTER);
		db.execSQL("DROP TABLE IF EXISTS " + CHATS);

		onCreate(db);
	}

}
