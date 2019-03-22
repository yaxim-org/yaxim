package org.yaxim.androidclient.data;

import org.yaxim.androidclient.FlavorConfig;
import org.yaxim.androidclient.R;
import org.yaxim.androidclient.exceptions.YaximXMPPAdressMalformedException;
import org.yaxim.androidclient.util.PreferenceConstants;
import org.yaxim.androidclient.util.StatusMode;
import org.yaxim.androidclient.util.XMPPHelper;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import java.net.URLEncoder;
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
				PreferenceConstants.STATUS_DNDSILENT,
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
	public String screenName;
	public boolean jid_configured;
	public boolean require_ssl;

	public String statusMode;
	public boolean statusDndSilent;
	public StatusMode smartAwayMode;
	public String statusMessage;
	public String[] statusMessageHistory;

	public String mucDomain = null; // used in AutoCompleteJidEdit, null fallbacks to first static entry

	public boolean smackdebug;
    public String theme;
    public String chatFontSize;
    public boolean showOffline;
	public boolean enableGroups;

	public boolean reconnect_required = false;
	public boolean rosterreset_required = false;
	public boolean presence_required = false;
	public boolean nickchange_required = false;

	/// this stores tuples of (JID, valid_until) or (token, valid_until) for PARS
	private HashMap<String, Long> invitationCodes = new HashMap<String, Long>();

	private final Context ctx;
	private final SharedPreferences prefs;

	public YaximConfiguration(Context _ctx) {
		ctx = _ctx;
		prefs = PreferenceManager.getDefaultSharedPreferences(_ctx);
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
		if (PreferenceConstants.JID.equals(key)) {
			rosterreset_required = true;
			prefs.edit().remove(PreferenceConstants.DOZE_NAG).commit();
		}
		if (RECONNECT_PREFS.contains(key))
			reconnect_required = true;
		if (PRESENCE_PREFS.contains(key))
			presence_required = true;
		if (PreferenceConstants.SCREEN_NAME.equals(key))
			nickchange_required = true;
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

		this.password = prefs.getString(PreferenceConstants.PASSWORD, "");
		this.ressource = prefs
				.getString(PreferenceConstants.RESSOURCE, "yaxim");
		this.port = XMPPHelper.tryToParseInt(prefs.getString(
				PreferenceConstants.PORT, ""),
				-1);

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
		this.statusDndSilent = prefs.getBoolean(PreferenceConstants.STATUS_DNDSILENT, true);
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
			// fix up custom server / port if only one of them is set
			if (customServer.length() > 0 && port == -1)
				port = PreferenceConstants.DEFAULT_PORT_INT;
			else if (customServer.length() == 0 && port != -1)
				customServer = server;
			this.jid_configured = true;
			this.screenName = prefs.getString(PreferenceConstants.SCREEN_NAME, XMPPHelper.capitalizeString(this.userName));
		} catch (YaximXMPPAdressMalformedException e) {
			Log.e(TAG, "Exception in getPreferences(): " + e);
		}
	}

	public SharedPreferences getJidPrefs(String jid) {
		return ctx.getSharedPreferences("notification_" + URLEncoder.encode(jid), Context.MODE_PRIVATE);
	}
	public String getJidString(boolean muc, String pref, String jid, String defValue) {
		if (jid != null) {
			/* try to obtain JID-specific value */
			SharedPreferences jidPrefs = getJidPrefs(jid);
			if (jidPrefs.getBoolean("override", false))
				return jidPrefs.getString(pref, defValue);
		}
		/* fall back to generic value */
		SharedPreferences default_prefs = muc ? getJidPrefs("muc") : prefs;
		return default_prefs.getString(pref, defValue);
	}
	public boolean getJidBoolean(boolean muc, String pref, String jid, boolean defValue) {
		if (jid != null) {
			/* try to obtain JID-specific value */
			SharedPreferences jidPrefs = getJidPrefs(jid);
			if (jidPrefs.getBoolean("override", false))
				return jidPrefs.getBoolean(pref, defValue);
		}
		/* fall back to generic value */
		SharedPreferences default_prefs = muc ? getJidPrefs("muc") : prefs;
		return default_prefs.getBoolean(pref, defValue);
	}

	public boolean needMucNotification(String jid, String nick, String message) {
		// highlight==false --> notify on all messages
		if (getJidBoolean(true, PreferenceConstants.HIGHLIGHT, jid, true) == false)
			return true;
		return message.toLowerCase().contains(nick.toLowerCase());
	}

	public int getTheme() {
		return FlavorConfig.getTheme(theme);
	}

	public synchronized void generateNewResource() {
		prefs.edit().putString(PreferenceConstants.RESSOURCE, XMPPHelper.createResource(ctx)).commit();
	}
	public synchronized boolean storeScreennameIfChanged(String name) {
		if (TextUtils.isEmpty(name))
			name = XMPPHelper.capitalizeString(this.userName);
		if (!name.equals(screenName)) {
			screenName = name;
			prefs.edit().putString(PreferenceConstants.SCREEN_NAME, screenName).commit();
			return true;
		}
		return false;
	}
	public synchronized void storeInstallReferrer(String referrer) {
		prefs.edit().putString(PreferenceConstants.INSTALL_REFERRER, referrer).commit();

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

	public StatusMode getPresenceMode() {
		StatusMode sm = StatusMode.fromString(statusMode);
		if (!statusDndSilent || smartAwayMode == null)
			return sm;
		return (smartAwayMode.compareTo(sm) < 0) ? smartAwayMode : sm;
	}
}
