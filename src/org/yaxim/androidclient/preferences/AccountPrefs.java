package org.yaxim.androidclient.preferences;

import org.yaxim.androidclient.YaximApplication;
import org.yaxim.androidclient.dialogs.ChangePasswordDialog;
import org.yaxim.androidclient.exceptions.YaximXMPPAdressMalformedException;
import org.yaxim.androidclient.util.PreferenceConstants;
import org.yaxim.androidclient.util.XMPPHelper;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;

import android.view.MenuItem;
import android.support.v7.app.ActionBar;


import org.yaxim.androidclient.R;

public class AccountPrefs extends AppCompatPreferenceActivity {

	private SharedPreferences sharedPreference;

	private static int prioIntValue = 0;

	private EditTextPreference prefPrio;
	private EditTextPreference prefAccountID;

	public void onCreate(Bundle savedInstanceState) {
		setTheme(YaximApplication.getConfig().getTheme());
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.accountprefs);

		ActionBar actionBar = getSupportActionBar();
		actionBar.setHomeButtonEnabled(true);
		actionBar.setDisplayHomeAsUpEnabled(true);

		sharedPreference = PreferenceManager.getDefaultSharedPreferences(this);

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
							prefAccountID.getEditText().setError(null);
						} catch (YaximXMPPAdressMalformedException e) {
							prefAccountID.getEditText().setError(getString(R.string.Global_JID_malformed));
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
						prefPrio.getEditText().setError(null);
						prefPrio.setPositiveButtonText(android.R.string.ok);
					} else {
						prefPrio.getEditText().setError(getString(R.string.account_prio_error));
					}
				} catch (NumberFormatException numF) {
					prioIntValue = 0;
					prefPrio.getEditText().setError(getString(R.string.account_prio_error));
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

		findPreference(PreferenceConstants.PASSWORD).setOnPreferenceClickListener(new OnPreferenceClickListener() {
			public boolean onPreferenceClick(Preference preference) {
				new ChangePasswordDialog(AccountPrefs.this).show();
				return true;
			}
		});
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			Intent intent = new Intent(this, MainPrefs.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(intent);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

}
