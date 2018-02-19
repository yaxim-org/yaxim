package org.yaxim.androidclient.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.yaxim.androidclient.data.YaximConfiguration;

public class InstallReferrerReceiver extends BroadcastReceiver {
	static final String TAG = "yaxim.InstallReceiver";


	@Override
	public void onReceive(Context context, Intent intent) {
		Log.d(TAG, "onReceive " + intent);
		YaximConfiguration config = new YaximConfiguration(context);
		config.storeInstallReferrer(intent.getDataString());
	}
}
