package org.yaxim.androidclient.preferences;

import android.content.Intent;
import android.os.Bundle;

import android.view.MenuItem;
import android.support.v7.app.ActionBar;

import org.yaxim.androidclient.MainWindow;
import org.yaxim.androidclient.R;
import org.yaxim.androidclient.YaximApplication;

public class MainPrefs extends AppCompatPreferenceActivity {
	public void onCreate(Bundle savedInstanceState) {
		setTheme(YaximApplication.getConfig().getTheme());
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.mainprefs);

		ActionBar actionBar = getSupportActionBar();
		actionBar.setHomeButtonEnabled(true);
		actionBar.setDisplayHomeAsUpEnabled(true);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			Intent intent = new Intent(this, MainWindow.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(intent);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

}
