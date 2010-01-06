package org.yaxim.androidclient.chat;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.yaxim.androidclient.R;
import org.yaxim.androidclient.data.ChatProvider;
import org.yaxim.androidclient.data.ChatProvider.Constants;
import org.yaxim.androidclient.service.IXMPPChatService;
import org.yaxim.androidclient.service.XMPPService;

import android.app.ListActivity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnKeyListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class ChatWindow extends ListActivity implements OnKeyListener,
		TextWatcher {

	private static final String TAG = "ChatWindow";
	private static final int NOTIFY_ID = 0;
	private static final String[] PROJECTION_FROM = new String[] {
			ChatProvider.Constants._ID, ChatProvider.Constants.DATE,
			ChatProvider.Constants.FROM_JID, ChatProvider.Constants.TO_JID,
			ChatProvider.Constants.MESSAGE };

	private static final int[] PROJECTION_TO = new int[] { R.id.chat_date,
			R.id.chat_from, R.id.chat_message };

	private Button mSendButton = null;
	private EditText mChatInput = null;
	private String mWithJabberID = null;
	private Intent mServiceIntent;
	private ServiceConnection mServiceConnection;
	private XMPPChatServiceAdapter mServiceAdapter;
	private NotificationManager mNotificationMGR;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.mainchat);
		registerForContextMenu(getListView());
		registerXMPPService();
		setNotificationManager();
		setUserInput();
		setSendButton();
		setContactFromUri();
		setTitle(getText(R.string.chat_titlePrefix) + " " + mWithJabberID);
		setChatWindowAdapter();
	}

	private void setChatWindowAdapter() {
		String selection = Constants.FROM_JID + "='" + mWithJabberID + "' OR "
				+ Constants.TO_JID + "='" + mWithJabberID + "'";
		Cursor cursor = managedQuery(ChatProvider.CONTENT_URI, PROJECTION_FROM,
				selection, null, null);
		ListAdapter adapter = new ChatWindowAdapter(cursor, PROJECTION_FROM,
				PROJECTION_TO);

		setListAdapter(adapter);
	}

	@Override
	protected void onPause() {
		super.onPause();
		unbindXMPPService();
	}

	@Override
	protected void onResume() {
		super.onResume();
		mNotificationMGR.cancel(NOTIFY_ID);
		bindXMPPService();
		mChatInput.requestFocus();

	}

	private void registerXMPPService() {
		Log.i(TAG, "called startXMPPService()");
		mServiceIntent = new Intent(this, XMPPService.class);
		Uri chatURI = Uri.parse("chatwindow");
		mServiceIntent.setData(chatURI);
		mServiceIntent.setAction("org.yaxim.androidclient.XMPPSERVICE");

		mServiceConnection = new ServiceConnection() {

			public void onServiceConnected(ComponentName name, IBinder service) {
				Log.i(TAG, "called onServiceConnected()");
				mServiceAdapter = new XMPPChatServiceAdapter(
						IXMPPChatService.Stub.asInterface(service),
						mWithJabberID);
			}

			public void onServiceDisconnected(ComponentName name) {
				Log.i(TAG, "called onServiceDisconnected()");
			}

		};
	}

	private void unbindXMPPService() {
		try {
			unbindService(mServiceConnection);
		} catch (IllegalArgumentException e) {
			Log.e(TAG, "Service wasn't bound!");
		}
	}

	private void bindXMPPService() {
		bindService(mServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
	}

	private void setSendButton() {
		mSendButton = (Button) findViewById(R.id.Chat_SendButton);
		View.OnClickListener onSend = getOnSetListener();
		mSendButton.setOnClickListener(onSend);
		mSendButton.setEnabled(false);
	}

	private void setUserInput() {
		mChatInput = (EditText) findViewById(R.id.Chat_UserInput);
		mChatInput.addTextChangedListener(this);
	}

	private void setContactFromUri() {
		Intent i = getIntent();
		mWithJabberID = i.getDataString();
	}

	private View.OnClickListener getOnSetListener() {
		return new View.OnClickListener() {

			public void onClick(View v) {
				sendMessageIfNotNull();
			}
		};
	}

	private void sendMessageIfNotNull() {
		if (mChatInput.getText().length() >= 1) {
			if (mServiceAdapter.isServiceAuthenticated())
				sendMessage(mChatInput.getText().toString());
			else
				showToastNotification(R.string.toast_connect_before_send);
		}
	}

	private void sendMessage(String message) {
		mChatInput.setText(null);
		mSendButton.setEnabled(false);
		mServiceAdapter.sendMessage(mWithJabberID, message);
	}

	class ChatWindowAdapter extends SimpleCursorAdapter {

		ChatWindowAdapter(Cursor cursor, String[] from, int[] to) {
			super(ChatWindow.this, android.R.layout.simple_list_item_1, cursor,
					from, to);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View row = convertView;
			ChatItemWrapper wrapper = null;
			Cursor cursor = this.getCursor();
			cursor.moveToPosition(position);

			long dateMilliseconds = cursor.getLong(cursor
					.getColumnIndex(ChatProvider.Constants.DATE));

			String date = getDateString(dateMilliseconds);
			String message = cursor.getString(cursor
					.getColumnIndex(ChatProvider.Constants.MESSAGE));
			String from = cursor.getString(cursor
					.getColumnIndex(ChatProvider.Constants.FROM_JID));

			if (row == null) {
				LayoutInflater inflater = getLayoutInflater();
				row = inflater.inflate(R.layout.chatrow, null);
				wrapper = new ChatItemWrapper(row);
				row.setTag(wrapper);
			} else {
				wrapper = (ChatItemWrapper) row.getTag();
			}

			wrapper.populateFrom(date, from, message);

			return row;
		}
	}

	private String getDateString(long milliSeconds) {
		SimpleDateFormat dateFormater = new SimpleDateFormat("yy-MM-dd HH:mm");
		Date date = new Date(milliSeconds);
		return dateFormater.format(date);
	}

	public class ChatItemWrapper {
		private TextView mDateView = null;
		private TextView mFromView = null;
		private TextView mMessageView = null;

		private final View mRowView;

		ChatItemWrapper(View row) {
			this.mRowView = row;
		}

		void populateFrom(String date, String from, String message) {
			getDateView().setText(date);
			getFromView().setText(from);
			getMessageView().setText(message);
		}

		TextView getDateView() {
			if (mDateView == null) {
				mDateView = (TextView) mRowView.findViewById(R.id.chat_date);
			}
			return mDateView;
		}

		TextView getFromView() {
			if (mFromView == null) {
				mFromView = (TextView) mRowView.findViewById(R.id.chat_from);
			}
			return mFromView;
		}

		TextView getMessageView() {
			if (mMessageView == null) {
				mMessageView = (TextView) mRowView
						.findViewById(R.id.chat_message);
			}
			return mMessageView;
		}

	}

	public boolean onKey(View v, int keyCode, KeyEvent event) {
		if (event.getAction() == KeyEvent.ACTION_DOWN
				&& keyCode == KeyEvent.KEYCODE_ENTER) {
			sendMessageIfNotNull();
			return true;
		}
		return false;

	}

	public void afterTextChanged(Editable s) {
		if (mChatInput.getText().length() >= 1) {
			mChatInput.setOnKeyListener(this);
			mSendButton.setEnabled(true);
		}
	}

	public void beforeTextChanged(CharSequence s, int start, int count,
			int after) {
		// TODO Auto-generated method stub

	}

	public void onTextChanged(CharSequence s, int start, int before, int count) {

	}

	private void setNotificationManager() {
		mNotificationMGR = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
	}

	private void showToastNotification(int message) {
		Toast toastNotification = Toast.makeText(this, message,
				Toast.LENGTH_SHORT);
		toastNotification.show();
	}

}
