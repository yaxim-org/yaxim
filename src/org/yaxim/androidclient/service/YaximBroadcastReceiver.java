package org.yaxim.androidclient.service;

import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.util.Log;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;


public class YaximBroadcastReceiver extends BroadcastReceiver {
	static final String TAG = "YaximBroadcastReceiver";
	private static int networkType = -1;
	
	public static void initNetworkStatus(Context context) {
		ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
		if (networkInfo != null) {
			Log.d(TAG, "Init: ACTIVE NetworkInfo: "+networkInfo.toString());
			if (networkInfo.isConnected()) {
				networkType = networkInfo.getType();
			}
		}
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		//Log.d(TAG, "onReceive "+intent.getAction());

		if (intent.getAction().equals(Intent.ACTION_SHUTDOWN)) {
			Log.d(TAG, "stop service");
			Intent xmppServiceIntent = new Intent(context, XMPPService.class);
			xmppServiceIntent.setAction("de.hdmstuttgart.yaxim.XMPPSERVICE");
			context.stopService(xmppServiceIntent);
		} else if (intent.getAction().equals(android.net.ConnectivityManager.CONNECTIVITY_ACTION)) {
			ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
			//Log.d(TAG, "ACTIVE NetworkInfo: "+(networkInfo != null ? networkInfo.toString() : "NONE"));
			if (((networkInfo == null) && (networkType != -1)) || ((networkInfo != null) && (networkInfo.isConnected() == false) && (networkInfo.getType() == networkType))) {
				Log.d(TAG, "we got disconnected");
				networkType = -1;
				Intent xmppServiceIntent = new Intent(context, XMPPService.class);
				xmppServiceIntent.setAction("de.hdmstuttgart.yaxim.XMPPSERVICE");
				xmppServiceIntent.putExtra("disconnect", true);
				context.startService(xmppServiceIntent);
			}
			if ((networkInfo != null) && (networkInfo.isConnected() == true) && (networkInfo.getType() != networkType)) {
				Log.d(TAG, "we got connected");
				networkType = networkInfo.getType();
				Intent xmppServiceIntent = new Intent(context, XMPPService.class);
				xmppServiceIntent.setAction("de.hdmstuttgart.yaxim.XMPPSERVICE");
				xmppServiceIntent.putExtra("reconnect", true);
				context.startService(xmppServiceIntent);
			}
		}
	}

}

