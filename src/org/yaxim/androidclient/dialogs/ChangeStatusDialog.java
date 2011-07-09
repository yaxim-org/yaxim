package org.yaxim.androidclient.dialogs;

import org.yaxim.androidclient.MainWindow;
import org.yaxim.androidclient.R;
import org.yaxim.androidclient.R.array;
import org.yaxim.androidclient.R.id;
import org.yaxim.androidclient.R.layout;
import org.yaxim.androidclient.R.string;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;

public class ChangeStatusDialog extends AlertDialog {

	private static final String TAG = "ChangeStatusDialog";

	private final Spinner status;

	private final EditText message;

	private final String[] statusCodes;

	private final MainWindow context;

	public ChangeStatusDialog(final MainWindow context) {
		super(context);

		this.context = context;

		LayoutInflater inflater = (LayoutInflater) context
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		View group = inflater.inflate(R.layout.statusview, null, false);
		status = (Spinner) group.findViewById(R.id.statusview_spinner);
		statusCodes = context.getResources()
				.getStringArray(R.array.statusCodes);
		message = (EditText) group.findViewById(R.id.statusview_message);

		setTitle(R.string.statuspopup_name);
		setView(group);

		setButton(BUTTON_POSITIVE, context.getString(android.R.string.ok),
				new OkListener());

		setButton(BUTTON_NEGATIVE, context.getString(android.R.string.cancel),
				(OnClickListener) null);
	}

	private class OkListener implements OnClickListener {
		@Override
		public void onClick(DialogInterface dialog, int which) {
			String statusStr = statusCodes[status.getSelectedItemPosition()];
			Log.d(TAG, "changeStatusDialog: status=" + statusStr);
			Log.d(TAG, "changeStatusDialog: message="
					+ message.getText().toString());
			context.setStatus(statusStr, message.getText().toString());
		}
	}

}
