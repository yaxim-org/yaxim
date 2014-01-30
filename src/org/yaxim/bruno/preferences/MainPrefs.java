package org.yaxim.bruno.preferences;

import android.os.Bundle;
import android.preference.PreferenceActivity;

import org.yaxim.bruno.R;
import org.yaxim.bruno.YaximApplication;

public class MainPrefs extends PreferenceActivity{
	public void onCreate(Bundle savedInstanceState) {
		setTheme(YaximApplication.getConfig(this).getPrefTheme());
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.layout.mainprefs);
	}

}
