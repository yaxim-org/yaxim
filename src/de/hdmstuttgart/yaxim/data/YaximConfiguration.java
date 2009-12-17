package de.hdmstuttgart.yaxim.data;

import de.hdmstuttgart.yaxim.exceptions.YaximXMPPAdressMalformedException;
import de.hdmstuttgart.yaxim.util.PreferenceConstants;
import de.hdmstuttgart.yaxim.util.XMPPHelper;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.util.Log;

public class YaximConfiguration implements OnSharedPreferenceChangeListener {

	private static final String TAG = "YaximConfiguration";

	public String password;
	public String ressource;
	public int port;
	public int priority;
	public boolean bootstart;
	public boolean connStartup;
	public boolean reconnect;
	public String userName;
	public String server;

	public boolean isLEDNotify;
	public boolean isVibraNotify;

	private final SharedPreferences prefs;

	public YaximConfiguration(SharedPreferences _prefs) {
		prefs = _prefs;
		prefs.registerOnSharedPreferenceChangeListener(this);
		loadPrefs(prefs);
	}

	public void finalize() {
		prefs.unregisterOnSharedPreferenceChangeListener(this);
	}

	public void loadPrefs(SharedPreferences prefs) {
		this.isLEDNotify = prefs.getBoolean(PreferenceConstants.LEDNOTIFY,
				false);
		this.isVibraNotify = prefs.getBoolean(
				PreferenceConstants.VIBRATIONNOTIFY, false);
		this.password = prefs.getString(PreferenceConstants.PASSWORD, "");
		this.ressource = prefs.getString(PreferenceConstants.RESSOURCE,
				"Yaxim");
		this.port = XMPPHelper.tryToParseInt(prefs.getString(
				PreferenceConstants.PORT, PreferenceConstants.DEFAULT_PORT),
				PreferenceConstants.DEFAULT_PORT_INT);

		this.priority = validatePriority(XMPPHelper.tryToParseInt(prefs
				.getString("account_prio", "0"), 0));
		
		this.bootstart = prefs.getBoolean(
				PreferenceConstants.BOOTSTART, false);

		this.connStartup = prefs.getBoolean(PreferenceConstants.CONN_STARTUP,
				false);
		this.reconnect = prefs.getBoolean(
				PreferenceConstants.AUTO_RECONNECT, false);

		String jid = prefs.getString(PreferenceConstants.JID, "");

		try {
			XMPPHelper.verifyJabberID(jid);
			splitAndSetJabberID(jid);
		} catch (YaximXMPPAdressMalformedException e) {
			Log.e(TAG, "Exception in getPreferences(): " + e);
		}
	}

	private void splitAndSetJabberID(String jid) {
		String[] res = jid.split("@");
		this.userName = res[0];
		this.server = res[1];
	}

	private int validatePriority(int jabPriority) {
		if (jabPriority > 127)
			return 127;
		else if (jabPriority < -127)
			return -127;
		return jabPriority;
	}

	public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
		Log.i(TAG, "onSharedPreferenceChanged(): " + key);
		loadPrefs(prefs);
	}
}
