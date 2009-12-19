package org.yaxim.androidclient.dialogs;

import org.yaxim.androidclient.MainWindow;
import org.yaxim.androidclient.XMPPRosterServiceAdapter;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;

public abstract class GenericDialog extends Dialog {

	protected MainWindow mainWindow;
	protected XMPPRosterServiceAdapter serviceAdapter;
	private static final String TAG = "GenericDialog";

	public GenericDialog(Context context, XMPPRosterServiceAdapter serviceAdapter) {
		super(context);
		tryToSetMainWindow(context);
		this.serviceAdapter = serviceAdapter;
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
                WindowManager.LayoutParams.FLAG_BLUR_BEHIND);

		setCancelable(true);
	}

	private void tryToSetMainWindow(Context context) {
		try {
			this.mainWindow = (MainWindow) context;
		} catch (ClassCastException e) {
			Log.e(TAG, "Called from wrong context!");
		}
	}
}
