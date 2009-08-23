package de.hdmstuttgart.yaxim.dialogs;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import de.hdmstuttgart.yaxim.R;
import de.hdmstuttgart.yaxim.XMPPRosterServiceAdapter;

public class RemoveRosterItemDialog extends GenericDialog implements OnClickListener {

	private String user;
	private Button okButton;
	private Button cancelButton;
	private TextView deleteRosterItemTextView;
	
	public RemoveRosterItemDialog(Context mainWindow,
			XMPPRosterServiceAdapter serviceAdapter, String user) {
		super(mainWindow, serviceAdapter);
		this.user = user;
	}
	
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.removerosteritemdialog);
		setTitle(R.string.deleteRosterItem_title);
		
		setTextField();
		setOkButton();
		setCancelButton();
	}
	
	private void setTextField() {
		deleteRosterItemTextView =(TextView) findViewById(R.id.DeleteRosterItem_summary);
		deleteRosterItemTextView.setText(mainWindow.getText(R.string.deleteRosterItem_text) + user + " ?");
		deleteRosterItemTextView.setBackgroundColor(android.R.color.background_light);
	}

	private void setOkButton() {
		okButton = (Button) findViewById(R.id.DeleteRosterItem_OkButton);
		okButton.setOnClickListener(this);
	}
	private void setCancelButton() {
		cancelButton = (Button) findViewById(R.id.DeleteRosterItem_CancelButton);
		cancelButton.setOnClickListener(this);
	}
	

	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.DeleteRosterItem_CancelButton:
			
			this.cancel();
			break;

		case R.id.DeleteRosterItem_OkButton:
			
			serviceAdapter.removeRosterItem(user);
			this.cancel();
			break;
		}
	}
}
