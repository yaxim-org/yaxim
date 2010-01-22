package org.yaxim.androidclient.dialogs;

import org.yaxim.androidclient.XMPPRosterServiceAdapter;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import org.yaxim.androidclient.R;

public class AboutDialog extends GenericDialog implements OnClickListener,
		TextWatcher {

	String versionTitle;

	public AboutDialog(Context mainWindow,
			XMPPRosterServiceAdapter serviceAdapter) {
		super(mainWindow, serviceAdapter);

		versionTitle  = mainWindow.getText(R.string.AboutDialog_title).toString();
		try {
			PackageManager pm = mainWindow.getPackageManager();
			PackageInfo pi = pm.getPackageInfo(mainWindow.getPackageName(), 0);
			versionTitle += " v" + pi.versionName;
		} catch (NameNotFoundException e) {
		}
	}



	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.aboutdialog);

		setTitle(versionTitle);
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
