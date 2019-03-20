package org.yaxim.androidclient.dialogs;

import org.yaxim.androidclient.XMPPRosterServiceAdapter;
import org.yaxim.androidclient.data.ChatHelper;
import org.yaxim.androidclient.exceptions.YaximXMPPAdressMalformedException;
import org.yaxim.androidclient.util.XMPPHelper;
import org.yaxim.androidclient.widget.AutoCompleteJidEdit;
import org.yaxim.androidclient.YaximApplication;

import android.app.Activity;
import android.support.v7.app.AlertDialog;
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

	private Activity mActivity;

	private Button okButton;
	private AutoCompleteJidEdit userInputField;
	private EditText aliasInputField;
	private String generatedAlias = "";
	private String mToken = null;
	private GroupNameView mGroupNameView;

	public AddRosterItemDialog(Activity act) {
		super(act);
		mActivity = act;

		setTitle(R.string.addFriend_Title);

		LayoutInflater inflater = (LayoutInflater) act
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View group = inflater.inflate(R.layout.addrosteritemdialog, null, false);
		setView(group);

		userInputField = (AutoCompleteJidEdit)group.findViewById(R.id.AddContact_EditTextField);
		userInputField.setServerList(YaximApplication.getConfig().server,
				ChatHelper.getXMPPDomains(act, ChatHelper.ROSTER_FILTER_CONTACTS), R.array.xmpp_servers);
		aliasInputField = (EditText)group.findViewById(R.id.AddContactAlias_EditTextField);

		mGroupNameView = (GroupNameView)group.findViewById(R.id.AddRosterItem_GroupName);
		mGroupNameView.setGroupList(ChatHelper.getRosterGroups(mActivity));

		setButton(BUTTON_POSITIVE, act.getString(android.R.string.ok), this);
		setButton(BUTTON_NEGATIVE, act.getString(android.R.string.cancel),
				(DialogInterface.OnClickListener)null);

	}
	public AddRosterItemDialog(Activity act, String jid) {
		this(act);
		userInputField.setText(jid);
	}

	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);

		okButton = getButton(BUTTON_POSITIVE);
		afterTextChanged(userInputField.getText());

		if (aliasInputField.getText().length() == 0)
			aliasInputField.setText(generatedAlias);
		userInputField.addTextChangedListener(this);
	}

	public AddRosterItemDialog setAlias(String alias) {
		if (alias != null && alias.length() > 0)
			aliasInputField.setText(alias);
		return this;
	}
	public AddRosterItemDialog setToken(String token) {
		if (token != null && token.length() > 0)
			mToken = token;
		return this;
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
		try {
			YaximApplication.getApp().getSmackable().addRosterItem(
					realJid,
					alias,
					mGroupNameView.getGroupName(),
					mToken);
		} catch (Exception e) {
			ChatHelper.shortToastNotify(mActivity, e);
			new AddRosterItemDialog(mActivity, userInputField.getText().toString())
					.setAlias(aliasInputField.getText().toString())
					.setToken(mToken)
					.show();
		}
	}

	public void afterTextChanged(Editable s) {
		try {
			XMPPHelper.verifyJabberID(s);
			okButton.setEnabled(true);
			userInputField.setError(null);
		} catch (YaximXMPPAdressMalformedException e) {
			okButton.setEnabled(false);
			if (s.length() > 0)
				userInputField.setError(mActivity.getString(R.string.Global_JID_malformed));
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
