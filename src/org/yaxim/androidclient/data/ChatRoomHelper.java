package org.yaxim.androidclient.data;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;

public class ChatRoomHelper {

	public static boolean addRoom(Context ctx, String jid, String password, String nickname) {
		ContentValues cv = new ContentValues();
		cv.put(RosterProvider.RosterConstants.JID, jid.toLowerCase());
		cv.put(RosterProvider.RosterConstants.NICKNAME, nickname);
		cv.put(RosterProvider.RosterConstants.PASSWORD, password);
		Uri ret = ctx.getContentResolver().insert(RosterProvider.MUCS_URI, cv);
		return (ret != null);
	}
	
	public static boolean removeRoom(Context ctx, String jid) {
		int deleted = ctx.getContentResolver().delete(RosterProvider.MUCS_URI, 
				RosterProvider.RosterConstants.JID+" LIKE ?", 
				new String[] {jid.toLowerCase()});
		return (deleted > 0);
	}
	
}
