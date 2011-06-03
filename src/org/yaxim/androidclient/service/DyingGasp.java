package org.yaxim.androidclient.service;

import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.util.Log;


public class DyingGasp extends BroadcastReceiver {
	static final String TAG = "YaximShutdown";

	@Override
	public void onReceive(Context context, Intent intent) {
		// Received intent only when the system boot is completed
		Log.d(TAG, "onReceive");

			Log.d(TAG, "stop service");
			Intent xmppServiceIntent = new Intent(context, XMPPService.class);
			xmppServiceIntent.setAction("de.hdmstuttgart.yaxim.XMPPSERVICE");
			context.stopService(xmppServiceIntent);
	}

}

