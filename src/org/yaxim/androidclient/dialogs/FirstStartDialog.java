package org.yaxim.androidclient.dialogs;

import org.yaxim.androidclient.XMPPRosterServiceAdapter;
import org.yaxim.androidclient.exceptions.YaximXMPPAdressMalformedException;
import org.yaxim.androidclient.util.PreferenceConstants;
import org.yaxim.androidclient.util.XMPPHelper;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import org.yaxim.androidclient.R;

public class FirstStartDialog extends GenericDialog implements OnClickListener,
		TextWatcher {

	private Button mOkButton;
	private EditText mEditJabberID;
	private EditText mEditPassword;
	private EditText mEditPort;

	public FirstStartDialog(Context mainWindow,
			XMPPRosterServiceAdapter serviceAdapter) {
		super(mainWindow, serviceAdapter);
	}

	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.firststartdialog);
		setTitle(R.string.StartupDialog_Title);

		setCancelable(false);
		setEditJabberID();
	}

	private void setEditJabberID() {
		mOkButton = (Button) findViewById(R.id.StartupDialog_OkButton);
		mEditJabberID = (EditText) findViewById(R.id.StartupDialog_JID_EditTextField);
		mEditPassword = (EditText) findViewById(R.id.StartupDialog_PASSWD_EditTextField);
		mEditPort = (EditText) findViewById(R.id.StartupDialog_PORT_EditTextField);
		mEditJabberID.addTextChangedListener(this);
	}

	public void onClick(View view) {
		verifyAndSavePreferences();
	}

	private void verifyAndSavePreferences() {
		String password = mEditPassword.getText().toString();
		String jabberID = mEditJabberID.getText().toString();
		String port = mEditPort.getText().toString();

		if (port == null) {
			savePreferences(jabberID, password);
			cancel();

		} else {
			savePreferences(jabberID, password, port);
			cancel();
		}
	}

	public void afterTextChanged(Editable s) {
		try {
			XMPPHelper.verifyJabberID(s);
			mOkButton.setEnabled(true);
			mOkButton.setOnClickListener(this);
			mEditJabberID.setTextColor(Color.DKGRAY);
		} catch (YaximXMPPAdressMalformedException e) {
			mOkButton.setEnabled(false);
			mEditJabberID.setTextColor(Color.RED);
		}
	}

	public void beforeTextChanged(CharSequence s, int start, int count,
			int after) {
	}

	public void onTextChanged(CharSequence s, int start, int before, int count) {
	}

	private void savePreferences(String jabberID, String password, String port) {
		SharedPreferences sharedPreferences = PreferenceManager
				.getDefaultSharedPreferences(mainWindow);
		Editor editor = sharedPreferences.edit();

		editor.putString(PreferenceConstants.JID, jabberID);
		editor.putString(PreferenceConstants.PASSWORD, password);
		editor.putString(PreferenceConstants.PORT, port);
		editor.commit();
	}

	private void savePreferences(String jabberID, String password) {
		savePreferences(jabberID, password, PreferenceConstants.DEFAULT_PORT);
	}

}
