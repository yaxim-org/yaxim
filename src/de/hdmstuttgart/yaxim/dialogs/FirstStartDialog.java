package de.hdmstuttgart.yaxim.dialogs;

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
import de.hdmstuttgart.yaxim.R;
import de.hdmstuttgart.yaxim.XMPPRosterServiceAdapter;
import de.hdmstuttgart.yaxim.exceptions.YaximXMPPAdressMalformedException;
import de.hdmstuttgart.yaxim.util.PreferenceConstants;
import de.hdmstuttgart.yaxim.util.XMPPHelper;

public class FirstStartDialog extends GenericDialog implements
		OnClickListener, TextWatcher {

	private Button cancelButton;
	private Button okButton;
	private EditText editJabberID;
	private EditText editPassword;
	private EditText editPort;

	public FirstStartDialog(Context mainWindow,
			XMPPRosterServiceAdapter serviceAdapter) {
		super(mainWindow, serviceAdapter);
	}

	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.firststartdialog);
		setTitle(R.string.StartupDialog_Title);

		setEditPort();
		setEditPassword();
		setEditJabberID();
		setOkButton();
		setCancelButton();
	}

	private void setEditJabberID() {
		editJabberID = (EditText) findViewById(R.id.StartupDialog_JID_EditTextField);
		editJabberID.addTextChangedListener(this);
	}

	private void setEditPort() {
		editPort = (EditText) findViewById(R.id.StartupDialog_PORT_EditTextField);
	}

	private void setEditPassword() {
		editPassword = (EditText) findViewById(R.id.StartupDialog_PASSWD_EditTextField);
	}

	private void setOkButton() {
		okButton = (Button) findViewById(R.id.StartupDialog_OkButton);
	}

	private void setCancelButton() {
		cancelButton = (Button) findViewById(R.id.StartupDialog_CancelButton);
		cancelButton.setOnClickListener(this);
	}

	public void onClick(View view) {

		switch (view.getId()) {

		case R.id.StartupDialog_CancelButton:
			cancel();
			break;

		case R.id.StartupDialog_OkButton:
			verifyAndSavePreferences();
			break;
		}
	}

	private void verifyAndSavePreferences() {
		String password = editPassword.getText().toString();
		String jabberID = editJabberID.getText().toString();
		String port = editPort.getText().toString();

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
			okButton.setClickable(true);
			okButton.setOnClickListener(this);
			editJabberID.setTextColor(Color.DKGRAY);
		} catch (YaximXMPPAdressMalformedException e) {
			okButton.setClickable(false);
			editJabberID.setTextColor(Color.RED);
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
