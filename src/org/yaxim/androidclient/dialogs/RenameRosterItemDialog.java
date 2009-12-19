package org.yaxim.androidclient.dialogs;

import org.yaxim.androidclient.XMPPRosterServiceAdapter;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import org.yaxim.androidclient.R;

public class RenameRosterItemDialog extends GenericDialog implements OnClickListener{
	private Button cancelButton;
	private Button okButton;
	private EditText renameEntryTextField;
	private String entryJabberID;

	public RenameRosterItemDialog(Context mainWindow, XMPPRosterServiceAdapter serviceAdapter, String entryJabberID) {
		super(mainWindow, serviceAdapter);
		this.entryJabberID = entryJabberID;
	}

	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.renameentrydialog);
		setTitle(R.string.RenameEntry_title);

		setRenameGroupTextField();
		setOkButton();
		setCancelButton();
	}
	
	private void setCancelButton() {
		cancelButton = (Button) findViewById(R.id.RenameEntry_CancelButton);
		cancelButton.setOnClickListener(this);
	}
	
	private void setOkButton() {
		okButton = (Button) findViewById(R.id.RenameEntry_OkButton);
		okButton.setOnClickListener(this);
	}
	
	private void setRenameGroupTextField() {
		renameEntryTextField = (EditText) findViewById(R.id.RenameEntry_EditTextField);
	}
	
	
	public void onClick(View view) {

		switch (view.getId()) {

		case R.id.RenameEntry_CancelButton:
			cancel();
			break;

		case R.id.RenameEntry_OkButton:
			serviceAdapter.renameRosterItem(entryJabberID, renameEntryTextField.getText().toString());
			cancel();
			break;
		}
	}


}
