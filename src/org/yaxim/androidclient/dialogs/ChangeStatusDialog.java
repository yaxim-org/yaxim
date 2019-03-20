package org.yaxim.androidclient.dialogs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.yaxim.androidclient.MainWindow;
import org.yaxim.androidclient.YaximApplication;
import org.yaxim.androidclient.R;
import org.yaxim.androidclient.data.YaximConfiguration;
import org.yaxim.androidclient.util.PreferenceConstants;
import org.yaxim.androidclient.util.StatusMode;

import android.support.v7.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.AutoCompleteTextView;

public class ChangeStatusDialog extends AlertDialog {

	private final CheckBox mDndSilent;

	private final Spinner mStatus;

	private final AutoCompleteTextView mMessage;

	private final MainWindow mContext;

	private final YaximConfiguration mConfig;

	public ChangeStatusDialog(final MainWindow context, final YaximConfiguration config) {
		super(context);

		mContext = context;
		mConfig = config;

		LayoutInflater inflater = (LayoutInflater) context
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		View group = inflater.inflate(R.layout.statusview, null, false);

		final List<StatusMode> modes = new ArrayList<StatusMode>(
				Arrays.asList(StatusMode.values()));
		// the user can not set statusmode "subscribe", it is only for incoming presences
		modes.remove(StatusMode.unknown);
		modes.remove(StatusMode.subscribe);

		Collections.sort(modes, new Comparator<StatusMode>() {
			public int compare(StatusMode object1, StatusMode object2) {
				return object2.compareTo(object1);
			}
		});

		mDndSilent = (CheckBox) group.findViewById(R.id.statusview_dnd_when_silent);
		mDndSilent.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				mStatus.setEnabled(true);
				setSpinnerSelection(modes, StatusMode.fromString(mConfig.statusMode));
				mDndSilent.setVisibility(View.GONE);
			}
		});
		mStatus = (Spinner) group.findViewById(R.id.statusview_spinner);
		StatusModeAdapter mStatusAdapter;
		mStatusAdapter = new StatusModeAdapter(context, R.layout.status_spinner_item, modes);
		mStatus.setAdapter(mStatusAdapter);

		setSpinnerSelection(modes, mConfig.getPresenceMode());

		if (mConfig.smartAwayMode != null) {
			mStatus.setEnabled(false);
			mDndSilent.setVisibility(View.VISIBLE);
			mDndSilent.setChecked(true);
		}

		mMessage = (AutoCompleteTextView) group.findViewById(R.id.statusview_message);
		mMessage.setText(mConfig.statusMessage);
		mMessage.setAdapter(new ArrayAdapter<String>(context,
					android.R.layout.simple_dropdown_item_1line, mConfig.statusMessageHistory));
		mMessage.setThreshold(1);

		Button messageClearButton = (Button) group.findViewById(R.id.statusview_message_button_clear);
		messageClearButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				mMessage.setText("");
			}
		});

		setTitle(R.string.statuspopup_name);
		setView(group);

		setButton(BUTTON_POSITIVE, context.getString(android.R.string.ok),
				new OkListener());

		setButton(BUTTON_NEGATIVE, context.getString(android.R.string.cancel),
				(OnClickListener) null);
	}

	private void setSpinnerSelection(List<StatusMode> modes, StatusMode status_mode) {
		for (int i = 0; i < modes.size(); i++) {
			if (modes.get(i).equals(status_mode)) {
				mStatus.setSelection(i);
			}
		}
	}

	private void setAndSaveStatus() {
		StatusMode statusMode = (StatusMode) mStatus.getSelectedItem();
		String message = mMessage.getText().toString();

		// save update into prefs
		SharedPreferences.Editor prefedit = PreferenceManager
				.getDefaultSharedPreferences(mContext).edit();
		// do not save "offline" to prefs, do not save when DND-silent is enabled
		if (statusMode != StatusMode.offline && !mDndSilent.isChecked())
			prefedit.putString(PreferenceConstants.STATUS_MODE, statusMode.name());
		if (!message.equals(mConfig.statusMessage)) {
			List<String> smh = new ArrayList<String>(java.util.Arrays.asList(mConfig.statusMessageHistory));
			if (!smh.contains(message))
				smh.add(message);
			String smh_joined = android.text.TextUtils.join("\036", smh);
			prefedit.putString(PreferenceConstants.STATUS_MESSAGE_HISTORY, smh_joined);
		}
		prefedit.putString(PreferenceConstants.STATUS_MESSAGE, message);
		// check if DND-silent was disabled by the user
		if (!mDndSilent.isChecked() && mConfig.smartAwayMode != null) {
			prefedit.putBoolean(PreferenceConstants.STATUS_DNDSILENT, false);
			mConfig.smartAwayMode = null;
		}
		prefedit.commit();

		mContext.updateStatus(statusMode);
	}

	private class OkListener implements OnClickListener {
		@Override
		public void onClick(DialogInterface dialog, int which) {
			setAndSaveStatus();
		}
	}

	private class StatusModeAdapter extends ArrayAdapter<StatusMode> {

		public StatusModeAdapter(Context context, int textViewResourceId,
				List<StatusMode> modes) {

			super(context, textViewResourceId, modes);
		}

		@Override
		public View getDropDownView(int position, View convertView, ViewGroup parent) {
			return getCustomView(position, convertView, parent, true);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			return getCustomView(position, convertView, parent, false);
		}

		public View getCustomView(int position, View convertView, ViewGroup parent, boolean padding) {
			LayoutInflater inflater = getLayoutInflater();
			View spinner = inflater.inflate(R.layout.status_spinner_item, parent, false);

			TextView text = (TextView) spinner.findViewById(R.id.status_text);
			text.setText(getItem(position).getTextId());

			ImageView icon = (ImageView) spinner.findViewById(R.id.status_icon);
			icon.setImageResource(getItem(position).getDrawableId());

			if (!padding)
				spinner.setPadding(0, 0, 0, 0);

			return spinner;
		}
	}

}
