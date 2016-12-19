package org.yaxim.androidclient.widget;

import org.yaxim.androidclient.R;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.TreeSet;

import android.content.Context;
import android.util.AttributeSet;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.Spannable;
import android.text.style.ForegroundColorSpan;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

public class AutoCompleteJidEdit extends AutoCompleteTextView {
	private String server_main;
	private TreeSet<String> servers;
	private String userpart = null;
	private ArrayAdapter<String> mServerAdapter;
	ForegroundColorSpan span;
	TextWatcher jtw;

	public AutoCompleteJidEdit(Context ctx, AttributeSet attrs) {
		super(ctx, attrs);
		mServerAdapter = new ArrayAdapter<String>(ctx,
				android.R.layout.simple_dropdown_item_1line,
				new ArrayList<String>());
		setAdapter(mServerAdapter);
		span = new ForegroundColorSpan(getCurrentHintTextColor());
		setThreshold(3);
	}

	public void setServerList(int static_elents_id) {
		String[] static_list = getResources().getStringArray(static_elents_id);
		servers = new TreeSet<String>(Arrays.asList(static_list));
		server_main = static_list[0];
	}

	public void setServerList(String first, Collection<String> dyn_elements, int static_elents_id) {
		setServerList(static_elents_id);
		if (dyn_elements != null)
			servers.addAll(dyn_elements);
		if (first != null)
			server_main = first;
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
				e.append("@" + server_main);
				atpos = len;
				AutoCompleteJidEdit.this.setSelection(atpos);
			}
			len = e.length();
			if (auto_appended)
				e.setSpan(span, atpos, len, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
			else if (len > 1) {
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
