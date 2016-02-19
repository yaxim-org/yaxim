package org.yaxim.androidclient.data;

import org.yaxim.androidclient.data.ChatProvider.ChatConstants;
import org.yaxim.androidclient.R;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;

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
