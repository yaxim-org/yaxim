package org.yaxim.androidclient.preferences;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

import org.yaxim.androidclient.R;
import org.yaxim.androidclient.util.PreferenceConstants;

public class MainPrefs extends PreferenceActivity{
	  public void onCreate(Bundle savedInstanceState) {
			String theme = PreferenceManager.getDefaultSharedPreferences(this).getString(PreferenceConstants.THEME, "dark");
			if (theme.equals("light")) {
				setTheme(R.style.YaximLightTheme);
			} else {
				setTheme(R.style.YaximDarkTheme);
			}
	        super.onCreate(savedInstanceState);
	        addPreferencesFromResource(R.layout.mainprefs);
	    }
}
