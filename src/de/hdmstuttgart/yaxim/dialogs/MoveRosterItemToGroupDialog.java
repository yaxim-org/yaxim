package de.hdmstuttgart.yaxim.dialogs;

import java.util.ArrayList;
import java.util.List;

import de.hdmstuttgart.yaxim.R;
import de.hdmstuttgart.yaxim.XMPPRosterServiceAdapter;
import de.hdmstuttgart.yaxim.util.AdapterConstants;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.AdapterView.OnItemSelectedListener;

public class MoveRosterItemToGroupDialog extends GenericDialog implements
		OnItemSelectedListener, OnClickListener {

	private Button cancelButton;
	private Button okButton;
	private Spinner groupSpinner;
	private List<String> groupList;
	private EditText newGroupInputField;
	private String entryJabberID;
	private String selectedGroup;

	public MoveRosterItemToGroupDialog(Context mainWindow,
			XMPPRosterServiceAdapter serviceAdapter, String user) {
		super(mainWindow, serviceAdapter);
		this.entryJabberID = user;
	}

	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.moverosterentrytogroupdialog);
		setTitle(R.string.MoveRosterEntryToGroupDialog_title);

		setGroupSpinner();
		createAndSetGroupSpinnerAdapter();
		setNewGroupInputField();
		setCancelButton();
		setOkButton();
	}

	private void setNewGroupInputField() {
		newGroupInputField = (EditText) findViewById(R.id.moveRosterItemToGroup_NewGroup_EditTextField);
	}

	private void setGroupSpinner() {
		groupSpinner = (Spinner) findViewById(R.id.MoveRosterEntryToGroupDialog_GroupSpinner);
	}

	private void createAndSetGroupSpinnerAdapter() {
		groupList = serviceAdapter.getRosterGroups();

		if (!groupList.contains(AdapterConstants.EMPTY_GROUP)) {
			groupList = new ArrayList<String>();
			groupList.add(AdapterConstants.EMPTY_GROUP);
			groupList.addAll(serviceAdapter.getRosterGroups());
		}

		ArrayAdapter<String> groupSpinnerAdapter = new ArrayAdapter<String>(
				mainWindow, android.R.layout.simple_spinner_item, groupList);
		groupSpinnerAdapter
				.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		groupSpinner.setAdapter(groupSpinnerAdapter);
		groupSpinnerAdapter.add(mainWindow
				.getString(R.string.addrosteritemaddgroupchoice));
		groupSpinner.setOnItemSelectedListener(this);
	}

	private void setCancelButton() {
		cancelButton = (Button) findViewById(R.id.moveRosterEntryToGroupDialog_CancelButton);
		cancelButton.setOnClickListener(this);
	}

	private void setOkButton() {
		okButton = (Button) findViewById(R.id.moveRosterEntryToGroupDialog_OkButton);
		okButton.setOnClickListener(this);
	}

	public void onItemSelected(AdapterView<?> view, View v, int position,
			long id) {
		if (view.getSelectedItem().toString().equals(
				mainWindow.getString(R.string.addrosteritemaddgroupchoice))) {
			newGroupInputField.setVisibility(View.VISIBLE);
			newGroupInputField.setEnabled(true);
		} else {
			newGroupInputField.setVisibility(View.INVISIBLE);
			newGroupInputField.setEnabled(false);
			selectedGroup = view.getSelectedItem().toString();
		}
	}

	public void onNothingSelected(AdapterView<?> arg0) {

	}

	public void onClick(View v) {

		switch (v.getId()) {
		case R.id.moveRosterEntryToGroupDialog_CancelButton:
			cancel();
			break;

		case R.id.moveRosterEntryToGroupDialog_OkButton:

			if (newGroupInputField.getVisibility() == View.VISIBLE) {
				serviceAdapter.moveRosterItemToGroup(entryJabberID,
						newGroupInputField.getText().toString());
			} else {
				serviceAdapter.moveRosterItemToGroup(entryJabberID,
						selectedGroup);
			}
			cancel();
			break;
		}
	}

}
