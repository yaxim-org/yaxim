package org.yaxim.androidclient.preferences;

import android.content.Context;
import android.preference.EditTextPreference;
import android.util.AttributeSet;
import android.view.View;

public class EditTextPreferenceWithValue extends EditTextPreference {
	CharSequence mSummary;

	public EditTextPreferenceWithValue(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public EditTextPreferenceWithValue(Context context) {
		super(context);
	}

	private void setSummaryToText(String text) {
		if (text == null || text.length() == 0)
			setSummary(mSummary);
		else
			setSummary(text);
	}
	@Override
	protected void onBindView(View view) {
		super.onBindView(view);
		mSummary = getSummary();
		setSummaryToText(getText());
	}

	@Override
	public void setText(String text) {
		super.setText(text);
		setSummaryToText(text);
	}

}
