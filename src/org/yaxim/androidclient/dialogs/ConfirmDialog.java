package org.yaxim.androidclient.dialogs;

import android.support.v7.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

import org.yaxim.androidclient.data.ChatRoomHelper;
import org.yaxim.androidclient.R;

public class ConfirmDialog {

	public static void show(Context context, int title_id,
			String message, final String jid, final Ok ok) {
		new AlertDialog.Builder(context)
			.setTitle(title_id)
			.setMessage(message)
			.setPositiveButton(android.R.string.yes,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							ok.ok(jid);
						}
					})
			.setNegativeButton(android.R.string.no, null)
			.create().show();
	}

	public static void showMucLeave(final Context context, final String jid) {
		show(context, R.string.roster_contextmenu_muc_leave,
				context.getString(R.string.muc_leave_question, jid), jid,
				new ConfirmDialog.Ok() {
					@Override
					public void ok(final String jid) {
						ChatRoomHelper.leaveRoom(context, jid);
						ChatRoomHelper.syncDbRooms(context);
					}
				});
	}

	public interface Ok {
		public void ok(final String jid);
	}
}
