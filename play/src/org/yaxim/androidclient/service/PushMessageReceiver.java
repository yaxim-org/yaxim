package org.yaxim.androidclient.service;

import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.yaxim.androidclient.YaximApplication;

public class PushMessageReceiver extends FirebaseMessagingService {
	private final static String TAG = "yaxim.PushMessageRcvr";

	private void startServiceIfNeeded(String action) {
		if (!YaximApplication.getConfig().autoConnect) {
			Log.d(TAG, "not starting, auto-connect disabled.");
			return;
		}
		Intent xmppServiceIntent = new Intent(this, XMPPService.class);
		if (!TextUtils.isEmpty(action))
			xmppServiceIntent.setAction(action);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			startForegroundService(xmppServiceIntent);
		} else
			startService(xmppServiceIntent);
	}

	@Override
	public void onMessageReceived(RemoteMessage remoteMessage) {
		Log.d(TAG, "Received push message!");
		startServiceIfNeeded(null);
	}

	@Override
	public void onNewToken(String s) {
		Log.d(TAG, "Received token update: " + s);
		startServiceIfNeeded("newtoken");
	}
}
