package org.yaxim.androidclient.chat;

import java.util.List;

import android.os.RemoteException;
import android.util.Log;
import org.yaxim.androidclient.service.IXMPPMucService;
import org.yaxim.androidclient.service.ParcelablePresence;

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
	
	public List<ParcelablePresence> getUserList() {
		try {
			return xmppServiceStub.getUserList(this.jabberID);
		} catch (RemoteException e) {
			return null;
		}
	}

}
