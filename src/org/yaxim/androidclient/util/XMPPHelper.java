package org.yaxim.androidclient.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.security.SecureRandom;

import org.yaxim.androidclient.exceptions.YaximXMPPAdressMalformedException;

import android.content.Context;
import android.text.Editable;
import android.util.TypedValue;

import gnu.inet.encoding.Stringprep;
import gnu.inet.encoding.StringprepException;

public class XMPPHelper {

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

	public static String capitalizeString(String original) {
		return (original.length() == 0) ? original :
			original.substring(0, 1).toUpperCase() + original.substring(1);
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
}
