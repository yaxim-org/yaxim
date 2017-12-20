/*
 * Copyright (c) 2017, Daniel Gultsch All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package eu.siacs.conversations.utils;

import android.graphics.Color;
import android.graphics.Typeface;
import android.support.annotation.ColorInt;
import android.text.Editable;
import android.text.ParcelableSpan;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.DisplayMetrics;
import android.widget.EditText;

import java.util.Arrays;
import java.util.List;

import eu.siacs.conversations.ui.text.DividerSpan;
import eu.siacs.conversations.ui.text.QuoteSpan;

public class StylingHelper {

	private static List<? extends Class<? extends ParcelableSpan>> SPAN_CLASSES = Arrays.asList(
			StyleSpan.class,
			StrikethroughSpan.class,
			TypefaceSpan.class,
			ForegroundColorSpan.class
	);

	public static void clear(final Editable editable) {
		final int end = editable.length() - 1;
		for (Class<? extends ParcelableSpan> clazz : SPAN_CLASSES) {
			for (ParcelableSpan span : editable.getSpans(0, end, clazz)) {
				editable.removeSpan(span);
			}
		}
	}

	public static void format(final Editable editable, int start, int end, @ColorInt int textColor) {
		for (ImStyleParser.Style style : ImStyleParser.parse(editable,start,end)) {
			final int keywordLength = style.getKeyword().length();
			editable.setSpan(createSpanForStyle(style), style.getStart() + keywordLength, style.getEnd() - keywordLength + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			makeKeywordOpaque(editable, style.getStart(), style.getStart() + keywordLength, textColor);
			makeKeywordOpaque(editable, style.getEnd() - keywordLength + 1, style.getEnd() + 1, textColor);
		}
	}

	public static class MergeSeparator {}

	public static void format(final Editable editable, @ColorInt int textColor) {
		int end = 0;
		MergeSeparator[] spans = editable.getSpans(0, editable.length() - 1, MergeSeparator.class);
		for(MergeSeparator span : spans) {
			format(editable,end,editable.getSpanStart(span),textColor);
			end = editable.getSpanEnd(span);
		}
		format(editable,end,editable.length() -1,textColor);
	}

	private static ParcelableSpan createSpanForStyle(ImStyleParser.Style style) {
		switch (style.getKeyword()) {
			case "*":
				return new StyleSpan(Typeface.BOLD);
			case "_":
				return new StyleSpan(Typeface.ITALIC);
			case "~":
				return new StrikethroughSpan();
			case "`":
			case "```":
				return new TypefaceSpan("monospace");
			default:
				throw new AssertionError("Unknown Style");
		}
	}

	private static void makeKeywordOpaque(final Editable editable, int start, int end, @ColorInt int fallbackTextColor) {
		QuoteSpan[] quoteSpans = editable.getSpans(start, end, QuoteSpan.class);
		@ColorInt int textColor = quoteSpans.length > 0 ? quoteSpans[0].getColor() : fallbackTextColor;
		@ColorInt int keywordColor = transformColor(textColor);
		editable.setSpan(new ForegroundColorSpan(keywordColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
	}

	private static
	@ColorInt
	int transformColor(@ColorInt int c) {
		return Color.argb(Math.round(Color.alpha(c) * 0.6f), Color.red(c), Color.green(c), Color.blue(c));
	}

	public static class MessageEditorStyler implements TextWatcher {

		private final EditText mEditText;

		public MessageEditorStyler(EditText editText) {
			this.mEditText = editText;
		}

		@Override
		public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

		}

		@Override
		public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

		}

		@Override
		public void afterTextChanged(Editable editable) {
			clear(editable);
			format(editable, mEditText.getCurrentTextColor());
		}
	}

	public static boolean handleTextQuotes(SpannableStringBuilder body, int color, DisplayMetrics dm) {
		boolean startsWithQuote = false;
		char previous = '\n';
		int lineStart = -1;
		int lineTextStart = -1;
		int quoteStart = -1;
		for (int i = 0; i <= body.length(); i++) {
			char current = body.length() > i ? body.charAt(i) : '\n';
			char next = body.length() > (i+1) ? body.charAt(i+1) : ' ';
			if (lineStart == -1) {
				if (previous == '\n') {
					if (current == '>' && next == ' ') {
						// Line start with quote
						lineStart = i;
						if (quoteStart == -1) quoteStart = i;
						if (i == 0) startsWithQuote = true;
					} else if (quoteStart >= 0) {
						// Line start without quote, apply spans there
						applyQuoteSpan(body, quoteStart, i - 1, color, dm);
						quoteStart = -1;
					}
				}
			} else {
				// Remove extra spaces between > and first character in the line
				// > character will be removed too
				if (current != ' ' && lineTextStart == -1) {
					lineTextStart = i;
				}
				if (current == '\n') {
					body.delete(lineStart, lineTextStart);
					i -= lineTextStart - lineStart;
					if (i == lineStart) {
						// Avoid empty lines because span over empty line can be hidden
						body.insert(i++, " ");
					}
					lineStart = -1;
					lineTextStart = -1;
				}
			}
			previous = current;
		}
		if (quoteStart >= 0) {
			// Apply spans to finishing open quote
			applyQuoteSpan(body, quoteStart, body.length(), color, dm);
		}
		return startsWithQuote;
	}

	private static int applyQuoteSpan(SpannableStringBuilder body, int start, int end, int color, DisplayMetrics dm) {
		if (start > 1 && !"\n\n".equals(body.subSequence(start - 2, start).toString())) {
			body.insert(start++, "\n");
			body.setSpan(new DividerSpan(false), start - 2, start, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			end++;
		}
		if (end < body.length() - 1 && !"\n\n".equals(body.subSequence(end, end + 2).toString())) {
			body.insert(end, "\n");
			body.setSpan(new DividerSpan(false), end, end + 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		}
		body.setSpan(new QuoteSpan(color, dm), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		return 0;
	}

}
