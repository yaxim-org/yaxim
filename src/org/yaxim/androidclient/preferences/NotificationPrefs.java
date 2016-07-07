package org.yaxim.androidclient.preferences;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.support.v7.app.ActionBar;
import android.widget.BaseAdapter;

import org.yaxim.androidclient.R;
import org.yaxim.androidclient.YaximApplication;

import java.net.URLEncoder;

public class NotificationPrefs extends AppCompatPreferenceActivity {
	private ActionBar actionBar;
	private boolean isMuc = false;
	private String jid = null;
	private String name = null;

	public void onCreate(Bundle savedInstanceState) {
		setTheme(YaximApplication.getConfig().getTheme());
		super.onCreate(savedInstanceState);

		Intent intent = getIntent();
		if (intent != null) {
			jid = intent.getStringExtra("jid");
			name = intent.getStringExtra("name");
			if ("muc".equals(intent.getDataString()))
				isMuc = true;
		}
		if (jid != null)
			getPreferenceManager().setSharedPreferencesName("notification_" + URLEncoder.encode(jid));
		else if (isMuc)
			getPreferenceManager().setSharedPreferencesName("notification_muc");

		addPreferencesFromResource(R.xml.notificationprefs);
		PreferenceScreen ps = getPreferenceScreen();
		if (!isMuc)
			ps.removePreference(ps.findPreference("highlight"));
		if (jid == null) {
			ps.removePreference(ps.findPreference("override"));
		} else {
			for (int i = 1; i < ps.getPreferenceCount(); i++)
				ps.getPreference(i).setDependency("override");
		}

		actionBar = getSupportActionBar();
		actionBar.setHomeButtonEnabled(true);
		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setTitle(isMuc ? R.string.preftitle_notify_muc : R.string.preftitle_notify_msg);
		actionBar.setSubtitle(name);
	}

	@Override
	protected void onResume() {
		super.onResume();
	}

	@Override
	public boolean onOptionsItemSelected(android.view.MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				finish();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

}
