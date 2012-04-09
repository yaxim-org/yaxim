package org.yaxim.androidclient.preferences;

import org.yaxim.androidclient.YaximApplication;
import org.yaxim.androidclient.exceptions.YaximXMPPAdressMalformedException;
import org.yaxim.androidclient.util.PreferenceConstants;
import org.yaxim.androidclient.util.XMPPHelper;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;

import org.yaxim.androidclient.R;

public class AccountPrefs extends PreferenceActivity {

	private SharedPreferences sharedPreference;

	private static int prioIntValue = 0;

	private EditTextPreference prefPrio;
	private EditTextPreference prefAccountID;
	private int themedTextColor;

	public void onCreate(Bundle savedInstanceState) {
		setTheme(YaximApplication.getConfig(this).getTheme());
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.layout.accountprefs);

		sharedPreference = PreferenceManager.getDefaultSharedPreferences(this);
		themedTextColor = XMPPHelper.getEditTextColor(this);

		this.prefAccountID = (EditTextPreference) findPreference(PreferenceConstants.JID);
		this.prefAccountID.getEditText().addTextChangedListener(
				new TextWatcher() {
					public void afterTextChanged(Editable s) {
						// Nothing
					}

					public void beforeTextChanged(CharSequence s, int start,
							int count, int after) {
						// Nothing
					}

					public void onTextChanged(CharSequence s, int start,
							int before, int count) {

						try {
							XMPPHelper.verifyJabberID(s.toString());
							prefAccountID.getEditText().setTextColor(themedTextColor);
						} catch (YaximXMPPAdressMalformedException e) {
							prefAccountID.getEditText().setTextColor(Color.RED);
						}
					}
				});

		this.prefPrio = (EditTextPreference) findPreference(PreferenceConstants.PRIORITY);
		this.prefPrio
				.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
					public boolean onPreferenceChange(Preference preference,
							Object newValue) {
						try {
							int prioIntValue = Integer.parseInt(newValue
									.toString());
							if (prioIntValue <= 127 && prioIntValue >= -128) {
								sharedPreference.edit().putInt(PreferenceConstants.PRIORITY,
										prioIntValue);
							} else {
								sharedPreference.edit().putInt(PreferenceConstants.PRIORITY, 0);
							}
							return true;

						} catch (NumberFormatException ex) {
							sharedPreference.edit().putInt(PreferenceConstants.PRIORITY, 0);
							return true;
						}

					}
				});

		this.prefPrio.getEditText().addTextChangedListener(new TextWatcher() {
			public void afterTextChanged(Editable s) {
				try {
					prioIntValue = Integer.parseInt(s.toString());
					if (prioIntValue <= 127 && prioIntValue >= -128) {
						prefPrio.getEditText().setTextColor(themedTextColor);
						prefPrio.setPositiveButtonText(android.R.string.ok);
					} else {
						prefPrio.getEditText().setTextColor(Color.RED);
					}
				} catch (NumberFormatException numF) {
					prioIntValue = 0;
					prefPrio.getEditText().setTextColor(Color.RED);
				}

			}

			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
				// Nothing
			}

			public void onTextChanged(CharSequence s, int start, int before,
					int count) {

			}

		});

	}

}
