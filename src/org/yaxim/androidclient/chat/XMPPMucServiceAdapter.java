package org.yaxim.androidclient.chat;

import android.os.RemoteException;
import android.util.Log;
import org.yaxim.androidclient.service.IXMPPChatService;
import org.yaxim.androidclient.service.IXMPPMucService;

public class XMPPMucServiceAdapter {

	private static final String TAG = "yaxim.XMPPCSAdapter";
	private IXMPPMucService xmppServiceStub;
	private String jabberID;

	public XMPPMucServiceAdapter(IXMPPMucService xmppServiceStub,
			String jabberID) {
		Log.i(TAG, "New XMPPMucServiceAdapter construced");
		this.xmppServiceStub = xmppServiceStub;
		this.jabberID = jabberID;
	}
	
	public boolean isRoom() {
		try {
			return xmppServiceStub.isRoom(jabberID);
		} catch (Exception e) {
			Log.e(TAG, "Caught Exception: "+e.getMessage());
			return false;
		}
	}
	
	public String[] getUserList() {
		try {
			return xmppServiceStub.getUserList(this.jabberID);
		} catch (RemoteException e) {
			return new String[]{};
		}
	}

}
