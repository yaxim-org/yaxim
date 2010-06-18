package org.yaxim.androidclient.data;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yaxim.androidclient.exceptions.YaximXMPPAdressMalformedException;
import org.yaxim.androidclient.util.PreferenceConstants;
import org.yaxim.androidclient.util.XMPPHelper;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.util.Log;

public class YaximConfiguration implements OnSharedPreferenceChangeListener {

	private static final String TAG = "YaximConfiguration";

	private final Pattern domainPattern = Pattern
			.compile("[a-z0-9\\-_]++(\\.[a-z0-9\\-_]++)++");

	public String password;
	public String ressource;
	public int port;
	public int priority;
	public boolean bootstart;
	public boolean autoConnect;
	public boolean autoReconnect;
	public boolean reportCrash;
	public String userName;
	public String server;
	public String customServer;
	public String jabberID;

	public boolean isLEDNotify;
	public boolean isVibraNotify;

	public boolean smackdebug;

	private final SharedPreferences prefs;

	public YaximConfiguration(SharedPreferences _prefs) {
		prefs = _prefs;
		prefs.registerOnSharedPreferenceChangeListener(this);
		loadPrefs(prefs);
	}

	@Override
	protected void finalize() {
		prefs.unregisterOnSharedPreferenceChangeListener(this);
	}

	public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
		Log.i(TAG, "onSharedPreferenceChanged(): " + key);
		loadPrefs(prefs);
	}

	private void splitAndSetJabberID(String jid) {
		String[] res = jid.split("@");
		this.userName = res[0];
		Matcher m = domainPattern.matcher(customServer);
		this.server = (m.matches()) ? customServer : res[1];
		Log.d("Prefs", server);
	}

	private int validatePriority(int jabPriority) {
		if (jabPriority > 127)
			return 127;
		else if (jabPriority < -127)
			return -127;
		return jabPriority;
	}

	private void loadPrefs(SharedPreferences prefs) {
		this.isLEDNotify = prefs.getBoolean(PreferenceConstants.LEDNOTIFY,
				false);
		this.isVibraNotify = prefs.getBoolean(
				PreferenceConstants.VIBRATIONNOTIFY, false);
		this.password = prefs.getString(PreferenceConstants.PASSWORD, "");
		this.ressource = prefs
				.getString(PreferenceConstants.RESSOURCE, "yaxim");
		this.port = XMPPHelper.tryToParseInt(prefs.getString(
				PreferenceConstants.PORT, PreferenceConstants.DEFAULT_PORT),
				PreferenceConstants.DEFAULT_PORT_INT);

		this.priority = validatePriority(XMPPHelper.tryToParseInt(prefs
				.getString("account_prio", "0"), 0));

		this.bootstart = prefs.getBoolean(PreferenceConstants.BOOTSTART, false);

		this.autoConnect = prefs.getBoolean(PreferenceConstants.CONN_STARTUP,
				false);
		this.autoReconnect = prefs.getBoolean(
				PreferenceConstants.AUTO_RECONNECT, false);

		this.smackdebug = prefs.getBoolean(PreferenceConstants.SMACKDEBUG,
				false);
		this.reportCrash = prefs.getBoolean(PreferenceConstants.REPORT_CRASH,
				false);
		this.jabberID = prefs.getString(PreferenceConstants.JID, "");
		this.customServer = prefs.getString(PreferenceConstants.CUSTOM_SERVER,
				"");

		try {
			XMPPHelper.verifyJabberID(jabberID);
			splitAndSetJabberID(jabberID);
		} catch (YaximXMPPAdressMalformedException e) {
			Log.e(TAG, "Exception in getPreferences(): " + e);
		}
	}
}
