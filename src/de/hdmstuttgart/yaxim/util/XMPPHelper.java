package de.hdmstuttgart.yaxim.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.text.Editable;

import de.hdmstuttgart.yaxim.exceptions.YaximXMPPAdressMalformedException;

public class XMPPHelper {

	public static void verifyJabberID(String jid)
			throws YaximXMPPAdressMalformedException {
		if (jid != null) {
			Pattern p = Pattern
					.compile("[a-z0-9\\-_\\.]++@[a-z0-9\\-_]++(\\.[a-z0-9\\-_]++)++");
			Matcher m = p.matcher(jid);

			if (!m.matches()) {
				throw new YaximXMPPAdressMalformedException(
						"Configured Jabber-ID is incorrect!");
			}
		} else {
			throw new YaximXMPPAdressMalformedException("Jabber-ID wasn't set!");
		}
	}

	public static void verifyJabberID(Editable jid)
			throws YaximXMPPAdressMalformedException {
		verifyJabberID(jid.toString());
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
}
