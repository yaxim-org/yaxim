package de.hdmstuttgart.yaxim.dialogs;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import de.hdmstuttgart.yaxim.R;
import de.hdmstuttgart.yaxim.XMPPRosterServiceAdapter;
import de.hdmstuttgart.yaxim.util.StatusMode;

public class ChangeStatusDialog extends GenericDialog implements
		OnCheckedChangeListener, OnClickListener,
		TextWatcher {

	private RadioGroup statusRadioGroup;
	private Button cancelButton;
	private Button okButton;
	private EditText statusTextField;
	private StatusMode selectedStatus = StatusMode.available;
	private String statusmsg;

	public ChangeStatusDialog(Context mainWindow, XMPPRosterServiceAdapter serviceAdapter) {
		super(mainWindow, serviceAdapter);
		this.setTitle(R.string.setStatusTitle);
	}

	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.statusdialog);

		setStatusRadioGroup();
		setStatusTextField();
		setOkButton();
		setCancelButton();
	}

	private void setStatusRadioGroup() {
		statusRadioGroup = (RadioGroup) findViewById(R.id.StatusDialog_RadioGroup);
		statusRadioGroup.setOnCheckedChangeListener(this);
		statusRadioGroup.setSaveEnabled(true);
	}

	private void setStatusTextField() {
		statusTextField = (EditText) findViewById(R.id.StatusDialogTextField);
		statusTextField.addTextChangedListener(this);
		statusTextField.setText(R.string.setStatusmsgDefault);
	}

	private void setOkButton() {
		okButton = (Button) findViewById(R.id.StatusDialogOkButton);
		okButton.setOnClickListener(this);
	}

	private void setCancelButton() {
		cancelButton = (Button) findViewById(R.id.StatusDialogCancelButton);
		cancelButton.setOnClickListener(this);
	}

	public void onCheckedChanged(RadioGroup radioGroup, int selectedID) {
		if (radioGroup == statusRadioGroup) {
			switch (selectedID) {
			case R.id.RB_online:
				selectedStatus = StatusMode.available;
				break;

			case R.id.RB_away:
				selectedStatus = StatusMode.away;
				break;

			case R.id.RB_chat:
				selectedStatus = StatusMode.chat;
				break;

			case R.id.RB_donotdisturb:
				selectedStatus = StatusMode.dnd;
				break;

			case R.id.RB_notavailable:
				selectedStatus = StatusMode.xa;
				break;

			case R.id.RB_offline:
				selectedStatus = StatusMode.offline;
				break;
			}
		}
	}

	public void onClick(View view) {
		switch (view.getId()) {
		case R.id.StatusDialogCancelButton:
			this.cancel();
			break;

		case R.id.StatusDialogOkButton:
			statusmsg = statusTextField.getText().toString();

			setStatus(selectedStatus, statusmsg);
			break;
		}
	}

	private void setStatus(StatusMode status, String statusmsg) {
		serviceAdapter.setStatus(status, statusmsg);
		cancel();
	}

	public void afterTextChanged(Editable arg0) {
	

	}

	public void beforeTextChanged(CharSequence arg0, int arg1, int arg2,
			int arg3) {
		

	}

	public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {

	}
}
