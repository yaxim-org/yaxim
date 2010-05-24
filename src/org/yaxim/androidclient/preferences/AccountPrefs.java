package org.yaxim.androidclient.preferences;

import org.yaxim.androidclient.exceptions.YaximXMPPAdressMalformedException;
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
import android.util.Log;
import org.yaxim.androidclient.R;

public class AccountPrefs extends PreferenceActivity {

	private final static String ACCOUNT_JABBERID = "account_jabberID";
	private final static String ACCOUNT_RESSOURCE = "account_resource";
	private final static String ACCOUNT_PRIO = "account_prio";

	private CharSequence newResourceSummary = null;
	private CharSequence newPrioValue = null;
	private CharSequence newSummary = null;

	private SharedPreferences sharedPreference;

	private static int prioIntValue = 0;

	private EditTextPreference prefPrio;
	private EditTextPreference prefAccountID;
	private EditTextPreference prefResource;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.layout.accountprefs);

		setPrefJIDSubtitle(ACCOUNT_JABBERID);
		setPrefRESOURCESubtitle(ACCOUNT_RESSOURCE);
		setPrefPRIOSubtitle(ACCOUNT_PRIO);

		this.prefAccountID = (EditTextPreference) findPreference(ACCOUNT_JABBERID);
		this.prefAccountID
				.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
					public boolean onPreferenceChange(Preference preference,
							Object newValue) {
						Log.e("accountprefs", "jabberid pref changed");
						newSummary = (CharSequence) newValue;
						if (newValue != null) {
							prefAccountID
									.setSummary(getString(R.string.account_summary_prefix)
											+ newSummary);
							return true;
						} else {
							prefAccountID
									.setSummary(getString(R.string.account_jabberID_sum));
							return false;
						}
					}
				});

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
							prefAccountID.getEditText().setTextColor(
									Color.DKGRAY);
						} catch (YaximXMPPAdressMalformedException e) {
							prefAccountID.getEditText().setTextColor(Color.RED);
							prefAccountID
									.setSummary(getString(R.string.account_jabberID_sum));
						}
					}
				});

		this.prefResource = (EditTextPreference) findPreference(ACCOUNT_RESSOURCE);
		this.prefResource
				.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
					public boolean onPreferenceChange(Preference preference,
							Object newValue) {
						newResourceSummary = (CharSequence) newValue;
						if (newValue != null && newResourceSummary.length() > 0) {
							prefResource
									.setSummary(getString(R.string.account_summary_prefix)
											+ newResourceSummary);
							return true;
						} else {
							prefResource
									.setSummary(getString(R.string.account_resource_summ));
							return false;
						}
					}
				});

		this.prefPrio = (EditTextPreference) findPreference(ACCOUNT_PRIO);
		this.prefPrio
				.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
					public boolean onPreferenceChange(Preference preference,
							Object newValue) {
						try {
							int prioIntValue = Integer.parseInt(newValue
									.toString());
							newPrioValue = (CharSequence) newValue;
							if (prioIntValue <= 127 && prioIntValue >= 0) {
								prefPrio
										.setSummary(getString(R.string.account_summary_prefix)
												+ newPrioValue);
								sharedPreference.edit().putInt(ACCOUNT_PRIO,
										prioIntValue);
							} else {
								prefPrio
										.setSummary(getString(R.string.account_summary_prefix)
												+ getString(R.string.account_prio_dialog_errorMessage));
								sharedPreference.edit().putString(ACCOUNT_PRIO, "0");
							}
							return true;

						} catch (NumberFormatException ex) {
							sharedPreference.edit().putString(ACCOUNT_PRIO, "0");
							prefPrio
									.setSummary(getString(R.string.account_summary_prefix)
											+ getString(R.string.account_prio_dialog_errorMessage));
							return true;
						}

					}
				});

		this.prefPrio.getEditText().addTextChangedListener(new TextWatcher() {
			public void afterTextChanged(Editable s) {
				try {
					prioIntValue = Integer.parseInt(s.toString());
					if (prioIntValue <= 127 && prioIntValue >= 0) {
						prefPrio.getEditText().setTextColor(Color.DKGRAY);
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

	private void setPrefJIDSubtitle(String key) {

		sharedPreference = PreferenceManager.getDefaultSharedPreferences(this);

		Preference preference = findPreference(key);

		preference.setSummary(sharedPreference.getString(key,
				getString(R.string.account_settings_defaultsum)));

	}

	private void setPrefRESOURCESubtitle(String key) {

		sharedPreference = PreferenceManager.getDefaultSharedPreferences(this);

		Preference preference = findPreference(key);

		preference.setSummary(sharedPreference.getString(key,
				getString(R.string.account_resource_summ)));

	}

	private void setPrefPRIOSubtitle(String key) {

		sharedPreference = PreferenceManager.getDefaultSharedPreferences(this);

		Preference preference = findPreference(key);
		int prioroty = XMPPHelper.tryToParseInt(sharedPreference.getString(key, "0"), 0);

		if ((prioroty <= 127) && (prioroty >= 0)) {
			preference.setSummary(getString(R.string.account_summary_prefix)
					+ sharedPreference.getString(key,
							getString(R.string.account_prio_summ)));
		} else {
			preference.setSummary(getString(R.string.account_prio_summ));
			sharedPreference.edit().putInt(key, 0);
		}

	}

}
