package org.yaxim.androidclient.preferences;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import org.yaxim.androidclient.R;

public class MainPrefs extends PreferenceActivity{
	  public void onCreate(Bundle savedInstanceState) {
	        super.onCreate(savedInstanceState);
	        addPreferencesFromResource(R.layout.mainprefs);
	    }
}
