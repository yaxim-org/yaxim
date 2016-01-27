package org.yaxim.androidclient.dialogs;

import org.yaxim.androidclient.data.ChatRoomHelper;
import org.yaxim.androidclient.exceptions.YaximXMPPAdressMalformedException;
import org.yaxim.androidclient.util.XMPPHelper;

import android.app.Activity;
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
import android.widget.TextView;
import org.yaxim.androidclient.R;

public class EditMUCDialog extends AlertDialog implements
		DialogInterface.OnClickListener, TextWatcher {

	private Activity mContext;

	private Button okButton;
	private TextView mInvitation;
	private EditText mRoomJID;
	private EditText mNickName;
	private EditText mPassword;

	public EditMUCDialog(Activity context) {
		super(context);
		mContext = context;

		setTitle(R.string.roster_contextmenu_muc_edit);

		LayoutInflater inflater = (LayoutInflater) context
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View group = inflater.inflate(R.layout.muc_new_dialog, null, false);
		setView(group);

		mInvitation = (TextView)group.findViewById(R.id.muc_invitation);
		mRoomJID = (EditText)group.findViewById(R.id.muc_new_jid);
		mNickName = (EditText)group.findViewById(R.id.muc_new_nick);
		mPassword = (EditText)group.findViewById(R.id.muc_new_pw);

		setButton(BUTTON_POSITIVE, context.getString(android.R.string.ok), this);
		setButton(BUTTON_NEGATIVE, context.getString(android.R.string.cancel),
				(DialogInterface.OnClickListener)null);

	}
	public EditMUCDialog(Activity context, String roomJID) {
		this(context);
		ChatRoomHelper.RoomInfo ri = ChatRoomHelper.getRoomInfo(mContext, roomJID);
		mRoomJID.setText(roomJID);
		mRoomJID.setEnabled(false);
		mNickName.setText(ri.nickname);
		mPassword.setText(ri.password);
		mNickName.requestFocus();
	}

	public EditMUCDialog(Activity context, String roomJID, String invitation,
			String nickname, String password) {
		this(context);
		setTitle(R.string.title_activity_muc_invite);

		if (invitation != null) {
			mInvitation.setText(invitation);
			mInvitation.setVisibility(View.VISIBLE);
		}
		mRoomJID.setText(roomJID);
		mRoomJID.setEnabled(false);
		mNickName.setText(nickname);
		mPassword.setText(password);
		mNickName.requestFocus();
	}

	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);

		okButton = getButton(BUTTON_POSITIVE);
		afterTextChanged(mRoomJID.getText());

		mRoomJID.addTextChangedListener(this);
		mNickName.addTextChangedListener(this);
	}

	public void onClick(DialogInterface dialog, int which) {
		ChatRoomHelper.addRoom(mContext,
				mRoomJID.getText().toString(),
				mPassword.getText().toString(),
				mNickName.getText().toString());
		ChatRoomHelper.syncDbRooms(mContext);
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
