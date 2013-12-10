package org.yaxim.androidclient.widget;

import org.yaxim.androidclient.R;

import java.util.ArrayList;

import android.content.Context;
import android.util.AttributeSet;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.Spannable;
import android.text.style.ForegroundColorSpan;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

public class AutoCompleteJidEdit extends AutoCompleteTextView {
	private String[] servers;
	private String userpart = null;
	private ArrayAdapter<String> mServerAdapter;
	ForegroundColorSpan span;
	TextWatcher jtw;

	public AutoCompleteJidEdit(Context ctx, AttributeSet attrs) {
		super(ctx, attrs);
		servers = getResources().getStringArray(R.array.xmpp_servers); // XXX hard-coded array reference
		mServerAdapter = new ArrayAdapter<String>(ctx,
				android.R.layout.simple_dropdown_item_1line,
				new ArrayList<String>(servers.length));
		setAdapter(mServerAdapter);
		span = new ForegroundColorSpan(getCurrentHintTextColor());
		setThreshold(3);
	}

	protected void onAttachedToWindow() {
		if (jtw == null) {
			jtw = new JidTextWatcher();
			addTextChangedListener(jtw);
		}
	}

	boolean auto_appended = false;
	boolean ignore_selection_change = false;

	@Override
	protected void onSelectionChanged(int selStart, int selEnd) {
		if (ignore_selection_change) {
			ignore_selection_change = false;
			return;
		}
		int atpos = getText().toString().indexOf("@");
		if (selStart > atpos+1 || selEnd > atpos+1) {
			auto_appended = false;
			getText().removeSpan(span);
		}
	}

	@Override
	public boolean enoughToFilter() {
		return true;
	}

	private class JidTextWatcher implements TextWatcher {
		public void afterTextChanged(Editable e) {
			String jid = e.toString();
			int len = e.length();
			int atpos = jid.indexOf("@");
			int secondat = (atpos == -1) ? -1 : jid.indexOf("@", atpos + 1);
			if (auto_appended && secondat >= 0) {
				// if the user enters @, we have their @ and our @ -> abort auto_append
				auto_appended = false;
				e.delete(secondat, len);
				e.removeSpan(span);
			} else if (auto_appended && atpos == 0) {
				// remove auto_append when user name is empty
				auto_appended = false;
				e.removeSpan(span);
				e.delete(0, len);
			} else if (!auto_appended && atpos == -1 && len > 0) {
				// if there is a string, but no @, begin auto_append
				auto_appended = true;
				ignore_selection_change = true;
				// append first server from our list
				e.append("@" + servers[0]);
				atpos = len;
				AutoCompleteJidEdit.this.setSelection(atpos);
			}
			len = e.length();
			if (auto_appended)
				e.setSpan(span, atpos, len, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
			else if (len > 0) {
				// populate drop-down list with userpart@domain for all known domains
				String u = jid.split("@")[0];
				if (!u.equals(userpart)) {
					userpart = u;
					mServerAdapter.setNotifyOnChange(false);
					mServerAdapter.clear();
					for (String domain : servers)
						mServerAdapter.add(u + "@" + domain);
					mServerAdapter.notifyDataSetChanged();
					performFiltering("", 0);
					showDropDown();
				}
			}
		}
		public void beforeTextChanged(CharSequence s, int start, int count,
				int after) {
		}

		public void onTextChanged(CharSequence s, int start, int before, int count) {
		}


	}
}
