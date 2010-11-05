package org.yaxim.androidclient.dialogs;

import java.util.ArrayList;
import java.util.List;

import org.yaxim.androidclient.XMPPRosterServiceAdapter;
import org.yaxim.androidclient.util.AdapterConstants;

import org.yaxim.androidclient.R;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class MoveRosterItemToGroupDialog extends GenericDialog implements
		OnClickListener {

	private Button cancelButton;
	private Button okButton;
	private String entryJabberID;
	private String selectedGroup;
	private GroupNameView mGroupNameView;

	public MoveRosterItemToGroupDialog(Context mainWindow,
			XMPPRosterServiceAdapter serviceAdapter, String user) {
		super(mainWindow, serviceAdapter);
		this.entryJabberID = user;
	}

	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.moverosterentrytogroupdialog);
		setTitle(R.string.MoveRosterEntryToGroupDialog_title);

		createAndSetGroupSpinnerAdapter();
		setCancelButton();
		setOkButton();
	}

	private void createAndSetGroupSpinnerAdapter() {
		mGroupNameView = (GroupNameView)findViewById(R.id.AddRosterItem_GroupName);
		mGroupNameView.setGroupList(serviceAdapter.getRosterGroups());
	}

	private void setCancelButton() {
		cancelButton = (Button) findViewById(R.id.moveRosterEntryToGroupDialog_CancelButton);
		cancelButton.setOnClickListener(this);
	}

	private void setOkButton() {
		okButton = (Button) findViewById(R.id.moveRosterEntryToGroupDialog_OkButton);
		okButton.setOnClickListener(this);
	}


	public void onClick(View v) {

		switch (v.getId()) {
		case R.id.moveRosterEntryToGroupDialog_CancelButton:
			cancel();
			break;

		case R.id.moveRosterEntryToGroupDialog_OkButton:

			serviceAdapter.moveRosterItemToGroup(entryJabberID,
					mGroupNameView.getGroupName());
			cancel();
			break;
		}
	}

}
