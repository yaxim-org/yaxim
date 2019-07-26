package org.yaxim.androidclient.preferences;

import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceScreen;
import android.support.v7.app.ActionBar;
import android.text.TextUtils;

import org.yaxim.androidclient.R;
import org.yaxim.androidclient.YaximApplication;
import org.yaxim.androidclient.data.YaximConfiguration;

import java.net.URLEncoder;

public class NotificationPrefs extends AppCompatPreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
	private ActionBar actionBar;
	YaximConfiguration mConfig;
	private boolean isMuc = false;
	private String jid = null;
	private String name = null;
	private boolean has_changed = false;

	public void onCreate(Bundle savedInstanceState) {
		setTheme(YaximApplication.getConfig().getTheme());
		super.onCreate(savedInstanceState);
		mConfig = org.yaxim.androidclient.YaximApplication.getConfig();

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

		mConfig.getJidPrefs(jid).registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
	}

	/* only set channel on actual notification, otherwise user can't change ringtone any more :(
	@Override
	protected void onStop() {
		super.onStop();
		if (!has_changed)
			return;
		has_changed = false;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			if (!TextUtils.isEmpty(jid))
				getSystemService(NotificationManager.class).deleteNotificationChannel("msg_" + jid);
			if (mConfig.getJidOverride(isMuc, jid)) {
				getSystemService(NotificationManager.class).createNotificationChannel(
						mConfig.createNotificationChannelFor(isMuc, jid, name));
			}
		}
	}
	*/

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

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
		has_changed = true;
	}
}
