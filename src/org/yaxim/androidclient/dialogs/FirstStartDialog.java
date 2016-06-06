package org.yaxim.androidclient.dialogs;

import org.yaxim.androidclient.XMPPRosterServiceAdapter;
import org.yaxim.androidclient.exceptions.YaximXMPPAdressMalformedException;
import org.yaxim.androidclient.preferences.AccountPrefs;
import org.yaxim.androidclient.util.PreferenceConstants;
import org.yaxim.androidclient.util.XMPPHelper;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Toast;
import org.yaxim.androidclient.MainWindow;
import org.yaxim.androidclient.R;

public class FirstStartDialog extends AlertDialog implements DialogInterface.OnClickListener,
		CompoundButton.OnCheckedChangeListener, TextWatcher {

	private MainWindow mainWindow;
	private Button mOkButton;
	private EditText mEditJabberID;
	private EditText mEditPassword;
	private EditText mRepeatPassword;
	private CheckBox mCreateAccount;

	public FirstStartDialog(MainWindow mainWindow,
			XMPPRosterServiceAdapter serviceAdapter) {
		super(mainWindow);
		this.mainWindow = mainWindow;

		setTitle(R.string.StartupDialog_Title);

		LayoutInflater inflater = (LayoutInflater) mainWindow
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View group = inflater.inflate(R.layout.firststartdialog, null, false);
		setView(group);

		setButton(BUTTON_POSITIVE, mainWindow.getString(android.R.string.ok), this);
		setButton(BUTTON_NEUTRAL, mainWindow.getString(R.string.StartupDialog_advanced), this);

		mEditJabberID = (EditText) group.findViewById(R.id.StartupDialog_JID_EditTextField);
		mEditPassword = (EditText) group.findViewById(R.id.StartupDialog_PASSWD_EditTextField);
		mRepeatPassword = (EditText) group.findViewById(R.id.startup_password_repeat);
		mCreateAccount = (CheckBox) group.findViewById(R.id.create_account);

		mEditJabberID.addTextChangedListener(this);
		mEditPassword.addTextChangedListener(this);
		mRepeatPassword.addTextChangedListener(this);
		mCreateAccount.setOnCheckedChangeListener(this);
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mOkButton = getButton(BUTTON_POSITIVE);
		mOkButton.setEnabled(false);
	}


	public void onClick(DialogInterface dialog, int which) {
		switch (which) {
		case BUTTON_POSITIVE:
			verifyAndSavePreferences();
			boolean create_account = mCreateAccount.isChecked();
			mainWindow.startConnection(create_account);
			break;
		case BUTTON_NEUTRAL:
			verifyAndSavePreferences();
			mainWindow.startActivity(new Intent(mainWindow, AccountPrefs.class));
			break;
		}
	}

	private void verifyAndSavePreferences() {
		String password = mEditPassword.getText().toString();
		String jabberID;
		try {
			jabberID = XMPPHelper.verifyJabberID(mEditJabberID.getText());
		} catch (YaximXMPPAdressMalformedException e) {
			e.printStackTrace();
			jabberID = mEditJabberID.getText().toString();
		}
		String resource = String.format("%s.%08X",
			mainWindow.getString(R.string.app_name),
			new java.util.Random().nextInt());

		savePreferences(jabberID, password, resource);
		cancel();
	}

	private void updateDialog() {
		boolean is_ok = true;
		// verify jabber ID
		Editable jid = mEditJabberID.getText();
		try {
			XMPPHelper.verifyJabberID(jid);
			//mOkButton.setOnClickListener(this);
			mEditJabberID.setError(null);
		} catch (YaximXMPPAdressMalformedException e) {
			if (jid.length() > 0)
				mEditJabberID.setError(mainWindow.getString(R.string.Global_JID_malformed));
		}
		if (mEditPassword.length() == 0)
			is_ok = false;
		if (mCreateAccount.isChecked()) {
			boolean passwords_match = mEditPassword.getText().toString().equals(
					mRepeatPassword.getText().toString());
			is_ok = is_ok && passwords_match;
			mRepeatPassword.setError((passwords_match || mRepeatPassword.length() == 0) ?
					null : mainWindow.getString(R.string.StartupDialog_error_password));
		}
		mOkButton.setEnabled(is_ok);
	}

	/* CompoundButton.OnCheckedChangeListener for mCreateAccount */
	@Override
	public void onCheckedChanged(CompoundButton btn,boolean isChecked) {
		mRepeatPassword.setVisibility(isChecked? View.VISIBLE : View.GONE);
		if (isChecked) {
			if (mEditPassword.length() == 0 && mRepeatPassword.length() == 0) {
				// create secure random password
				String pw = XMPPHelper.securePassword();
				Toast.makeText(mainWindow, R.string.StartupDialog_created_password, Toast.LENGTH_SHORT).show();
				mEditPassword.setText(pw);
				mRepeatPassword.setText(pw);
			} else
				mRepeatPassword.requestFocus();
		}
		updateDialog();
	}
	public void afterTextChanged(Editable s) {
		updateDialog();
	}

	public void beforeTextChanged(CharSequence s, int start, int count,
			int after) {
	}

	public void onTextChanged(CharSequence s, int start, int before, int count) {
	}

	private void savePreferences(String jabberID, String password, String resource) {
		SharedPreferences sharedPreferences = PreferenceManager
				.getDefaultSharedPreferences(mainWindow);
		Editor editor = sharedPreferences.edit();

		editor.putString(PreferenceConstants.JID, jabberID);
		editor.putString(PreferenceConstants.PASSWORD, password);
		editor.putString(PreferenceConstants.RESSOURCE, resource);
		editor.putString(PreferenceConstants.PORT, PreferenceConstants.DEFAULT_PORT);
		editor.commit();
	}

}
