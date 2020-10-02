package org.yaxim.androidclient.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

import com.android.installreferrer.api.InstallReferrerClient;
import com.android.installreferrer.api.InstallReferrerClient.InstallReferrerResponse;
import com.android.installreferrer.api.InstallReferrerStateListener;
import com.android.installreferrer.api.ReferrerDetails;

import org.yaxim.androidclient.MainWindow;
import org.yaxim.androidclient.data.YaximConfiguration;
import org.yaxim.androidclient.util.XMPPHelper;

import java.net.URLDecoder;

public class InstallReferrerReceiver extends BroadcastReceiver {
	static final String TAG = "yaxim.InstallReceiver";
	static boolean receivedReferrer = false;

	static synchronized void startActivity(Context context, Uri reference) {
		if (receivedReferrer)
			return;
		receivedReferrer = true;
		Log.i(TAG, "Referrer: " + reference);
		Intent ref_intent = new Intent(context, MainWindow.class)
				.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
				.setAction(Intent.ACTION_VIEW)
				.setData(reference);
		context.startActivity(ref_intent);
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.d(TAG, "onReceive " + intent);
		try {
			YaximConfiguration config = new YaximConfiguration(context);
			String ref = URLDecoder.decode(intent.getStringExtra("referrer"), "UTF-8");
			//config.storeInstallReferrer(ref);
			Log.w(TAG, "Ignoring legacy Referrer: " + ref);
			//startActivity(context, Uri.parse(ref));
		} catch (Exception e) {
			Log.e(TAG, "Error in handling referrer: " + e.getLocalizedMessage());
			e.printStackTrace();
		}
	}

	public static void queryInstallReferrerLibrary(final Context context) {
		Log.d(TAG, "Querying InstallReferrer");
		final InstallReferrerClient referrerClient = InstallReferrerClient.newBuilder(context).build();
		referrerClient.startConnection(new InstallReferrerStateListener() {
			@Override
			public void onInstallReferrerSetupFinished(int responseCode) {
				Log.d(TAG, "InstallReferrerSetup: " + responseCode);
				switch (responseCode) {
					case InstallReferrerResponse.OK:
						// Connection established.
						try {
							ReferrerDetails response = referrerClient.getInstallReferrer();
							String referrerUrl = response.getInstallReferrer();
							Log.d(TAG, "InstallReferrerSetup: " + referrerUrl);
							//long referrerClickTime = response.getReferrerClickTimestampSeconds();
							//long appInstallTime = response.getInstallBeginTimestampSeconds();
							//boolean instantExperienceLaunched = response.getGooglePlayInstantParam();
							Uri ref = XMPPHelper.transmogrifyXmppUriHelper(Uri.parse(referrerUrl));
							if (ref != null)
								startActivity(context, ref);
						} catch (RemoteException e) {
							e.printStackTrace();
						}
						break;
					case InstallReferrerResponse.FEATURE_NOT_SUPPORTED:
						// API not available on the current Play Store app.
						break;
					case InstallReferrerResponse.SERVICE_UNAVAILABLE:
						// Connection couldn't be established.
						break;
				}
			}

			@Override
			public void onInstallReferrerServiceDisconnected() {
				// Try to restart the connection on the next request to
				// Google Play by calling the startConnection() method.
			}
		});
	}
}
