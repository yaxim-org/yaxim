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
import android.util.Log;

public class RosterProvider extends ContentProvider {

	public static final String AUTHORITY = "org.yaxim.androidclient.provider.Roster";
	public static final String TABLE_NAME = "roster";
	public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY
			+ "/" + TABLE_NAME);

	private static final UriMatcher URI_MATCHER = new UriMatcher(
			UriMatcher.NO_MATCH);

	private static final int CONTACTS = 1;
	private static final int CONTACT_ID = 2;

	static {
		URI_MATCHER.addURI(AUTHORITY, "roster", CONTACTS);
		URI_MATCHER.addURI(AUTHORITY, "roster/#", CONTACT_ID);
	}

	private static final String TAG = "RosterProvider";

	private SQLiteOpenHelper mOpenHelper;

	public RosterProvider() {
	}

	@Override
	public int delete(Uri url, String where, String[] whereArgs) {
		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
		int count;
		switch (URI_MATCHER.match(url)) {

		case CONTACTS:
			count = db.delete(TABLE_NAME, where, whereArgs);
			break;

		case CONTACT_ID:
			String segment = url.getPathSegments().get(1);

			if (TextUtils.isEmpty(where)) {
				where = "_id=" + segment;
			} else {
				where = "_id=" + segment + " AND (" + where + ")";
			}

			count = db.delete(TABLE_NAME, where, whereArgs);
			break;

		default:
			throw new IllegalArgumentException("Cannot delete from URL: " + url);
		}

		getContext().getContentResolver().notifyChange(url, null);
		return count;
	}

	@Override
	public String getType(Uri url) {
		int match = URI_MATCHER.match(url);
		switch (match) {
		case CONTACTS:
			return Constants.CONTENT_TYPE;
		case CONTACT_ID:
			return Constants.CONTENT_ITEM_TYPE;
		default:
			throw new IllegalArgumentException("Unknown URL");
		}
	}

	@Override
	public Uri insert(Uri url, ContentValues initialValues) {
		if (URI_MATCHER.match(url) != CONTACTS) {
			throw new IllegalArgumentException("Cannot insert into URL: " + url);
		}

		ContentValues values = (initialValues != null) ? new ContentValues(
				initialValues) : new ContentValues();

		for (String colName : Constants.getRequiredColumns()) {
			if (values.containsKey(colName) == false) {
				throw new IllegalArgumentException("Missing column: " + colName);
			}
		}

		SQLiteDatabase db = mOpenHelper.getWritableDatabase();

		long rowId = db.insert(TABLE_NAME, Constants.JID, values);

		if (rowId < 0) {
			throw new SQLException("Failed to insert row into " + url);
		}

		Uri noteUri = ContentUris.withAppendedId(CONTENT_URI, rowId);
		getContext().getContentResolver().notifyChange(noteUri, null);
		return noteUri;
	}

	@Override
	public boolean onCreate() {
		mOpenHelper = new DatabaseHelper(getContext());
		return true;
	}

	@Override
	public Cursor query(Uri url, String[] projectionIn, String selection,
			String[] selectionArgs, String sortOrder) {

		SQLiteQueryBuilder qBuilder = new SQLiteQueryBuilder();
		int match = URI_MATCHER.match(url);

		switch (match) {

		case CONTACTS:
			qBuilder.setTables(TABLE_NAME);
			break;

		case CONTACT_ID:
			qBuilder.setTables(TABLE_NAME);
			qBuilder.appendWhere("_id=");
			qBuilder.appendWhere(url.getPathSegments().get(1));
			break;

		default:
			throw new IllegalArgumentException("Unknown URL " + url);
		}

		String orderBy;
		if (TextUtils.isEmpty(sortOrder)) {
			orderBy = Constants.DEFAULT_SORT_ORDER;
		} else {
			orderBy = sortOrder;
		}

		SQLiteDatabase db = mOpenHelper.getReadableDatabase();
		Cursor ret = qBuilder.query(db, projectionIn, selection, selectionArgs,
				null, null, orderBy);

		if (ret == null) {
			if (LogConstants.LOG_INFO) {
				Log.i(TAG, "ChatProvider.query: failed");
			}
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
			count = db.update(TABLE_NAME, values, where, null);
			break;
		case CONTACT_ID:
			String segment = url.getPathSegments().get(1);
			rowId = Long.parseLong(segment);
			count = db.update(TABLE_NAME, values, "_id=" + rowId, null);
			break;
		default:
			throw new UnsupportedOperationException("Cannot update URL: " + url);
		}

		if (LogConstants.LOG_INFO) {
			Log.i(TAG, "*** notifyChange() rowId: " + rowId + " url " + url);
		}

		getContext().getContentResolver().notifyChange(url, null);
		return count;

	}

	private static class DatabaseHelper extends SQLiteOpenHelper {

		private static final String DATABASE_NAME = "yaxim.db";
		private static final int DATABASE_VERSION = 4;

		public DatabaseHelper(Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			if (LogConstants.LOG_INFO) {
				Log.i(TAG, "creating new chat table");
			}

			db.execSQL("CREATE TABLE " + TABLE_NAME + " (" + Constants._ID
					+ " INTEGER PRIMARY KEY AUTOINCREMENT," + Constants.JID
					+ " TEXT," + Constants.ALIAS + " TEXT,"
					+ Constants.STATUS_MODE + " INTEGER,"
					+ Constants.STATUS_MESSAGE + " TEXT," + Constants.GROUP
					+ " TEXT);");
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			switch (oldVersion) {
			case 3:
				db.execSQL("UPDATE " + TABLE_NAME + " SET READ=1");
				break;
			default:
				db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
				onCreate(db);
			}
		}
	}

	public static final class Constants implements BaseColumns {

		private Constants() {
		}

		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.yaxim.roster";
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.yaxim.roster";
		public static final String DEFAULT_SORT_ORDER = Constants.STATUS_MODE
				+ " ASC";

		public static final String JID = "jid";
		public static final String ALIAS = "alias";
		public static final String STATUS_MODE = "status_mode";
		public static final String STATUS_MESSAGE = "status_message";
		public static final String GROUP = "group";

		public static ArrayList<String> getRequiredColumns() {
			ArrayList<String> tmpList = new ArrayList<String>();
			tmpList.add(JID);
			tmpList.add(ALIAS);
			tmpList.add(STATUS_MODE);
			tmpList.add(STATUS_MESSAGE);
			tmpList.add(GROUP);
			return tmpList;
		}

	}

}
