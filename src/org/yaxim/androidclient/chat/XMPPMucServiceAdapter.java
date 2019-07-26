package org.yaxim.androidclient.chat;

import java.util.List;

import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;
import org.yaxim.androidclient.service.IXMPPMucService;

public class XMPPMucServiceAdapter {

	private static final String TAG = "yaxim.XMPPCSAdapter";
	private IXMPPMucService xmppServiceStub;
	private String jabberID;

	public XMPPMucServiceAdapter(final IXMPPMucService xmppServiceStub,
			String jabberID) {
		Log.i(TAG, "New XMPPMucServiceAdapter construced");
		this.xmppServiceStub = xmppServiceStub;
		this.jabberID = jabberID;
		new Thread() {public void run() {
			Log.d(TAG, "HACK: starting background sync...");
			try {
				xmppServiceStub.syncDbRooms();
			} catch (RemoteException e) {
				e.printStackTrace();
			}
			Log.d(TAG, "HACK: finished background sync...");
			};
		}.start();
	}
	
	public String getMyMucNick() {
		try {
			return xmppServiceStub.getMyMucNick(this.jabberID);
		} catch (RemoteException e) {
			return null;
		}
	}

}
