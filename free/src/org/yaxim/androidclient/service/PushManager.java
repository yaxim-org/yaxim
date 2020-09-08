package org.yaxim.androidclient.service;

import android.app.Service;
import android.util.Log;

import org.jivesoftware.smack.XMPPConnection;

public class PushManager {
	private final static String TAG = "yaxim.PushManager";

	public PushManager(Service service, XMPPConnection connection) {
		Log.w(TAG, "This version of yaxim doesn't have Push support");
	}

	public boolean enableAccountPush() {
		return false;
	}

	public boolean disableAccountPush() {
		return false;
	}
}
