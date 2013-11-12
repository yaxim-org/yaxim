package org.yaxim.androidclient.dialogs;

import org.yaxim.androidclient.XMPPRosterServiceAdapter;
import org.yaxim.androidclient.exceptions.YaximXMPPAdressMalformedException;
import org.yaxim.androidclient.util.XMPPHelper;
import org.yaxim.androidclient.MainWindow;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import org.yaxim.androidclient.R;

public class AddRosterItemDialog extends AlertDialog implements
		DialogInterface.OnClickListener, TextWatcher {

	private MainWindow mMainWindow;
	private XMPPRosterServiceAdapter mServiceAdapter;

	private Button okButton;
	private EditText userInputField;
	private EditText aliasInputField;
	private String generatedAlias = "";
	private GroupNameView mGroupNameView;

	public AddRosterItemDialog(MainWindow mainWindow,
			XMPPRosterServiceAdapter serviceAdapter) {
		super(mainWindow);
		mMainWindow = mainWindow;
		mServiceAdapter = serviceAdapter;

		setTitle(R.string.addFriend_Title);

		LayoutInflater inflater = (LayoutInflater) mainWindow
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View group = inflater.inflate(R.layout.addrosteritemdialog, null, false);
		setView(group);

		userInputField = (EditText)group.findViewById(R.id.AddContact_EditTextField);
		aliasInputField = (EditText)group.findViewById(R.id.AddContactAlias_EditTextField);

		mGroupNameView = (GroupNameView)group.findViewById(R.id.AddRosterItem_GroupName);
		mGroupNameView.setGroupList(mMainWindow.getRosterGroups());

		setButton(BUTTON_POSITIVE, mainWindow.getString(android.R.string.ok), this);
		setButton(BUTTON_NEGATIVE, mainWindow.getString(android.R.string.cancel),
				(DialogInterface.OnClickListener)null);

	}
	public AddRosterItemDialog(MainWindow mainWindow,
			XMPPRosterServiceAdapter serviceAdapter, String jid) {
		this(mainWindow, serviceAdapter);
		userInputField.setText(jid);
	}

	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);

		okButton = getButton(BUTTON_POSITIVE);
		afterTextChanged(userInputField.getText());

		aliasInputField.setText(generatedAlias);
		userInputField.addTextChangedListener(this);
	}

	public void onClick(DialogInterface dialog, int which) {
		String alias = aliasInputField.getText().toString();
		if (alias.length() == 0)
			alias = generatedAlias;
		mServiceAdapter.addRosterItem(userInputField.getText()
				.toString(), alias,
				mGroupNameView.getGroupName());
	}

	public void afterTextChanged(Editable s) {
		try {
			XMPPHelper.verifyJabberID(s);
			okButton.setEnabled(true);
			userInputField.setTextColor(XMPPHelper.getEditTextColor(mMainWindow));
		} catch (YaximXMPPAdressMalformedException e) {
			okButton.setEnabled(false);
			userInputField.setTextColor(Color.RED);
		}
		if (s.length() > 0) {
			String userpart = s.toString().split("@")[0];
			if (userpart.length() > 0) {
				generatedAlias = XMPPHelper.capitalizeString(userpart);
				aliasInputField.setHint(generatedAlias);
			}
		}
	}

	public void beforeTextChanged(CharSequence s, int start, int count,
			int after) {

	}

	public void onTextChanged(CharSequence s, int start, int before, int count) {

	}

}
