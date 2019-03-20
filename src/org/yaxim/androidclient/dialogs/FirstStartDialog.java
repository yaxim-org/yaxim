package org.yaxim.androidclient.dialogs;

import org.yaxim.androidclient.XMPPRosterServiceAdapter;
import org.yaxim.androidclient.exceptions.YaximXMPPAdressMalformedException;
import org.yaxim.androidclient.preferences.AccountPrefs;
import org.yaxim.androidclient.util.PreferenceConstants;
import org.yaxim.androidclient.util.XMPPHelper;
import org.yaxim.androidclient.widget.AutoCompleteJidEdit;

import android.support.v7.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import org.yaxim.androidclient.MainWindow;
import org.yaxim.androidclient.R;

public class FirstStartDialog extends AlertDialog implements DialogInterface.OnClickListener,
		CompoundButton.OnCheckedChangeListener, TextWatcher {

	private MainWindow mainWindow;
	private Button mOkButton;
	private AutoCompleteJidEdit mEditJabberID;
	private EditText mEditPassword;
	private CheckBox mShowPassword;
	private CheckBox mCreateAccount;
	private String preauth;

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

		mEditJabberID = (AutoCompleteJidEdit) group.findViewById(R.id.StartupDialog_JID_EditTextField);
		mEditPassword = (EditText) group.findViewById(R.id.StartupDialog_PASSWD_EditTextField);
		mCreateAccount = (CheckBox) group.findViewById(R.id.create_account);
		mShowPassword = (CheckBox) group.findViewById(R.id.password_show);

		mEditJabberID.setServerList(R.array.xmpp_servers);
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mOkButton = getButton(BUTTON_POSITIVE);

		SharedPreferences sharedPreferences = PreferenceManager
				.getDefaultSharedPreferences(mainWindow);
		mEditJabberID.setText(sharedPreferences.getString(PreferenceConstants.JID, ""));
		mEditPassword.setText(sharedPreferences.getString(PreferenceConstants.PASSWORD, ""));

		mEditJabberID.addTextChangedListener(this);
		mEditPassword.addTextChangedListener(this);
		mCreateAccount.setOnCheckedChangeListener(this);
		mShowPassword.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView,boolean isChecked) {
				mEditPassword.setTransformationMethod(isChecked ? null :
						new android.text.method.PasswordTransformationMethod());
			}
		});
		// if create is set, simulate click on checkbox
		if (sharedPreferences.getBoolean(PreferenceConstants.INITIAL_CREATE, false)) {
			mCreateAccount.setChecked(true);
		}
		updateDialog(false);
	}

	public FirstStartDialog setJID(String jid, String preauth) {
		android.util.Log.d("FirstStartDialog", "setJID: " + jid + " / " + preauth);
		((TextView)findViewById(R.id.StartupDialog_Summary)).setText(R.string.StartupDialog_invitation);
		this.preauth = preauth;
		mEditJabberID.setText(jid);
		mEditJabberID.setInputType(android.text.InputType.TYPE_NULL);
		mEditJabberID.dismissDropDown();
		mCreateAccount.setChecked(true);
		mCreateAccount.setEnabled(false);
		return this;
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
		savePreferences(jabberID, password, XMPPHelper.createResource(mainWindow), mCreateAccount.isChecked());
		dismiss();
	}

	private void updateDialog(boolean show_errors) {
		boolean is_ok = true;
		// verify jabber ID
		Editable jid = mEditJabberID.getText();
		try {
			XMPPHelper.verifyJabberID(jid);
			//mOkButton.setOnClickListener(this);
			mEditJabberID.setError(null);
		} catch (YaximXMPPAdressMalformedException e) {
			if (show_errors && (jid.length() > 0))
				mEditJabberID.setError(mainWindow.getString(R.string.Global_JID_malformed));
			is_ok = false;
		}
		if (mEditPassword.length() == 0)
			is_ok = false;
		if (mCreateAccount.isChecked()) {
			boolean good_password = (mEditPassword.length() >= 6);
			is_ok = is_ok && good_password;
			mEditPassword.setError((!show_errors || good_password || mEditPassword.length() == 0) ?
					null : mainWindow.getString(R.string.StartupDialog_error_password));
		}
		mOkButton.setEnabled(is_ok);
	}

	/* CompoundButton.OnCheckedChangeListener for mCreateAccount */
	@Override
	public void onCheckedChanged(CompoundButton btn,boolean isChecked) {
		if (isChecked) {
			if (mEditPassword.length() == 0) {
				// create secure random password
				String pw = XMPPHelper.securePassword();
				Toast.makeText(mainWindow, R.string.StartupDialog_created_password, Toast.LENGTH_SHORT).show();
				mEditPassword.setText(pw);
			} else
				mEditPassword.requestFocus();
		}
		updateDialog(true);
	}
	public void afterTextChanged(Editable s) {
		updateDialog(true);
	}

	public void beforeTextChanged(CharSequence s, int start, int count,
			int after) {
	}

	public void onTextChanged(CharSequence s, int start, int before, int count) {
	}

	private void savePreferences(String jabberID, String password, String resource, boolean initial_create) {
		SharedPreferences sharedPreferences = PreferenceManager
				.getDefaultSharedPreferences(mainWindow);
		Editor editor = sharedPreferences.edit();

		editor.putString(PreferenceConstants.JID, jabberID);
		editor.putString(PreferenceConstants.PASSWORD, password);
		editor.putBoolean(PreferenceConstants.FIRSTRUN, true);
		if (sharedPreferences.getString(PreferenceConstants.RESSOURCE, null) == null)
			editor.putString(PreferenceConstants.RESSOURCE, resource);
		editor.putBoolean(PreferenceConstants.INITIAL_CREATE, initial_create);
		if (preauth != null)
			editor.putString(PreferenceConstants.INITIAL_PREAUTH, preauth);
		editor.commit();
	}

}
