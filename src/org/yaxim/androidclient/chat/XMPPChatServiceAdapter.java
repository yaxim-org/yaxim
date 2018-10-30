package org.yaxim.androidclient.chat;

import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;
import org.yaxim.androidclient.service.IXMPPChatService;

public class XMPPChatServiceAdapter {

	private static final String TAG = "yaxim.XMPPCSAdapter";
	private IXMPPChatService xmppServiceStub;
	private String jabberID;

	public XMPPChatServiceAdapter(IXMPPChatService xmppServiceStub,
			String jabberID) {
		Log.i(TAG, "New XMPPChatServiceAdapter construced");
		this.xmppServiceStub = xmppServiceStub;
		this.jabberID = jabberID;
	}

	public void sendMessage(String user, String message, String lmc, long upsert_id) {
		try {
			Log.i(TAG, "Called sendMessage(): " + jabberID + ": " + message);
			xmppServiceStub.sendMessage(user, message, lmc, upsert_id);
		} catch (RemoteException e) {
			Log.e(TAG, "caught RemoteException: " + e.getMessage());
		}
	}
	
	public boolean isServiceAuthenticated() {
		try {
			return xmppServiceStub.isAuthenticated();
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		return false;
	}

	public void clearNotifications(String Jid) {
		try {
			xmppServiceStub.clearNotifications(Jid);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	public void sendFile(Uri path, String user, int flags) {
		try {
			xmppServiceStub.sendFile(path, user, flags);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}
}
