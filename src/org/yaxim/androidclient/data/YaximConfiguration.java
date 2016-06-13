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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

public class YaximConfiguration implements OnSharedPreferenceChangeListener {

	private static final String TAG = "yaxim.Configuration";

	private static final String GMAIL_SERVER = "talk.google.com";

	private static final long DEFAULT_INVITATION_TIME = 14*24*3600; // two weeks in seconds

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
	public boolean jid_configured;
	public boolean require_ssl;

	public String statusMode;
	public String statusMessage;
	public String[] statusMessageHistory;

	public boolean isLEDNotify;
	public String vibraNotify;
	public Uri notifySound;
	public boolean ticker;
	
	public boolean highlightNickMuc;
	public boolean isLEDNotifyMuc;
	public String vibraNotifyMuc;
	public Uri notifySoundMuc;
	public boolean tickerMuc;

	public boolean smackdebug;
    public String theme;
    public String chatFontSize;
    public boolean showOffline;
	public boolean enableGroups;

    public boolean reconnect_required = false;
    public boolean presence_required = false;

	/// this stores tuples of (JID, valid_until) or (token, valid_until) for PARS
	private HashMap<String, Long> invitationCodes = new HashMap<String, Long>();

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
		this.jid_configured = false;

		this.highlightNickMuc = prefs.getBoolean(PreferenceConstants.HIGHLIGHTMUC, false);
		this.isLEDNotifyMuc = prefs.getBoolean(PreferenceConstants.LEDNOTIFYMUC,
				false);
		this.vibraNotifyMuc = prefs.getString(
				PreferenceConstants.VIBRATIONNOTIFYMUC, "SYSTEM");
		this.notifySoundMuc = Uri.parse(prefs.getString(
				PreferenceConstants.RINGTONENOTIFYMUC, ""));
		this.tickerMuc = prefs.getBoolean(PreferenceConstants.TICKERMUC,
				true);
		
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
		this.statusMessageHistory = prefs.getString(PreferenceConstants.STATUS_MESSAGE_HISTORY, statusMessage).split("\036");
		this.theme = prefs.getString(PreferenceConstants.THEME, "dark");
		this.chatFontSize = prefs.getString("setSizeChat", "18");
		this.showOffline = prefs.getBoolean(PreferenceConstants.SHOW_OFFLINE, false);
		this.enableGroups = prefs.getBoolean(PreferenceConstants.ENABLE_GROUPS, true);

		this.invitationCodes.clear();
		Log.d(TAG, "invitation_codes = " + prefs.getString(PreferenceConstants.INVITATION_CODES, ""));
		try {
			for (String record : prefs.getString(PreferenceConstants.INVITATION_CODES, "").split("\036")) {
				if (record.length() >= 3) {
					// records are JID or token plus timestamp, separated by Unit Separator
					String[] r = record.split("\037");
					long valid = Long.parseLong(r[1]);
					invitationCodes.put(r[0], valid);
				}
			}
		} catch (Exception e) {
			Log.e(TAG, "Exception parsing tokens: " + e);
		}


		try {
			splitAndSetJabberID(XMPPHelper.verifyJabberID(jabberID));
			this.jid_configured = true;
		} catch (YaximXMPPAdressMalformedException e) {
			Log.e(TAG, "Exception in getPreferences(): " + e);
		}
	}

	public boolean needMucNotification(String nick, String message) {
		if (!highlightNickMuc)
			return true;
		return message.toLowerCase().contains(nick.toLowerCase());
	}

	public int getTheme() {
		if (theme.equals("light")) {
			return R.style.YaximLightTheme;
		} else {
			return R.style.YaximDarkTheme;
		}
	}

	private synchronized void storeInvitationCodes() {
		Iterator<String> it = invitationCodes.keySet().iterator();
		// remove all expired codes
		long ts = System.currentTimeMillis()/1000;
		while (it.hasNext()) {
			Long valid = invitationCodes.get(it.next());
			if (valid < ts)
				it.remove();
		}
		String[] records = new String[invitationCodes.size()];
		int i = 0;
		it = invitationCodes.keySet().iterator();
		// create string records with code|timestamp
		while (it.hasNext()) {
			String code = it.next();
			records[i++] = code + "\037" + invitationCodes.get(code);
		}
		// concatenate all records to one string
		String ic_joined = android.text.TextUtils.join("\036", records);
		Log.d(TAG, "invitation_codes = " + ic_joined);
		prefs.edit().putString(PreferenceConstants.INVITATION_CODES, ic_joined).commit();
	}

	public boolean redeemInvitationCode(String code_or_jid) {
		if (invitationCodes.containsKey(code_or_jid) &&
			(invitationCodes.get(code_or_jid) > System.currentTimeMillis()/1000)) {
			invitationCodes.remove(code_or_jid);
			storeInvitationCodes();
			return true;
		} else return false;
	}
	
	public String createInvitationCode(long validity) {
		// create an 80-bit random code, encode as BASE-36
		String code = new java.math.BigInteger(80, new java.security.SecureRandom()).toString(36);
		invitationCodes.put(code, System.currentTimeMillis()/1000 + validity);
		Log.d(TAG, "createInvitationCode: " + code + " for " + validity + "s");
		storeInvitationCodes();
		return code;
	}
	public String createInvitationCode() {
		return createInvitationCode(DEFAULT_INVITATION_TIME);
	}
	
	public void whitelistInvitationJID(String jid) {
		invitationCodes.put(jid, System.currentTimeMillis()/1000 + DEFAULT_INVITATION_TIME);
		storeInvitationCodes();
	}
}
