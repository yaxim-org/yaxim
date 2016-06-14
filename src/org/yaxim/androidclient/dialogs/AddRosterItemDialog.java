package org.yaxim.androidclient.dialogs;

import org.yaxim.androidclient.XMPPRosterServiceAdapter;
import org.yaxim.androidclient.exceptions.YaximXMPPAdressMalformedException;
import org.yaxim.androidclient.util.XMPPHelper;
import org.yaxim.androidclient.MainWindow;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
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
		String realJid;
		try {
			realJid = XMPPHelper.verifyJabberID(userInputField.getText());
		} catch (YaximXMPPAdressMalformedException e) {
			e.printStackTrace();
			return;
		}
		mServiceAdapter.addRosterItem(
				realJid,
				alias,
				mGroupNameView.getGroupName(),
				null);
	}

	public void afterTextChanged(Editable s) {
		try {
			XMPPHelper.verifyJabberID(s);
			okButton.setEnabled(true);
			userInputField.setError(null);
		} catch (YaximXMPPAdressMalformedException e) {
			okButton.setEnabled(false);
			if (s.length() > 0)
				userInputField.setError(mMainWindow.getString(R.string.Global_JID_malformed));
		}
		if (s.length() > 0) {
			String userpart[] = s.toString().split("@");
			if (userpart.length > 0 && userpart[0].length() > 0) {
				generatedAlias = XMPPHelper.capitalizeString(userpart[0]);
				aliasInputField.setHint(generatedAlias);
			}
		}
	}

	public void beforeTextChanged(CharSequence s, int start, int count,
			int after) {}
	public void onTextChanged(CharSequence s, int start, int before, int count) {}
}
