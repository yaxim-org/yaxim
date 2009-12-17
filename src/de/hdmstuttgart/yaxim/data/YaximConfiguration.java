package de.hdmstuttgart.yaxim.data;

import de.hdmstuttgart.yaxim.exceptions.YaximXMPPAdressMalformedException;
import de.hdmstuttgart.yaxim.util.PreferenceConstants;
import de.hdmstuttgart.yaxim.util.XMPPHelper;
import android.content.SharedPreferences;
import android.util.Log;

public class YaximConfiguration {

	private static final String TAG = "YaximConfiguration";

	public final String password;
	public final String ressource;
	public final int port;
	public final int priority;
	public final boolean connStartup;
	public final boolean reconnect;
	public String userName;
	public String server;

	public final boolean isLEDNotify;
	public final boolean isVibraNotify;

	public YaximConfiguration(SharedPreferences prefs) {
		this.isLEDNotify = prefs.getBoolean(PreferenceConstants.LEDNOTIFY,
				false);
		this.isVibraNotify = prefs.getBoolean(
				PreferenceConstants.VIBRATIONNOTIFY, false);
		this.password = prefs.getString(PreferenceConstants.PASSWORD, "");
		this.ressource = prefs.getString(PreferenceConstants.RESSOURCE,
				"yaxim");
		this.port = XMPPHelper.tryToParseInt(prefs.getString(
				PreferenceConstants.PORT, PreferenceConstants.DEFAULT_PORT),
				PreferenceConstants.DEFAULT_PORT_INT);

		this.priority = validatePriority(XMPPHelper.tryToParseInt(prefs
				.getString("account_prio", "0"), 0));
		
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

}
