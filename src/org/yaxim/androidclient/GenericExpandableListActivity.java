package org.yaxim.androidclient;

import org.yaxim.androidclient.data.YaximConfiguration;

import android.app.ExpandableListActivity;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.nullwire.trace.ExceptionHandler;

public class GenericExpandableListActivity extends ExpandableListActivity {

	private static final String TAG = "GenericService";

	protected YaximConfiguration mConfig;

	@Override
	protected void onPause() {
		super.onPause();
		Log.i(TAG, "called onPause()");
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.i(TAG, "called onCreate()");
		mConfig = new YaximConfiguration(PreferenceManager
				.getDefaultSharedPreferences(this));
		registerCrashReporter();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		Log.i(TAG, "called onDestroy()");
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.i(TAG, "called onResume()");
	}

	protected void showToastNotification(int message) {
		Toast tmptoast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
		tmptoast.show();
	}

	private void registerCrashReporter() {
		if (mConfig.reportCrash) {
			ExceptionHandler.register(this, "http://duenndns.de/yaxim-crash/");
		}
	}
}
