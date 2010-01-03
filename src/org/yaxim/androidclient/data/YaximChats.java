package org.yaxim.androidclient.data;

import java.util.ArrayList;

import android.net.Uri;
import android.provider.BaseColumns;

public class YaximChats {

	public static final String AUTHORITY = "org.yaxim.androidclient.provider.Chats";
	public static final String TABLE_NAME = "chats";
	public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY
			+ "/" + TABLE_NAME);

	public static final class Chats implements BaseColumns {

		private Chats() {
		}

		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.yaxim.chat";
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.yaxim.chat";
		public static final String DEFAULT_SORT_ORDER = "time DESC";

		public static final String TIME = "time";
		public static final String FROM_JID = "fromJID";
		public static final String TO_JID = "toJID";
		public static final String MESSAGE = "message";
		public static final String HAS_BEEN_READ = "read";

		public static ArrayList<String> getRequiredColumns() {
			ArrayList<String> tmpList = new ArrayList<String>();
			tmpList.add(TIME);
			tmpList.add(FROM_JID);
			tmpList.add(TO_JID);
			tmpList.add(MESSAGE);
			tmpList.add(HAS_BEEN_READ);
			return tmpList;
		}

	}

}
