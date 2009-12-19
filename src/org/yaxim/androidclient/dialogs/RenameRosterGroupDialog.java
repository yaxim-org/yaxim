package org.yaxim.androidclient.dialogs;

import org.yaxim.androidclient.XMPPRosterServiceAdapter;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import org.yaxim.androidclient.R;

public class RenameRosterGroupDialog extends GenericDialog implements
		OnClickListener, TextWatcher {

	private final Button okButton = (Button) findViewById(R.id.RenameGroup_OkButton);
	private final Button cancelButton = (Button) findViewById(R.id.RenameGroup_CancelButton);;
	private final EditText renameGroupTextField = (EditText) findViewById(R.id.RenameGroup_EditTextField);;
	private final String groupTitle;

	public RenameRosterGroupDialog(Context mainWindow,
			XMPPRosterServiceAdapter serviceAdapter, String groupTitle) {
		super(mainWindow, serviceAdapter);
		this.groupTitle = groupTitle;
	}

	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.renamegroupdialog);
		setTitle(R.string.RenameGroup_title);

		setupListeners();
	}

	private void setupListeners() {
		cancelButton.setOnClickListener(this);
		okButton.setOnClickListener(this);
		
		renameGroupTextField.setText(groupTitle);
		renameGroupTextField.addTextChangedListener(this);
	}

	public void onClick(View view) {

		switch (view.getId()) {
		case R.id.RenameGroup_CancelButton:
			cancel();
			break;

		case R.id.RenameGroup_OkButton:
			serviceAdapter.renameRosterGroup(groupTitle, renameGroupTextField
					.getText().toString());
			cancel();
			break;
		}
	}

	public void afterTextChanged(Editable s) {
		// TODO Auto-generated method stub
	}

	public void beforeTextChanged(CharSequence s, int start, int count,
			int after) {
		// TODO Auto-generated method stub
	}

	public void onTextChanged(CharSequence s, int start, int before, int count) {
		okButton.setEnabled(s.length() > 0);
	}
}
