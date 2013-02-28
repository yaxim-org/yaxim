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
			Log.d(TAG, "called isRoom with jid "+jabberID+" and got response "+xmppServiceStub.isRoom(jabberID));
			return xmppServiceStub.isRoom(jabberID);
		} catch (RemoteException e) {
			Log.e(TAG, "Caught RemoteException: "+e.getMessage());
			return false;
		}
	}

}
