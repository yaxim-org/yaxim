package org.yaxim.androidclient;

import android.app.ExpandableListActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

public class GenericExpandableListActivity extends ExpandableListActivity {

	private static final String TAG = "GenericService";

	@Override
	protected void onPause() {
		super.onPause();
		Log.i(TAG, "called onPause()");
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.i(TAG, "called onCreate()");
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
}
