package org.yaxim.androidclient.preferences;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

import org.yaxim.androidclient.R;
import org.yaxim.androidclient.YaximApplication;
import org.yaxim.androidclient.util.PreferenceConstants;

public class MainPrefs extends PreferenceActivity{
	public void onCreate(Bundle savedInstanceState) {
		setTheme(YaximApplication.getConfig(this).getTheme());
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.layout.mainprefs);
	}

}
