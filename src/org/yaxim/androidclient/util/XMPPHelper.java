package org.yaxim.androidclient.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.security.SecureRandom;

import org.yaxim.androidclient.data.YaximConfiguration;
import org.yaxim.androidclient.exceptions.YaximXMPPAdressMalformedException;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.text.Editable;
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
	public static final Pattern PHONE = Pattern.compile(	// sdd = space, dot, or dash
			"(\\+[0-9]+[\\- \\.]*)?"        // +<digits><sdd>*
			+ "(\\([0-9]+\\)[\\- \\.]*)?"   // (<digits>)<sdd>*
			+ "([0-9][0-9\\- \\.]+[0-9])"); // <digit><digit|sdd>+<digit>
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
						+ "\\;\\/\\?\\@\\&\\=\\#\\~\\-\\.\\+\\!\\*\\'\\,\\_])"
						+ "|(?:\\%[a-fA-F0-9]{2}))+"
						+ "(\\?[\\p{Alnum}=;&]+)?");

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
	public static String quoteString(String original) {
		return "> " + original.replace("\n", "\n> ") + "\n";
	}

	public static String capitalizeString(String original) {
		return (original.length() == 0) ? original :
			original.substring(0, 1).toUpperCase() + original.substring(1);
	}

	public static float getEmojiScalingFactor(String message, int length_threshold) {
		int offset = 0, len = message.length();
		int count = 0;
		while (offset < len) {
			int cp = message.codePointAt(offset);
			switch (Character.getType(cp)) {
				// if Android doesn't know them yet:
				case Character.UNASSIGNED:
				// all smileys should be in here:
				case Character.OTHER_SYMBOL:
					count++;
					break;
				// ignore spacing and combining characters:
				case Character.SPACE_SEPARATOR:
				case Character.FORMAT:
				case Character.NON_SPACING_MARK:
					if (cp == 0x200d && count > 0) count--; // ZWJ = discount one emoji for length purposes
					break;
				default:
					return 1.f;
			}
			offset += Character.charCount(cp);
			// we do not want to have too long messages
			if (length_threshold > 0 && count > length_threshold)
				return 1.f;
		}
		if (count <= 0) // only whitespace encountered
			return 1.f;
		return 18f/(2+count);
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
			sb.append("?preauth=").append(token);
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
}
