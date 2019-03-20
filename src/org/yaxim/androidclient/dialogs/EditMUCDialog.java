package org.yaxim.androidclient.dialogs;

import org.yaxim.androidclient.data.ChatHelper;
import org.yaxim.androidclient.data.ChatRoomHelper;
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
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import org.yaxim.androidclient.R;

public class EditMUCDialog extends AlertDialog implements
		DialogInterface.OnClickListener, TextWatcher {

	private Activity mContext;

	private Button okButton;
	private TextView mInvitation;
	private AutoCompleteJidEdit mRoomJID;
	private EditText mNickName;
	private EditText mPassword;
	private CheckBox mShowPassword;
	private boolean openChat = true;

	public EditMUCDialog(Activity context) {
		super(context);
		mContext = context;

		setTitle(R.string.Menu_muc);

		LayoutInflater inflater = (LayoutInflater) context
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View group = inflater.inflate(R.layout.muc_new_dialog, null, false);
		setView(group);

		mInvitation = (TextView)group.findViewById(R.id.muc_invitation);
		mRoomJID = (AutoCompleteJidEdit)group.findViewById(R.id.muc_new_jid);
		String mucDomain = YaximApplication.getConfig().mucDomain;
		mRoomJID.setServerList(mucDomain, ChatHelper.getXMPPDomains(context, ChatHelper.ROSTER_FILTER_MUCS),
				R.array.muc_services);
		mNickName = (EditText)group.findViewById(R.id.muc_new_nick);
		mPassword = (EditText)group.findViewById(R.id.muc_new_pw);
		mShowPassword = (CheckBox) group.findViewById(R.id.password_show);
		mShowPassword.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView,boolean isChecked) {
				mPassword.setTransformationMethod(isChecked ? null :
						new android.text.method.PasswordTransformationMethod());
			}
		});

		setButton(BUTTON_POSITIVE, context.getString(android.R.string.ok), this);
		setButton(BUTTON_NEGATIVE, context.getString(android.R.string.cancel),
				(DialogInterface.OnClickListener)null);

	}

	// this constructor is called for actual editing of an existing MUC
	public EditMUCDialog(Activity context, String roomJID) {
		this(context);
		setTitle(R.string.roster_contextmenu_muc_edit);

		ChatRoomHelper.RoomInfo ri = ChatRoomHelper.getRoomInfo(mContext, roomJID);
		mRoomJID.setText(roomJID);
		mRoomJID.setInputType(android.text.InputType.TYPE_NULL);
		mNickName.setText(ri.nickname);
		mPassword.setText(ri.password);
		mNickName.requestFocus();
	}

	// this is called when following an invitation
	public EditMUCDialog(Activity context, String roomJID, String invitation,
			String nickname, String password) {
		this(context);
		setTitle(R.string.title_activity_muc_invite);

		if (invitation != null) {
			mInvitation.setText(invitation);
			mInvitation.setVisibility(View.VISIBLE);
		}
		mRoomJID.setText(roomJID);
		mRoomJID.setInputType(android.text.InputType.TYPE_NULL);
		mNickName.setText(nickname);
		mPassword.setText(password);
		mNickName.requestFocus();
	}

	// chained function call to set the nickname
	public EditMUCDialog withNick(String fallback) {
		mNickName.setText(ChatRoomHelper.guessMyNickname(mContext, fallback));
		return this;
	}

	// chained function to prevent opening
	public EditMUCDialog dontOpen() {
		openChat = false;
		return this;
	}

	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);

		okButton = getButton(BUTTON_POSITIVE);
		afterTextChanged(mRoomJID.getText());

		mRoomJID.addTextChangedListener(this);
		mNickName.addTextChangedListener(this);
	}

	public void addAndOpen(String jid, String password, String nickname) {
		ChatRoomHelper.addRoom(mContext, jid, password, nickname, true);
		if (openChat)
			ChatHelper.startChatActivity(mContext, jid, jid, null);
		ChatRoomHelper.syncDbRooms(mContext);
	}

	public void onClick(DialogInterface dialog, int which) {
		addAndOpen(mRoomJID.getText().toString(),
				mPassword.getText().toString(),
				mNickName.getText().toString());
	}

	public void afterTextChanged(Editable s) {
		try {
			XMPPHelper.verifyJabberID(mRoomJID.getText());
			okButton.setEnabled(mNickName.getText().length() > 0);
			mRoomJID.setError(null);
		} catch (YaximXMPPAdressMalformedException e) {
			okButton.setEnabled(false);
			if (s.length() > 0)
				mRoomJID.setError(mContext.getString(R.string.Global_JID_malformed));
		}
	}

	public void beforeTextChanged(CharSequence s, int start, int count,
			int after) {}
	public void onTextChanged(CharSequence s, int start, int before, int count) {}
}
