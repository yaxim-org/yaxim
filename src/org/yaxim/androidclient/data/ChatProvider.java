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

public class ChatProvider extends ContentProvider {

	public static final String AUTHORITY = "org.yaxim.androidclient.provider.Chats";
	public static final String TABLE_NAME = "chats";
	public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY
			+ "/" + TABLE_NAME);

	private static final UriMatcher URI_MATCHER = new UriMatcher(
			UriMatcher.NO_MATCH);

	private static final int MESSAGES = 1;
	private static final int MESSAGE_ID = 2;

	static {
		URI_MATCHER.addURI(AUTHORITY, "chats", MESSAGES);
		URI_MATCHER.addURI(AUTHORITY, "chats/#", MESSAGE_ID);
	}

	private static final String TAG = "yaxim.ChatProvider";

	private SQLiteOpenHelper mOpenHelper;

	public ChatProvider() {
	}

	@Override
	public int delete(Uri url, String where, String[] whereArgs) {
		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
		int count;
		switch (URI_MATCHER.match(url)) {

		case MESSAGES:
			count = db.delete(TABLE_NAME, where, whereArgs);
			break;
		case MESSAGE_ID:
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
		case MESSAGES:
			return ChatConstants.CONTENT_TYPE;
		case MESSAGE_ID:
			return ChatConstants.CONTENT_ITEM_TYPE;
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

		for (String colName : ChatConstants.getRequiredColumns()) {
			if (values.containsKey(colName) == false) {
				throw new IllegalArgumentException("Missing column: " + colName);
			}
		}

		SQLiteDatabase db = mOpenHelper.getWritableDatabase();

		long rowId = db.insert(TABLE_NAME, ChatConstants.DATE, values);

		if (rowId < 0) {
			throw new SQLException("Failed to insert row into " + url);
		}

		Uri noteUri = ContentUris.withAppendedId(CONTENT_URI, rowId);
		getContext().getContentResolver().notifyChange(noteUri, null);
		return noteUri;
	}

	@Override
	public boolean onCreate() {
		mOpenHelper = new ChatDatabaseHelper(getContext());
		return true;
	}

	@Override
	public Cursor query(Uri url, String[] projectionIn, String selection,
			String[] selectionArgs, String sortOrder) {

		SQLiteQueryBuilder qBuilder = new SQLiteQueryBuilder();
		int match = URI_MATCHER.match(url);

		switch (match) {
		case MESSAGES:
			qBuilder.setTables(TABLE_NAME);
			break;
		case MESSAGE_ID:
			qBuilder.setTables(TABLE_NAME);
			qBuilder.appendWhere("_id=");
			qBuilder.appendWhere(url.getPathSegments().get(1));
			break;
		default:
			throw new IllegalArgumentException("Unknown URL " + url);
		}

		String orderBy;
		if (TextUtils.isEmpty(sortOrder)) {
			orderBy = ChatConstants.DEFAULT_SORT_ORDER;
		} else {
			orderBy = sortOrder;
		}

		SQLiteDatabase db = mOpenHelper.getReadableDatabase();
		Cursor ret = qBuilder.query(db, projectionIn, selection, selectionArgs,
				null, null, orderBy);

		if (ret == null) {
			infoLog("ChatProvider.query: failed");
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
		case MESSAGES:
			count = db.update(TABLE_NAME, values, where, whereArgs);
			break;
		case MESSAGE_ID:
			String segment = url.getPathSegments().get(1);
			rowId = Long.parseLong(segment);
			count = db.update(TABLE_NAME, values, "_id=" + rowId, null);
			break;
		default:
			throw new UnsupportedOperationException("Cannot update URL: " + url);
		}

		infoLog("*** notifyChange() rowId: " + rowId + " url " + url);

		getContext().getContentResolver().notifyChange(url, null);
		return count;

	}

	private static void infoLog(String data) {
		if (LogConstants.LOG_INFO) {
			Log.i(TAG, data);
		}
	}

	private static class ChatDatabaseHelper extends SQLiteOpenHelper {

		private static final String DATABASE_NAME = "yaxim.db";
		private static final int DATABASE_VERSION = 6;

		public ChatDatabaseHelper(Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			if (LogConstants.LOG_DEBUG) {
				infoLog("creating new chat table");
			}

			db.execSQL("CREATE TABLE " + TABLE_NAME + " (" 
					+ ChatConstants._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
					+ ChatConstants.DATE + " INTEGER,"
					+ ChatConstants.DIRECTION + " INTEGER,"
					+ ChatConstants.JID + " TEXT,"
					+ ChatConstants.MESSAGE + " TEXT,"
					+ ChatConstants.DELIVERY_STATUS + " INTEGER,"
					+ ChatConstants.PACKET_ID + " TEXT,"
					+ ChatConstants.RESOURCE + " TEXT DEFAULT NULL);");
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			infoLog("onUpgrade: from " + oldVersion + " to " + newVersion);
			switch (oldVersion) {
			case 3:
				db.execSQL("UPDATE " + TABLE_NAME + " SET READ=1");
			case 4:
				db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD " + ChatConstants.PACKET_ID + " TEXT");
			case 5:
				db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD " + ChatConstants.RESOURCE + " TEXT DEFAULT NULL");
				break;
			default:
				db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
				onCreate(db);
			}
		}

	}

	public static final class ChatConstants implements BaseColumns {

		private ChatConstants() {
		}

		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.yaxim.chat";
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.yaxim.chat";
		public static final String DEFAULT_SORT_ORDER = "_id ASC"; // sort by auto-id

		public static final String DATE = "date";
		public static final String DIRECTION = "from_me";
		public static final String JID = "jid";
		public static final String MESSAGE = "message";
		public static final String DELIVERY_STATUS = "read"; // SQLite can not rename columns, reuse old name
		public static final String PACKET_ID = "pid";
		public static final String RESOURCE = "resource"; // to identify senders in MUCs (among others)

		// boolean mappings
		public static final int INCOMING = 0;
		public static final int OUTGOING = 1;
		public static final int DS_NEW = 0; //< this message has not been sent/displayed yet
		public static final int DS_SENT_OR_READ = 1; //< this message was sent but not yet acked, or it was received and read
		public static final int DS_ACKED = 2; //< this message was XEP-0184 acknowledged
		public static final int DS_FAILED = 3; //< this message was returned as failed

		public static ArrayList<String> getRequiredColumns() {
			ArrayList<String> tmpList = new ArrayList<String>();
			tmpList.add(DATE);
			tmpList.add(DIRECTION);
			tmpList.add(JID);
			tmpList.add(MESSAGE);
			return tmpList;
		}

	}

}
