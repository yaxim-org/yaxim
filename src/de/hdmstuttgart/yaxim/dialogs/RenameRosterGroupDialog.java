package de.hdmstuttgart.yaxim.dialogs;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import de.hdmstuttgart.yaxim.R;
import de.hdmstuttgart.yaxim.XMPPRosterServiceAdapter;

public class RenameRosterGroupDialog extends GenericDialog implements OnClickListener {
	
	private Button cancelButton;
	private Button okButton;
	private EditText renameGroupTextField;
	private String groupTitle;
	
	public RenameRosterGroupDialog(Context mainWindow, XMPPRosterServiceAdapter serviceAdapter, String groupTitle) {
		super(mainWindow,serviceAdapter);
		this.groupTitle=groupTitle;
	}
	
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.renamegroupdialog);
		setTitle(R.string.RenameGroup_title);

		setRenameGroupTextField();
		setOkButton();
		setCancelButton();
	}
	
	private void setCancelButton() {
		cancelButton = (Button) findViewById(R.id.RenameGroup_CancelButton);
		cancelButton.setOnClickListener(this);
	}
	
	private void setOkButton() {
		okButton = (Button) findViewById(R.id.RenameGroup_OkButton);
		okButton.setOnClickListener(this);
		
	}
	
	private void setRenameGroupTextField() {
		renameGroupTextField = (EditText) findViewById(R.id.RenameGroup_EditTextField);
		renameGroupTextField.setText(groupTitle);
	}
	
	public void onClick(View view) {

		switch (view.getId()) {
		case R.id.RenameGroup_CancelButton:
			cancel();
			break;

		case R.id.RenameGroup_OkButton:
			serviceAdapter.renameRosterGroup(groupTitle, renameGroupTextField.getText().toString());
			cancel();
			break;
		}
	}
}
