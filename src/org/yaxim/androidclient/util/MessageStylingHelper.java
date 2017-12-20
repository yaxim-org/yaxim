package org.yaxim.androidclient.util;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

public class MessageStylingHelper {
	public static boolean applyNicknameHighlight(SpannableStringBuilder message, String highlight, int text_color) {
		if (TextUtils.isEmpty(highlight))
			return false;

		boolean match = false;
		String msg = message.toString().toLowerCase();
		int pos = 0, hl_len = highlight.length();
		int color = XEP0392Helper.mixColors(XEP0392Helper.rgbFromNick(highlight), (text_color^0xffffff), 192);
		highlight = highlight.toLowerCase();
		while ((pos = msg.indexOf(highlight, pos)) >= 0) {
			message.setSpan(new ForegroundColorSpan(color), pos, pos+hl_len,
				Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			pos = pos+hl_len;
			match = true;
		}
		return match;
	}

	public static SpannableStringBuilder formatMessage(String message, String from, String highlight_text,
				int text_color) {
		boolean slash_me = message.startsWith("/me ");
		if (slash_me) {
			message = String.format("\u25CF %s %s", from, message.substring(4));
		}
		// format string
		SpannableStringBuilder body = new SpannableStringBuilder(message);
		if (slash_me)
			body.setSpan(new StyleSpan(Typeface.ITALIC), 2, message.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		if (MessageStylingHelper.applyNicknameHighlight(body, highlight_text, text_color))
			body.setSpan(new StyleSpan(Typeface.BOLD), 0, message.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		eu.siacs.conversations.utils.StylingHelper.format(body, text_color);
		return body;
	}

	public static void applyEmojiScaling(SpannableStringBuilder message, float max_scale) {
		String msg_string = message.toString();
		int start = 0;
		while (start < msg_string.length()) {
			int end = msg_string.indexOf('\n', start);
			if (end == -1)
				end = msg_string.length();
			float line_factor = XMPPHelper.getEmojiScalingFactorRE(msg_string.substring(start, end), max_scale);
			if (line_factor > 1.0f) {
				message.setSpan(new RelativeSizeSpan(line_factor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			}
			start = end+1;
		}
	}
}
