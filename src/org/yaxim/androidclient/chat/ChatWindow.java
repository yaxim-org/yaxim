package org.yaxim.androidclient.chat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.yaxim.androidclient.service.XMPPService;
import org.yaxim.androidclient.util.GetDateTimeHelper;

import android.app.ListActivity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnKeyListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import org.yaxim.androidclient.R;
import org.yaxim.androidclient.chat.IXMPPChatCallback;
import org.yaxim.androidclient.chat.IXMPPChatCallback.Stub;
import org.yaxim.androidclient.service.IXMPPChatService;

public class ChatWindow extends ListActivity implements OnKeyListener,
		TextWatcher {

	private static final String TAG = "ChatWindow";
	private static final int NOTIFY_ID = 0;
	
	private static final HashMap<String, ChatWindowAdapter> mAdapterMap = new HashMap<String, ChatWindowAdapter>();
	
	private final ArrayList<ChatItem> mChatItemList = new ArrayList<ChatItem>();

	private Button mSendButton = null;
	private EditText mChatInput = null;
	private String mJabberID = null;
	private Intent mServiceIntent;
	private ServiceConnection mServiceConnection;
	private XMPPChatServiceAdapter mServiceAdapter;
	private Stub mChatCallback;
	private ListView mChatView;
	private Handler mHandler = new Handler();
	private NotificationManager mNotificationMGR;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.mainchat);
		registerForContextMenu(getListView());
		registerXMPPService();
		createUICallback();
		setNotificationManager();
		setUserInput();
		setSendButton();
		setContactFromUri();
		setChatItems();
		setTitle(getText(R.string.chat_titlePrefix) + " " + mJabberID);
	}

	@Override
	protected void onPause() {
		super.onPause();
		mServiceAdapter.unregisterUICallback(mChatCallback);
		unbindXMPPService();
	}

	@Override
	protected void onResume() {
		super.onResume();
		mNotificationMGR.cancel(NOTIFY_ID);
		bindXMPPService();
	}

	private void processMessageQueue() {
		List<String> queue = mServiceAdapter.pullMessagesForContact(mJabberID);
		for (String message : queue) {
			processIncomingMessageInHandler(mJabberID, message);
		}
	}

	private void createUICallback() {
		mChatCallback = new IXMPPChatCallback.Stub() {

			public void newMessage(String from, String message)
					throws RemoteException {
				processIncomingMessageInHandler(from, message);
			}
		};
	}

	private void processIncomingMessageInHandler(String from, String message) {
		final ChatItem newChatItem = new ChatItem(GetDateTimeHelper.setDate()
				+ ": " + from, message);
		mHandler.post(new Runnable() {
			public void run() {
				mAdapterMap.get(mJabberID).add(newChatItem);
			}
		});
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
						IXMPPChatService.Stub.asInterface(service), mJabberID);
				mServiceAdapter.registerUICallback(mChatCallback);
				processMessageQueue();
			}

			public void onServiceDisconnected(ComponentName name) {
				Log.i(TAG, "called onServiceDisconnected()");
				mServiceAdapter.unregisterUICallback(mChatCallback);
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

	private void setChatItems() {
		mChatView = (ListView) findViewById(android.R.id.list);
		if (!mAdapterMap.containsKey(mJabberID))
			mAdapterMap.put(mJabberID, new ChatWindowAdapter());
		mChatView.setAdapter(mAdapterMap.get(mJabberID));
	}

	private void setUserInput() {
		mChatInput = (EditText) findViewById(R.id.Chat_UserInput);
		mChatInput.addTextChangedListener(this);
	}

	private void setContactFromUri() {
		Intent i = getIntent();
		mJabberID = i.getDataString().toLowerCase();
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
		
		ChatItem newChatItem = new ChatItem(GetDateTimeHelper.setDate() + ": "
				+ getString(R.string.Global_Me), message);
		mAdapterMap.get(mJabberID).add(newChatItem);
		mChatInput.setText(null);
		mSendButton.setEnabled(false);
		mServiceAdapter.sendMessage(mJabberID, message);
	}

	class ChatWindowAdapter extends ArrayAdapter<ChatItem> {
		ChatWindowAdapter() {
			super(ChatWindow.this, android.R.layout.simple_list_item_1,
					mChatItemList);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View row = convertView;
			ChatItemWrapper wrapper = null;

			if (row == null) {
				LayoutInflater inflater = getLayoutInflater();
				row = inflater.inflate(R.layout.chatrow, null);
				wrapper = new ChatItemWrapper(row);
				row.setTag(wrapper);
			} else {
				wrapper = (ChatItemWrapper) row.getTag();
			}

			wrapper.populateFrom(mChatItemList.get(position));
			return row;
		}
	}

	public class ChatItemWrapper {
		private TextView name = null;
		private TextView message = null;
		private View row = null;

		ChatItemWrapper(View row) {
			this.row = row;
		}

		void populateFrom(ChatItem chatItem) {
			if (chatItem.getName().endsWith(getString(R.string.Global_Me))) {
				getName().setTextColor(Color.WHITE);
				getName().setShadowLayer(1, 0, 0, Color.RED);
			} else {
				getName().setTextColor(Color.LTGRAY);
				getName().setShadowLayer(1, 0, 0, Color.BLUE);
			}
			getName().setText(chatItem.getName());
			getMessage().setText(chatItem.getMessage());

		}

		TextView getName() {
			if (name == null) {
				name = (TextView) row.findViewById(R.id.chat_info);
			}
			return name;
		}

		TextView getMessage() {
			if (message == null) {
				message = (TextView) row.findViewById(R.id.chat_message);
			}
			return message;
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
		Toast toastNotification = Toast.makeText(this, message, Toast.LENGTH_SHORT);
		toastNotification.show();
	}

}
