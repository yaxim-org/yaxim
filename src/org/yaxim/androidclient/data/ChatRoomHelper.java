package org.yaxim.androidclient.data;

import org.yaxim.androidclient.data.RosterProvider.RosterConstants;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
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
	
	public static boolean isRoom(Context ctx, String jid) {
		Cursor cursor = ctx.getContentResolver().query(RosterProvider.MUCS_URI, 
				new String[] {RosterProvider.RosterConstants._ID,
					RosterProvider.RosterConstants.JID}, 
					RosterConstants.JID + " = ?", new String[] { jid.toLowerCase() }, null);
		boolean is_room = (cursor.getCount() == 1);
		cursor.close();
		return is_room;
	}

	public static RoomInfo getRoomInfo(Context ctx, String jid) {
		Cursor cursor = ctx.getContentResolver().query(RosterProvider.MUCS_URI,
				new String[] { RosterConstants._ID, RosterConstants.JID,
					       RosterConstants.NICKNAME, RosterConstants.PASSWORD },
				RosterConstants.JID + " = ?", new String[] { jid.toLowerCase() }, null);
		if (cursor.getCount() == 1) {
			cursor.moveToFirst();
			RoomInfo ri = new RoomInfo(cursor);
			cursor.close();
			return ri;
		} else {
			cursor.close();
			return null;
		}
	}

	public static class RoomInfo {
		public String jid;
		public String nickname;
		public String password;
		//public String status_message;

		RoomInfo(Cursor c) {
			jid = c.getString(c.getColumnIndexOrThrow(RosterConstants.JID));
			nickname = c.getString(c.getColumnIndexOrThrow(RosterConstants.NICKNAME));
			password = c.getString(c.getColumnIndexOrThrow(RosterConstants.PASSWORD));
			//status_message = c.getString(c.getColumnIndexOrThrow(RosterConstants.STATUS_MESSAGE));
		}
	}
}
