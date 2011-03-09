package org.yaxim.androidclient.dialogs;

import java.util.ArrayList;
import java.util.List;

import org.yaxim.androidclient.XMPPRosterServiceAdapter;
import org.yaxim.androidclient.exceptions.YaximXMPPAdressMalformedException;
import org.yaxim.androidclient.util.AdapterConstants;
import org.yaxim.androidclient.util.XMPPHelper;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import org.yaxim.androidclient.R;

public class AddRosterItemDialog extends GenericDialog implements
		OnClickListener, TextWatcher {

	private Button cancelButton;
	private Button okButton;
	private EditText userInputField;
	private EditText aliasInputField;
	private GroupNameView mGroupNameView;

	public AddRosterItemDialog(Context mainWindow,
			XMPPRosterServiceAdapter serviceAdapter) {
		super(mainWindow, serviceAdapter);
	}

	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.addrosteritemdialog);
		setTitle(R.string.addFriend_Title);

		setUserInputField();
		setAliasInputField();
		createAndSetGroupSpinnerAdapter();
		setOkButton();
		setCancelButton();

	}

	private void createAndSetGroupSpinnerAdapter() {
		mGroupNameView = (GroupNameView)findViewById(R.id.AddRosterItem_GroupName);
		mGroupNameView.setGroupList(serviceAdapter.getRosterGroups());
	}

	private void setOkButton() {
		okButton = (Button) findViewById(R.id.AddContact_OkButton);
	}

	private void setUserInputField() {
		userInputField = (EditText) findViewById(R.id.AddContact_EditTextField);
		userInputField.addTextChangedListener(this);
	}

	private void setAliasInputField() {
		aliasInputField = (EditText) findViewById(R.id.AddContactAlias_EditTextField);
	}

	private void setCancelButton() {
		cancelButton = (Button) findViewById(R.id.AddContact_CancelButton);
		cancelButton.setOnClickListener(this);
	}

	public void onClick(View view) {

		switch (view.getId()) {

		case R.id.AddContact_CancelButton:
			cancel();
			break;

		case R.id.AddContact_OkButton:
			serviceAdapter.addRosterItem(userInputField.getText()
					.toString(), aliasInputField.getText().toString(),
					mGroupNameView.getGroupName());
			cancel();
			break;
		}
	}

	public void afterTextChanged(Editable s) {
		try {
			XMPPHelper.verifyJabberID(s);
			okButton.setEnabled(true);
			okButton.setOnClickListener(this);
			userInputField.setTextColor(Color.DKGRAY);
		} catch (YaximXMPPAdressMalformedException e) {
			okButton.setEnabled(false);
			userInputField.setTextColor(Color.RED);
		}
	}

	public void beforeTextChanged(CharSequence s, int start, int count,
			int after) {

	}

	public void onTextChanged(CharSequence s, int start, int before, int count) {

	}

}
