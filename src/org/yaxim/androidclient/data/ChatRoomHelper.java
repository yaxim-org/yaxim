package org.yaxim.androidclient.data;

import org.yaxim.androidclient.data.RosterProvider.RosterConstants;
import org.yaxim.androidclient.service.IXMPPMucService;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;
import android.os.RemoteException;

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

	public static void syncDbRooms(final Context ctx) {
		Intent serviceIntent = new Intent(ctx, org.yaxim.androidclient.service.XMPPService.class);
		serviceIntent.setAction("org.yaxim.androidclient.XMPPSERVICE");
		serviceIntent.setData(Uri.parse("?chat"));
		ServiceConnection c = new ServiceConnection() {
			public void onServiceConnected(ComponentName name, IBinder service) {
				IXMPPMucService mucService = IXMPPMucService.Stub.asInterface(service);
				try {
					mucService.syncDbRooms();
				} catch (RemoteException e) {
					e.printStackTrace();
				}
				ctx.unbindService(this);
			}
			public void onServiceDisconnected(ComponentName name) {}
		};
		ctx.bindService(serviceIntent, c, Context.BIND_AUTO_CREATE);
	}

	public static String guessMyNickname(Context ctx, String fallback) {
		// obtain a sorted list of MUC nicknames; manually counting the
		// occurences is a workaround for the missing "GROUP BY nickname"
		Cursor cursor = ctx.getContentResolver().query(RosterProvider.MUCS_URI,
				new String[] { RosterConstants.NICKNAME },
				null, null, RosterConstants.NICKNAME );
		String best = fallback;
		int best_count = 0;
		String current = null;
		int current_count = 0;
		// count the occurences of every nickname in 'current', update 'best' with most-often used one
		while (cursor.moveToNext()) {
			android.util.Log.d("ChatRoomHelper", String.format("guessMyNickname: %s(%d) %s(%d) %s", best, best_count, current, current_count, cursor.getString(0)));
			if (cursor.getString(0).equals(current))
				current_count++;
			else {
				current = cursor.getString(0);
				current_count = 1;
			}
			if (current_count > best_count) {
				best = current;
				best_count = current_count;
			}
		}
		cursor.close();
		return best;
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
