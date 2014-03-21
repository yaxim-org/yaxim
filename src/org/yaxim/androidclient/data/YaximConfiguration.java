package org.yaxim.androidclient.data;

import org.yaxim.androidclient.R;
import org.yaxim.androidclient.exceptions.YaximXMPPAdressMalformedException;
import org.yaxim.androidclient.util.PreferenceConstants;
import org.yaxim.androidclient.util.XMPPHelper;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.Uri;
import android.util.Log;

import java.util.Arrays;
import java.util.HashSet;

public class YaximConfiguration implements OnSharedPreferenceChangeListener {

	private static final String TAG = "yaxim.Configuration";

	private static final String GMAIL_SERVER = "talk.google.com";

	private static final HashSet<String> RECONNECT_PREFS = new HashSet<String>(Arrays.asList(
				PreferenceConstants.JID,
				PreferenceConstants.PASSWORD,
				PreferenceConstants.CUSTOM_SERVER,
				PreferenceConstants.PORT,
				PreferenceConstants.RESSOURCE,
				PreferenceConstants.FOREGROUND,
				PreferenceConstants.REQUIRE_SSL,
				PreferenceConstants.SMACKDEBUG
			));
	private static final HashSet<String> PRESENCE_PREFS = new HashSet<String>(Arrays.asList(
				PreferenceConstants.MESSAGE_CARBONS,
				PreferenceConstants.PRIORITY,
				PreferenceConstants.STATUS_MODE,
				PreferenceConstants.STATUS_MESSAGE
			));

	public String password;
	public String ressource;
	public int port;
	public int priority;
	public boolean foregroundService;
	public boolean autoConnect;
	public boolean messageCarbons;
	public boolean reportCrash;
	public String userName;
	public String server;
	public String customServer;
	public String jabberID;
	public boolean require_ssl;

	public String statusMode;
	public String statusMessage;

	public boolean isLEDNotify;
	public String vibraNotify;
	public Uri notifySound;
	public boolean ticker;

	public boolean smackdebug;
    public String theme;
    public String chatFontSize;
    public boolean showOffline;
	public boolean enableGroups;

    public boolean reconnect_required = false;
    public boolean presence_required = false;

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
		if (RECONNECT_PREFS.contains(key))
			reconnect_required = true;
		if (PRESENCE_PREFS.contains(key))
			presence_required = true;
	}

	private void splitAndSetJabberID(String jid) {
		String[] res = jid.split("@");
		this.userName = res[0];
		this.server = res[1];
		// check for gmail.com and other google hosted jabber accounts
		if ("gmail.com".equals(res[1]) || "googlemail.com".equals(res[1])
				|| GMAIL_SERVER.equals(this.customServer)) {
			// work around for gmail's incompatible jabber implementation:
			// send the whole JID as the login, connect to talk.google.com
			this.userName = jid;
			if (this.customServer.length() == 0)
				this.customServer = GMAIL_SERVER;
		}
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
		this.vibraNotify = prefs.getString(
				PreferenceConstants.VIBRATIONNOTIFY, "SYSTEM");
		this.notifySound = Uri.parse(prefs.getString(
				PreferenceConstants.RINGTONENOTIFY, ""));
		this.ticker = prefs.getBoolean(PreferenceConstants.TICKER,
				true);
		this.password = prefs.getString(PreferenceConstants.PASSWORD, "");
		this.ressource = prefs
				.getString(PreferenceConstants.RESSOURCE, "yaxim");
		this.port = XMPPHelper.tryToParseInt(prefs.getString(
				PreferenceConstants.PORT, PreferenceConstants.DEFAULT_PORT),
				PreferenceConstants.DEFAULT_PORT_INT);

		this.priority = validatePriority(XMPPHelper.tryToParseInt(prefs
				.getString(PreferenceConstants.PRIORITY, "0"), 0));

		this.foregroundService = prefs.getBoolean(PreferenceConstants.FOREGROUND, true);

		this.autoConnect = prefs.getBoolean(PreferenceConstants.CONN_STARTUP,
				false);
		this.messageCarbons = prefs.getBoolean(
				PreferenceConstants.MESSAGE_CARBONS, true);

		this.smackdebug = prefs.getBoolean(PreferenceConstants.SMACKDEBUG,
				false);
		this.reportCrash = prefs.getBoolean(PreferenceConstants.REPORT_CRASH,
				false);
		this.jabberID = prefs.getString(PreferenceConstants.JID, "");
		this.customServer = prefs.getString(PreferenceConstants.CUSTOM_SERVER,
				"");
		this.require_ssl = prefs.getBoolean(PreferenceConstants.REQUIRE_SSL,
				false);
		this.statusMode = prefs.getString(PreferenceConstants.STATUS_MODE, "available");
		this.statusMessage = prefs.getString(PreferenceConstants.STATUS_MESSAGE, "");
        this.theme = prefs.getString(PreferenceConstants.THEME, "dark");
        this.chatFontSize = prefs.getString("setSizeChat", "18");
        this.showOffline = prefs.getBoolean(PreferenceConstants.SHOW_OFFLINE, false);
		this.enableGroups = prefs.getBoolean(PreferenceConstants.ENABLE_GROUPS, true);

		try {
			splitAndSetJabberID(XMPPHelper.verifyJabberID(jabberID));
		} catch (YaximXMPPAdressMalformedException e) {
			Log.e(TAG, "Exception in getPreferences(): " + e);
		}
	}


	public int getTheme() {
		if (theme.equals("light")) {
			return R.style.YaximLightTheme;
		} else {
			return R.style.YaximDarkTheme;
		}
	}
}
