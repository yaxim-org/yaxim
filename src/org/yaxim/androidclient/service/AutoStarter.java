package org.yaxim.androidclient.service;

import org.yaxim.androidclient.data.YaximConfiguration;

import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.preference.PreferenceManager;
import android.util.Log;


public class AutoStarter extends BroadcastReceiver {
	static final String TAG = "AutoStarter";

	@Override
	public void onReceive(Context context, Intent intent) {
		// Received intent only when the system boot is completed
		Log.d(TAG, "onReceive");

		YaximConfiguration config = new YaximConfiguration(PreferenceManager
				.getDefaultSharedPreferences(context));

		if (config.bootstart) {
			Log.d(TAG, "start service");
			Intent xmppServiceIntent = new Intent(context, XMPPService.class);
			xmppServiceIntent.setAction("de.hdmstuttgart.yaxim.XMPPSERVICE");
			xmppServiceIntent.putExtra("autostart", true);
			context.startService(xmppServiceIntent);
		}
	}

}

