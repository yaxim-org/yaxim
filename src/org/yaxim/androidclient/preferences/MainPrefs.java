package org.yaxim.androidclient.preferences;

import android.os.Bundle;
import android.preference.PreferenceActivity;

import org.yaxim.androidclient.R;
import org.yaxim.androidclient.YaximApplication;

public class MainPrefs extends PreferenceActivity{
	public void onCreate(Bundle savedInstanceState) {
		setTheme(YaximApplication.getConfig(this).getPrefTheme());
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.layout.mainprefs);
	}

}
