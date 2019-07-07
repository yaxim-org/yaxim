package org.yaxim.androidclient.util;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.security.SecureRandom;

import org.yaxim.androidclient.R;
import org.yaxim.androidclient.data.YaximConfiguration;
import org.yaxim.androidclient.exceptions.YaximXMPPAdressMalformedException;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.Editable;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.Build;

import gnu.inet.encoding.Stringprep;
import gnu.inet.encoding.StringprepException;

public class XMPPHelper {

	// shameless copy from android/platform_frameworks_base/blob/master/core/java/android/util/Patterns.java
	public static final String GOOD_IRI_CHAR = "a-zA-Z0-9\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF";
	// original Android pattern, surrounded by word boundaries
	public static final Pattern PHONE = Pattern.compile(	// sdd = space, dot, or dash
			"\\b(\\+[0-9]+[\\- \\.]*)?"        // +<digits><sdd>*
			+ "(\\([0-9]+\\)[\\- \\.]*|[0-9]{3,} ?/[\\- \\.]*)?"   // (<digits>)<sdd>*|<digits>/<sdd>*
			+ "([0-9][0-9\\- \\.]+[0-9])\\b"); // <digit><digit|sdd>+<digit>
	public static final Pattern EMAIL_ADDRESS = Pattern.compile(
			"[a-zA-Z0-9\\+\\.\\_\\%\\-\\+]{1,256}" +
			"\\@" +
			"[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
			"(" +
			"\\." +
			"[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25}" +
			")+");

	// shameless copy from conversations/src/main/java/eu/siacs/conversations/ui/adapter/MessageAdapter.java
	public static final Pattern XMPP_PATTERN = Pattern.compile("xmpp\\:(?:(?:["
						+ GOOD_IRI_CHAR
						+ "\\;\\/\\?\\@\\&\\=\\#\\~\\-\\.\\+\\!\\*\\'\\,\\_%])"
						+ "|(?:\\%[a-fA-F0-9]{2}))+"
						+ "(\\?[\\p{Alnum}=;&]+)?");

	// case-insensitive "XEP-####" surrounded by word boundaries, will extract the number as group 1
	public static final Pattern XEP_PATTERN = Pattern.compile("(?i)\\bXEP-(\\d{4})\\b");

	public static String verifyJabberID(String jid)
			throws YaximXMPPAdressMalformedException {
		try {
			String parts[] = jid.split("@");
			if (parts.length != 2 || parts[0].length() == 0 || parts[1].length() == 0)
				throw new YaximXMPPAdressMalformedException(
						"Configured Jabber-ID is incorrect!");
			StringBuilder sb = new StringBuilder();
			sb.append(Stringprep.nodeprep(parts[0]));
			sb.append("@");
			sb.append(Stringprep.nameprep(parts[1]));
			return sb.toString();
		} catch (StringprepException spe) {
			throw new YaximXMPPAdressMalformedException(spe);
		} catch (NullPointerException e) {
			throw new YaximXMPPAdressMalformedException("Jabber-ID wasn't set!");
		}
	}

	public static String verifyJabberID(Editable jid)
			throws YaximXMPPAdressMalformedException {
		return verifyJabberID(jid.toString());
	}

	public static String createResource(Context ctx) {
		return String.format("%s.%08X",
				ctx.getString(R.string.app_name),
				new java.util.Random().nextInt());
	}

	public static int tryToParseInt(String value, int defVal) {
		int ret;
		try {
			ret = Integer.parseInt(value);
		} catch (NumberFormatException ne) {
			ret = defVal;
		}
		return ret;
	}

	/* Convert a (single- or multi-line) message into a quote */
	public static String quoteStringWithoutQuotes(String original) {
		StringBuilder sb = new StringBuilder();
		String[] lines = original.split("\n");
		for (String s : lines) {
			if (!s.startsWith(">"))
				sb.append("> ").append(s).append('\n');
		}
		return sb.toString();
	}
	/* Convert a (single- or multi-line) message into a quote */
	public static String quoteString(String original) {
		return "> " + original.replace("\n", "\n> ") + "\n";
	}

	public static String capitalizeString(String original) {
		return (original.length() == 0) ? original :
			original.substring(0, 1).toUpperCase() + original.substring(1);
	}

	// a line consisting only of: Emoji (So: Symbol Other), Emoji unknown to Android (Cn: not assigned), ZWJ, Variant-Selectors, whitespace
	static final Pattern LINE_OF_EMOJI = Pattern.compile("[\\p{So}\\p{Cn}\u200D\uFE00-\uFE0F\\s]+");
	static final Pattern ONE_EMOJI = Pattern.compile("[\\p{Cn}\\p{So}](\u200D[\\p{Cn}\\p{So}])*[\uFE00-\uFE0F]?");
	// how many Emoji do we need before falling back to normal
	static final int LENGTH_THRESHOLD = 12;

	public static float getEmojiScalingFactorRE(String message, float max_scale) {
		if (!LINE_OF_EMOJI.matcher(message).matches())
			return 1.f;
		int count = 0;
		Matcher m = ONE_EMOJI.matcher(message);
		while (m.find()) {
			count++;
			if (count > LENGTH_THRESHOLD)
				return 1.f;
		}
		return (count > 0) ? max_scale*3.f/(2+count) : 1.f;
	}

	public static int getEditTextColor(Context ctx) {
		TypedValue tv = new TypedValue();
		boolean found = ctx.getTheme().resolveAttribute(android.R.attr.editTextColor, tv, true);
		if (found) {
			// SDK 11+
			return ctx.getResources().getColor(tv.resourceId);
		} else {
			// SDK < 11
			return ctx.getResources().getColor(android.R.color.primary_text_light);
		}
	}

	private static final String PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456780+-/#$!?";
	private static final int PASSWORD_LENGTH = 12;
	public static String securePassword() {
		SecureRandom r = new SecureRandom();
		StringBuilder sb = new StringBuilder();
		for (int i = 0 ; i < PASSWORD_LENGTH; i++)
			sb.append(PASSWORD_CHARS.charAt(r.nextInt(PASSWORD_CHARS.length() - 1)));
		return sb.toString();
	}

	public static final String MATRIX_BRIDGE = "bridge.xmpp.matrix.org";
	static final Pattern JID2MXID = Pattern.compile("^(#?)([^_#]*)[_#](.*)@" + MATRIX_BRIDGE);
	static final Pattern MXID2JID = Pattern.compile("^([@#])([^:]*):(.*)");

	public static String jid2mxid(String jid) {
		if (jid.equals(MATRIX_BRIDGE))
			return "The Matrix";
		Matcher m = JID2MXID.matcher(jid);
		if (m.find()) {
			String prefix = m.group(1).length() == 0 ? "@" : m.group(1);
			return prefix + m.group(2) + ":" + m.group(3);
		} else
			return jid;
	}

	public static String mxid2jid(String mxid) {
		Matcher m = MXID2JID.matcher(mxid);
		if (m.find()) {
			if (m.group(1).equals("@"))
				return m.group(2) + "_" + m.group(3) + "@" + MATRIX_BRIDGE;
			else
				return "#" + m.group(2) + "#" + m.group(3) + "@" + MATRIX_BRIDGE;
		} else
			return mxid;
	}


	// WARNING: This is not secure! This method is supposed to create a nice-
	// looking URL parameter for JIDs, not to encode all special characters.
	// This is especially important for i18n bare-JIDs which would get
	// mangled into URL-encoded WTF-8
	public static String jid2url(String jid) {
		return jid.replace("%", "%25").replace("#", "%23");
	}

	public static String createInvitationLink(String jid, String token) {
		StringBuilder sb = new StringBuilder();
		sb.append("xmpp:").append(jid2url(jid)).append("?roster");
		if (token != null && token.length() > 0)
			sb.append(";preauth=").append(token);
		return sb.toString();
	}

	public static String createInvitationLinkHTTPS(String jid, String token) {
		StringBuilder sb = new StringBuilder();
		sb.append("https://yax.im/i/#").append(jid2url(jid));
		if (token != null && token.length() > 0)
			sb.append("?;preauth=").append(token);
		return sb.toString();
	}

	public static String createRosterLinkHTTPS(String jid) {
		return "https://yax.im/i/#" + jid2url(jid);
	}

	public static String createMucLinkHTTPS(String jid) {
		return "https://yax.im/i/#" + jid2url(jid) + "?join";
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	public static void setStaticNFC(Activity act, String uri) {
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
			NfcAdapter na = NfcAdapter.getDefaultAdapter(act);
			if (na == null)
				return;
			NdefMessage nm = new NdefMessage(NdefRecord.createUri(uri),
					NdefRecord.createApplicationRecord(act.getPackageName()));
			na.setNdefPushMessage(nm, act);
		}
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	public static void setNFCInvitation(final Activity act, final YaximConfiguration config) {
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
			NfcAdapter na = NfcAdapter.getDefaultAdapter(act);
			if (na == null)
				return;
			na.setNdefPushMessageCallback(new NfcAdapter.CreateNdefMessageCallback() {
				@Override
				public NdefMessage createNdefMessage (NfcEvent event) {
					// expire NFC codes after 30mins
					String uri = createInvitationLink(config.jabberID,
							config.createInvitationCode(30*60));
					return new NdefMessage(NdefRecord.createUri(uri),
							NdefRecord.createApplicationRecord(act.getPackageName()));
				}
			}, act);
		}
	}

	public static void shareLink(Activity act, int title_id, String link) {
		act.startActivity(Intent.createChooser(new Intent(android.content.Intent.ACTION_SEND)
					.setType("text/plain")
					.putExtra(Intent.EXTRA_TEXT,
						link),
				    act.getString(title_id)));
	}

	public static Uri transmogrifyXmppUriHelper(Uri uri) {
		Uri data = uri;
		if ("xmpp".equalsIgnoreCase(data.getScheme())) {
			if (data.isOpaque()) {
				// cheat around android's unwillingness to parse opaque URIs
				data = Uri.parse(data.toString().replaceFirst(":", "://").replace(';', '&'));
			}
		} else if ("yax.im".equalsIgnoreCase(data.getHost()) && !TextUtils.isEmpty(data.getFragment())) {
			// convert URI fragment (after # sign) into xmpp URI
			String jid = data.getFragment().replace(';', '&');
			data = Uri.parse("xmpp://" + jid2url(jid));
		} else if ("conversations.im".equalsIgnoreCase(data.getHost())) {
			try {
				List<String> segments = data.getPathSegments();
				String code = segments.get(0);
				String jid = segments.get(1);
				String token = "";
				if (!jid.contains("@")) {
					jid = segments.get(1) + "@" + segments.remove(2);
				}
				if (segments.size() > 2)
					token = "&preauth=" + segments.get(2);
				if ("i".equalsIgnoreCase(code))
					data = Uri.parse("xmpp://" + jid + "?roster" + token);
				else if ("j".equalsIgnoreCase(code))
					data = Uri.parse("xmpp://" + jid + "?join");
				else return null;
			} catch (Exception e) {
				Log.d("yaxim.XMPPHelper", "Failed to parse URI " + data);
				return null;
			}
		} else
			return null;
		Log.d("yaxim.XMPPHelper", "transmogrifyXmppUri: " + uri + " --> " + data);
		return data;
	}

	public static boolean transmogrifyXmppUri(Intent intent) {
		Uri data = transmogrifyXmppUriHelper(intent.getData());
		if (data != null) {
			intent.setData(data);
			return true;
		}
		return false;
	}
}
