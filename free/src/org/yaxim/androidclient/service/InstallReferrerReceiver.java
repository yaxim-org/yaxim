package org.yaxim.androidclient.service;

import android.content.Context;
import android.util.Log;

public class InstallReferrerReceiver {
	static final String TAG = "yaxim.InstallReceiver";

	public static void queryInstallReferrerLibrary(final Context context) {
		Log.d(TAG, "No-Op InstallReferrer, see https://gitlab.com/fdroid/fdroidclient/-/issues/1932");
	}
}
