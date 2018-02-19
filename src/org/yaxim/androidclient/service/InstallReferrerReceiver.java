package org.yaxim.androidclient.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import org.yaxim.androidclient.MainWindow;
import org.yaxim.androidclient.data.YaximConfiguration;

import java.net.URLDecoder;

public class InstallReferrerReceiver extends BroadcastReceiver {
	static final String TAG = "yaxim.InstallReceiver";


	@Override
	public void onReceive(Context context, Intent intent) {
		Log.d(TAG, "onReceive " + intent);
		try {
			YaximConfiguration config = new YaximConfiguration(context);
			String ref = URLDecoder.decode(intent.getStringExtra("referrer"), "UTF-8");
			config.storeInstallReferrer(ref);
			Log.i(TAG, "Referrer: " + ref);
			Intent ref_intent = new Intent(context, MainWindow.class)
					.setAction(Intent.ACTION_VIEW)
					.setData(Uri.parse(ref));
			context.startActivity(ref_intent);
		} catch (Exception e) {
			Log.e(TAG, "Error in handling referrer: " + e.getLocalizedMessage());
			e.printStackTrace();
		}
	}
}
