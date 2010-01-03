package org.yaxim.androidclient.data;

import org.yaxim.androidclient.data.YaximChats.Chats;
import org.yaxim.androidclient.util.LogConstants;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

public class ChatProvider extends ContentProvider {

	public static final Uri CONTENT_URI = Uri
			.parse("content://org.yaxim.chat_item/chatitem");

	private static final UriMatcher URI_MATCHER = new UriMatcher(
			UriMatcher.NO_MATCH);

	private static final int MESSAGES = 1;
	private static final int MESSAGES_ID = 2;

	static {
		URI_MATCHER.addURI("org.yaxim.chat_item", "chatitem", MESSAGES);
		URI_MATCHER.addURI("org.yaxim.chat_item", "chatitem/#", MESSAGES_ID);
	}

	private static final String TAG = "ChatProvider";

	private SQLiteOpenHelper mOpenHelper;

	public ChatProvider() {
	}

	@Override
	public int delete(Uri url, String where, String[] whereArgs) {
		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
		int count;
		long rowId = 0;
		switch (URI_MATCHER.match(url)) {

		case MESSAGES:
			count = db.delete(YaximChats.TABLE_NAME, where, whereArgs);
			break;
		case MESSAGES_ID:
			String segment = url.getPathSegments().get(1);
			rowId = Long.parseLong(segment);

			if (TextUtils.isEmpty(where)) {
				where = "_id=" + segment;
			} else {
				where = "_id=" + segment + " AND (" + where + ")";
			}

			count = db.delete(YaximChats.TABLE_NAME, where, whereArgs);
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
		case MESSAGES:
			return Chats.CONTENT_TYPE;
		case MESSAGES_ID:
			return Chats.CONTENT_ITEM_TYPE;
		default:
			throw new IllegalArgumentException("Unknown URL");
		}
	}

	@Override
	public Uri insert(Uri url, ContentValues initialValues) {
		if (URI_MATCHER.match(url) != MESSAGES) {
			throw new IllegalArgumentException("Cannot insert into URL: " + url);
		}

		ContentValues values = (initialValues != null) ? new ContentValues(
				initialValues) : new ContentValues();

		SQLiteDatabase db = mOpenHelper.getWritableDatabase();

		long rowId = db.insert(YaximChats.TABLE_NAME, Chats.TIME, values);

		if (rowId < 0) {
			throw new SQLException("Failed to insert row into " + url);
		}

		return null;
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
		case MESSAGES:
			qBuilder.setTables(YaximChats.TABLE_NAME);
			break;
		case MESSAGES_ID:
			qBuilder.setTables(YaximChats.TABLE_NAME);
			qBuilder.appendWhere("_id=");
			qBuilder.appendWhere(url.getPathSegments().get(1));
			break;
		default:
			throw new IllegalArgumentException("Unknown URL " + url);
		}

		String orderBy;
		if (TextUtils.isEmpty(sortOrder)) {
			orderBy = Chats.DEFAULT_SORT_ORDER;
		} else {
			orderBy = sortOrder;
		}

		SQLiteDatabase db = mOpenHelper.getReadableDatabase();
		Cursor ret = qBuilder.query(db, projectionIn, selection, selectionArgs,
				null, null, orderBy);

		if (ret == null) {
			if (LogConstants.LOG_INFO)
				Log.i(TAG, "ChatProvider.query: failed");
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
		case MESSAGES_ID:
			String segment = url.getPathSegments().get(1);
			rowId = Long.parseLong(segment);
			count = db.update(YaximChats.TABLE_NAME, values, "_id=" + rowId,
					null);
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
		private static final int DATABASE_VERSION = 1;

		public DatabaseHelper(Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL("CREATE TABLE " + YaximChats.TABLE_NAME + " ("
					+ Chats._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
					+ Chats.TIME + " TIMESTAMP," + Chats.FROM_JID + " TEXT,"
					+ Chats.TO_JID + "toJID TEXT," + Chats.MESSAGE + " TEXT,"
					+ Chats.HAS_BEEN_READ + " BOOLEAN);");
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			db.execSQL("DROP TABLE IF EXISTS " + YaximChats.TABLE_NAME);
			onCreate(db);
		}

	}

}
