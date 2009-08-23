package de.hdmstuttgart.yaxim.preferences;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import de.hdmstuttgart.yaxim.R;

public class MainPrefs extends PreferenceActivity{
	  public void onCreate(Bundle savedInstanceState) {
	        super.onCreate(savedInstanceState);
	        addPreferencesFromResource(R.layout.mainprefs);
	    }
}
