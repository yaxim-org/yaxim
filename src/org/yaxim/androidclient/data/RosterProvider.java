package org.yaxim.androidclient.data;

import java.util.ArrayList;

import org.yaxim.androidclient.util.LogConstants;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.os.Handler;
import android.util.Log;

public class RosterProvider extends ContentProvider {

	public static final String AUTHORITY = "org.yaxim.androidclient.provider.Roster";
	public static final String TABLE_ROSTER = "roster";
	public static final String TABLE_GROUPS = "groups";
	public static final String TABLE_MUCS = "mucs";
	public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY
			+ "/" + TABLE_ROSTER);
	public static final Uri GROUPS_URI = Uri.parse("content://" + AUTHORITY
			+ "/" + TABLE_GROUPS);
	public static final Uri MUCS_URI = Uri.parse("content://" + AUTHORITY
			+ "/" + TABLE_MUCS);
	public static final String QUERY_ALIAS = "main_result";

	private static final UriMatcher URI_MATCHER = new UriMatcher(
			UriMatcher.NO_MATCH);
	private static final int CONTACTS = 1;
	private static final int CONTACT_ID = 2;
	private static final int GROUPS = 3;
	private static final int GROUP_MEMBERS = 4;
	private static final int MUCS = 5;
	

	static {
		URI_MATCHER.addURI(AUTHORITY, "roster", CONTACTS);
		URI_MATCHER.addURI(AUTHORITY, "roster/#", CONTACT_ID);
		URI_MATCHER.addURI(AUTHORITY, "groups", GROUPS);
		URI_MATCHER.addURI(AUTHORITY, "groups/*", GROUP_MEMBERS);
		URI_MATCHER.addURI(AUTHORITY, "mucs", MUCS);
	}

	private static final String TAG = "yaxim.RosterProvider";

	private Runnable mNotifyChange = new Runnable() {
		public void run() {
			Log.d(TAG, "notifying change");
			getContext().getContentResolver().notifyChange(CONTENT_URI, null);
			getContext().getContentResolver().notifyChange(GROUPS_URI, null);
			getContext().getContentResolver().notifyChange(MUCS_URI, null);
		}
	};
	private Handler mNotifyHandler = new Handler();

	private SQLiteOpenHelper mOpenHelper;


	public RosterProvider() {
	}

	@Override
	public int delete(Uri url, String where, String[] whereArgs) {
		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
		int count;
				
		switch (URI_MATCHER.match(url)) {

		case CONTACTS:
			count = db.delete(TABLE_ROSTER, where, whereArgs);
			break;

		case CONTACT_ID:
			String segment = url.getPathSegments().get(1);

			if (TextUtils.isEmpty(where)) {
				where = "_id=" + segment;
			} else {
				where = "_id=" + segment + " AND (" + where + ")";
			}

			count = db.delete(TABLE_ROSTER, where, whereArgs);
			break;

		case MUCS:
			count = db.delete(TABLE_MUCS, where, whereArgs);
			break;
		default:
			throw new IllegalArgumentException("Cannot delete from URL: " + url);
		}

		getContext().getContentResolver().notifyChange(GROUPS_URI, null);
		notifyChange();

		return count;
	}

	@Override
	public String getType(Uri url) {
		int match = URI_MATCHER.match(url);
		switch (match) {
		case CONTACTS:
			return RosterConstants.CONTENT_TYPE;
		case CONTACT_ID:
			return RosterConstants.CONTENT_ITEM_TYPE;
		case MUCS:
			return RosterConstants.MUC_TYPE;
		default:
			throw new IllegalArgumentException("Unknown URL");
		}
	}

	@Override
	public Uri insert(Uri url, ContentValues initialValues) {
		
		if(URI_MATCHER.match(url) == CONTACTS) {
		ContentValues values = (initialValues != null) ? new ContentValues(
				initialValues) : new ContentValues();
		for (String colName : RosterConstants.getRequiredContactColumns()) {
			if (values.containsKey(colName) == false) {
				throw new IllegalArgumentException("Missing column: " + colName);
			}
		}

		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
		long rowId = db.insert(TABLE_ROSTER, RosterConstants.JID, values);
		if (rowId < 0) {
			throw new SQLException("Failed to insert row into " + url);
		}
		Uri noteUri = ContentUris.withAppendedId(CONTENT_URI, rowId);
		notifyChange();

		return noteUri;
		} else if (URI_MATCHER.match(url) == MUCS) {
			ContentValues values = (initialValues != null) ? new ContentValues(
					initialValues) : new ContentValues();
			for (String colName : RosterConstants.getRequiredMUCColumns()) {
				if (values.containsKey(colName) == false) {
					throw new IllegalArgumentException("Missing column: " + colName);
				}
			}
			
			SQLiteDatabase db = mOpenHelper.getWritableDatabase();
			long rowId = db.insert(TABLE_MUCS, RosterConstants.JID, values);
			if (rowId < 0) {
				throw new SQLException("Failed to insert row into " + url);
			}
			Uri noteUri = ContentUris.withAppendedId(CONTENT_URI, rowId);
			notifyChange();
			
			return noteUri;
		} else {
			throw new IllegalArgumentException("Cannot insert into URL: " + url);
		}
		
	}

	@Override
	public boolean onCreate() {
		mOpenHelper = new RosterDatabaseHelper(getContext());
		return true;
	}

	@Override
	public Cursor query(Uri url, String[] projectionIn, String selection,
			String[] selectionArgs, String sortOrder) {
		
		SQLiteQueryBuilder qBuilder = new SQLiteQueryBuilder();
		int match = URI_MATCHER.match(url);
		
		String groupBy = null;
		switch (match) {

		case GROUPS:
			qBuilder.setTables(TABLE_ROSTER + " " + QUERY_ALIAS);
			groupBy = RosterConstants.GROUP;
			break;

		case GROUP_MEMBERS:
			qBuilder.setTables(TABLE_ROSTER + " " + QUERY_ALIAS);
			qBuilder.appendWhere(RosterConstants.GROUP + "=");
			qBuilder.appendWhere(url.getPathSegments().get(1));
			break;

		case CONTACTS:
			qBuilder.setTables(TABLE_ROSTER + " " + QUERY_ALIAS);
			break;

		case CONTACT_ID:
			qBuilder.setTables(TABLE_ROSTER + " " + QUERY_ALIAS);
			qBuilder.appendWhere("_id=");
			qBuilder.appendWhere(url.getPathSegments().get(1));
			break;
			
		case MUCS:
			qBuilder.setTables(TABLE_MUCS + " " + QUERY_ALIAS);
			break;

		default:
			throw new IllegalArgumentException("Unknown URL " + url);
		}

		String orderBy;
		if (TextUtils.isEmpty(sortOrder) && match == CONTACTS) {
			orderBy = RosterConstants.DEFAULT_SORT_ORDER;
		} else {
			orderBy = sortOrder;
		}

		SQLiteDatabase db = mOpenHelper.getReadableDatabase();
		Cursor ret = qBuilder.query(db, projectionIn, selection, selectionArgs,
				groupBy, null, orderBy);

		if (ret == null) {
			infoLog("RosterProvider.query: failed");
		} else {
			ret.setNotificationUri(getContext().getContentResolver(), url);
		}

		return ret;
	}

	@Override
	public int update(Uri url, ContentValues values, String where,
			String[] whereArgs) {
		int count;
		long rowId = 0;
		int match = URI_MATCHER.match(url);
		SQLiteDatabase db = mOpenHelper.getWritableDatabase();

		switch (match) {
		case CONTACTS:
			count = db.update(TABLE_ROSTER, values, where, whereArgs);
			break;
		case CONTACT_ID:
			String segment = url.getPathSegments().get(1);
			rowId = Long.parseLong(segment);
			count = db.update(TABLE_ROSTER, values, "_id=" + rowId, whereArgs);
			break;
		case MUCS:
			count = db.update(TABLE_MUCS, values, where, whereArgs); 
			break;
		default:
			throw new UnsupportedOperationException("Cannot update URL: " + url);
		}

		notifyChange();

		return count;

	}

	/* delay change notification, cancel previous attempts.
	 * this implements rate throttling on fast update sequences
	 */
	long last_notify = 0;
	private void notifyChange() {
		mNotifyHandler.removeCallbacks(mNotifyChange);
		long ts = System.currentTimeMillis();
		if (ts > last_notify + 500)
			mNotifyChange.run();
		else
			mNotifyHandler.postDelayed(mNotifyChange, 200);
		last_notify = ts;
	}

	private static void infoLog(String data) {
		if (LogConstants.LOG_INFO) {
			Log.i(TAG, data);
		}
	}

	private static class RosterDatabaseHelper extends SQLiteOpenHelper {

		private static final String DATABASE_NAME = "roster.db";
		private static final int DATABASE_VERSION = 6;

		public RosterDatabaseHelper(Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}

		private void onCreateRoster(SQLiteDatabase db) {
			infoLog("creating new roster table");

			db.execSQL("CREATE TABLE " + TABLE_ROSTER + " ("
					+ RosterConstants._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
					+ RosterConstants.JID + " TEXT UNIQUE ON CONFLICT REPLACE, "
					+ RosterConstants.ALIAS + " TEXT, " 
					+ RosterConstants.STATUS_MODE + " INTEGER, "
					+ RosterConstants.STATUS_MESSAGE + " TEXT, "
					+ RosterConstants.GROUP + " TEXT);");
			db.execSQL("CREATE INDEX idx_roster_group ON " + TABLE_ROSTER
				        + " (" + RosterConstants.GROUP + ")");
			db.execSQL("CREATE INDEX idx_roster_alias ON " + TABLE_ROSTER
				        + " (" + RosterConstants.ALIAS + ")");
			db.execSQL("CREATE INDEX idx_roster_status ON " + TABLE_ROSTER
				        + " (" + RosterConstants.STATUS_MODE + ")");
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			onCreateRoster(db);
			db.execSQL("CREATE TABLE " + TABLE_MUCS + " ("
					+ RosterConstants._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
					+ RosterConstants.JID + " TEXT UNIQUE ON CONFLICT REPLACE, "
					+ RosterConstants.NICKNAME + " TEXT, "
					+ RosterConstants.PASSWORD + " TEXT"
					+ ");");
			
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			infoLog("onUpgrade: from " + oldVersion + " to " + newVersion);
			switch (oldVersion) {
			case 4:
			case 5:
				db.execSQL("CREATE TABLE " + TABLE_MUCS + " ("
						+ RosterConstants._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
						+ RosterConstants.JID + " TEXT UNIQUE ON CONFLICT REPLACE, "
						+ RosterConstants.NICKNAME + " TEXT, "
						+ RosterConstants.PASSWORD + " TEXT"
						+ ");");
			default:
				db.execSQL("DROP TABLE IF EXISTS " + TABLE_GROUPS);
				db.execSQL("DROP TABLE IF EXISTS " + TABLE_ROSTER);
				onCreateRoster(db);
			}
		}
	}

	public static final class RosterConstants implements BaseColumns {

		private RosterConstants() {
		}

		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.yaxim.roster";
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.yaxim.roster";
		public static final String MUC_TYPE = "TODO";

		public static final String JID = "jid";
		public static final String ALIAS = "alias";
		public static final String STATUS_MODE = "status_mode";
		public static final String STATUS_MESSAGE = "status_message";
		public static final String GROUP = "roster_group";
		public static final String MUCS = "\uFFFF"; // XXX: hack to end up at the end of the alphabetical list
		
		public static final String PASSWORD = "password";
		public static final String NICKNAME = "nickname";

		public static final String DEFAULT_SORT_ORDER = STATUS_MODE + " DESC, " + ALIAS + " COLLATE NOCASE";

		public static ArrayList<String> getRequiredContactColumns() {
			ArrayList<String> tmpList = new ArrayList<String>();
			tmpList.add(JID);
			tmpList.add(ALIAS);
			tmpList.add(STATUS_MODE);
			tmpList.add(STATUS_MESSAGE);
			tmpList.add(GROUP);
			return tmpList;
		}
		
		public static ArrayList<String> getRequiredMUCColumns() {
			ArrayList<String> tmpList = new ArrayList<String>();
			tmpList.add(JID);
			tmpList.add(NICKNAME);
			tmpList.add(PASSWORD);
			return tmpList;
		}

	}

}
