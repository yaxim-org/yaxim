package de.hdmstuttgart.yaxim.dialogs;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import de.hdmstuttgart.yaxim.R;
import de.hdmstuttgart.yaxim.XMPPRosterServiceAdapter;

public class AboutDialog extends GenericDialog implements OnClickListener,
		TextWatcher {

	public AboutDialog(Context mainWindow,
			XMPPRosterServiceAdapter serviceAdapter) {
		super(mainWindow, serviceAdapter);
	}



	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.aboutdialog);
		
		setTitle(R.string.AboutDialog_title);
		Button okButton = (Button) findViewById(R.id.AboutDialog_OkButton);
		okButton.setOnClickListener(this);
	}

	public void onClick(View view) {
		cancel();
	}

	public void afterTextChanged(Editable s) {
		// TODO Auto-generated method stub
	}

	public void beforeTextChanged(CharSequence s, int start, int count,
			int after) {
		// TODO Auto-generated method stub
	}

	public void onTextChanged(CharSequence s, int start, int before, int count) {
		// TODO Auto-generated method stub
	}

}
