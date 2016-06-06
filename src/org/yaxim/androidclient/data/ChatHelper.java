package org.yaxim.androidclient.data;

import org.yaxim.androidclient.data.ChatProvider.ChatConstants;
import org.yaxim.androidclient.service.IXMPPChatService;
import org.yaxim.androidclient.R;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;

public class ChatHelper {

	public static void markAllAsRead(Context ctx) {
		ContentValues cv = new ContentValues();
		cv.put(ChatConstants.DELIVERY_STATUS, ChatConstants.DS_SENT_OR_READ);
		ctx.getContentResolver().update(ChatProvider.CONTENT_URI, cv,
						ChatConstants.DIRECTION + " = " + ChatConstants.INCOMING + " AND "
						+ ChatConstants.DELIVERY_STATUS + " = " + ChatConstants.DS_NEW, null);
	}
	
	public static void markAsRead(Context ctx, String jid) {
		ContentValues cv = new ContentValues();
		cv.put(ChatConstants.DELIVERY_STATUS, ChatConstants.DS_SENT_OR_READ);
		ctx.getContentResolver().update(ChatProvider.CONTENT_URI, cv,
				ChatProvider.ChatConstants.JID + " = ? AND "
						+ ChatConstants.DIRECTION + " = " + ChatConstants.INCOMING + " AND "
						+ ChatConstants.DELIVERY_STATUS + " = " + ChatConstants.DS_NEW,
				new String[]{jid});
	}

	public static void clearAndRespond(Context ctx, BroadcastReceiver br, String jid, String response) {
		// mark message(s) as read
		markAsRead(ctx, jid);

		// obtain service reference if possible
		Intent serviceIntent = new Intent(ctx, org.yaxim.androidclient.service.XMPPService.class);
		serviceIntent.setAction("org.yaxim.androidclient.XMPPSERVICE");
		IXMPPChatService.Stub cs = (IXMPPChatService.Stub)br.peekService(ctx, serviceIntent);
		if (cs == null) {
			android.util.Log.d("ChatHelper", "Could not peek Service for " + jid);
			return;
		}
		try {
			cs.clearNotifications(jid);
			if (response != null && response.length() > 0)
				cs.sendMessage(jid, response);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	public static void sendMessage(final Context ctx, final String jid, final String message) {
		Intent serviceIntent = new Intent(ctx, org.yaxim.androidclient.service.XMPPService.class);
		serviceIntent.setAction("org.yaxim.androidclient.XMPPSERVICE");
		ServiceConnection c = new ServiceConnection() {
			public void onServiceConnected(ComponentName name, IBinder service) {
				IXMPPChatService chatService = IXMPPChatService.Stub.asInterface(service);
				try {
					if (message != null)
						chatService.sendMessage(jid, message);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
				ctx.unbindService(this);
			}
			public void onServiceDisconnected(ComponentName name) {}
		};
		ctx.bindService(serviceIntent, c, Context.BIND_AUTO_CREATE);
	}

	public static void removeChatHistory(Context ctx, String jid) {
		// TODO: MUC PM history
		ctx.getContentResolver().delete(ChatProvider.CONTENT_URI,
				ChatProvider.ChatConstants.JID + " = ?", new String[] { jid });
	}

	public static void removeChatHistoryDialog(final Context ctx, final String jid, final String userName) {
		new AlertDialog.Builder(ctx)
			.setTitle(R.string.deleteChatHistory_title)
			.setMessage(ctx.getString(R.string.deleteChatHistory_text, userName, jid))
			.setPositiveButton(android.R.string.yes,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							removeChatHistory(ctx, jid);
						}
					})
			.setNegativeButton(android.R.string.no, null)
			.create().show();
	}

}
