package de.hdmstuttgart.yaxim.dialogs;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.AdapterView.OnItemSelectedListener;
import de.hdmstuttgart.yaxim.R;
import de.hdmstuttgart.yaxim.XMPPRosterServiceAdapter;
import de.hdmstuttgart.yaxim.exceptions.YaximXMPPAdressMalformedException;
import de.hdmstuttgart.yaxim.util.AdapterConstants;
import de.hdmstuttgart.yaxim.util.XMPPHelper;

public class AddRosterItemDialog extends GenericDialog implements
		OnClickListener, TextWatcher, OnItemSelectedListener {

	private Button cancelButton;
	private Button okButton;
	private EditText userInputField;
	private EditText aliasInputField;
	private EditText newGroupInputField;
	private Spinner groupSpinner;
	private List<String> groupList;
	private String selectedGroup;

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
		setGroupSpinner();
		setNewGroupInputField();
		createAndSetGroupSpinnerAdapter();
		setOkButton();
		setCancelButton();
	}

	private void setNewGroupInputField() {
		newGroupInputField = (EditText) findViewById(R.id.AddRosterItem_NewGroup_EditTextField);
	}

	private void setGroupSpinner() {
		groupSpinner = (Spinner) findViewById(R.id.AddContact_GroupSpinner);
	}

	private void createAndSetGroupSpinnerAdapter() {
		groupList = serviceAdapter.getRosterGroups();
		
		if (!groupList.contains(AdapterConstants.EMPTY_GROUP)) {
			groupList = new ArrayList<String>();
			groupList.add(AdapterConstants.EMPTY_GROUP);
			groupList.addAll(serviceAdapter.getRosterGroups());
		}
		
		groupList.add(mainWindow
				.getString(R.string.addrosteritemaddgroupchoice));
		ArrayAdapter<String> groupSpinnerAdapter = new ArrayAdapter<String>(
				mainWindow, android.R.layout.simple_spinner_item, groupList);
		groupSpinnerAdapter
				.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		groupSpinner.setAdapter(groupSpinnerAdapter);
		groupSpinner.setOnItemSelectedListener(this);
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
			if (newGroupInputField.getVisibility() == View.VISIBLE) {
				serviceAdapter.addRosterItem(userInputField.getText()
						.toString(), aliasInputField.getText().toString(),
						newGroupInputField.getText().toString());
			} else {
				serviceAdapter.addRosterItem(userInputField.getText()
						.toString(), aliasInputField.getText().toString(),
						selectedGroup);
			}
			cancel();
			break;
		}
	}

	public void afterTextChanged(Editable s) {
		try {
			XMPPHelper.verifyJabberID(s);
			okButton.setClickable(true);
			okButton.setOnClickListener(this);
			userInputField.setTextColor(Color.DKGRAY);
		} catch (YaximXMPPAdressMalformedException e) {
			okButton.setClickable(false);
			userInputField.setTextColor(Color.RED);
		}
	}

	public void beforeTextChanged(CharSequence s, int start, int count,
			int after) {

	}

	public void onTextChanged(CharSequence s, int start, int before, int count) {

	}

	public void onItemSelected(AdapterView<?> view, View arg1, int arg2,
			long arg3) {

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
}
