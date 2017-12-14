package org.yaxim.androidclient.util;

import android.content.res.Resources;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

public class MessageStylingHelper {
	public static boolean applyNicknameHighlight(SpannableStringBuilder message, String highlight, Resources.Theme theme, int yaxim_theme) {
		if (TextUtils.isEmpty(highlight))
			return false;

		boolean match = false;
		String msg = message.toString().toLowerCase();
		int pos = 0, hl_len = highlight.length();
		int color = XEP0392Helper.mixNickWithBackground(highlight, theme, yaxim_theme);
		highlight = highlight.toLowerCase();
		while ((pos = msg.indexOf(highlight, pos)) >= 0) {
			message.setSpan(new ForegroundColorSpan(color), pos, pos+hl_len,
				Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			pos = pos+hl_len;
			match = true;
		}
		return match;
	}
}
