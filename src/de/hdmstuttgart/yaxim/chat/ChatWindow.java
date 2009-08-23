package de.hdmstuttgart.yaxim.chat;

import java.util.ArrayList;
import java.util.List;

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
import de.hdmstuttgart.yaxim.R;
import de.hdmstuttgart.yaxim.chat.IXMPPChatCallback.Stub;
import de.hdmstuttgart.yaxim.service.IXMPPChatService;
import de.hdmstuttgart.yaxim.service.XMPPService;
import de.hdmstuttgart.yaxim.util.GetDateTimeHelper;

public class ChatWindow extends ListActivity implements OnKeyListener,
		TextWatcher {

	private static final String TAG = "ChatWindow";
	private static final int NOTIFY_ID = 0;

	private Button send = null;
	private EditText userInput = null;
	private String jabberID = null;
	private List<ChatItem> chatItemList;
	private ChatWindowAdapter adapter;
	private Intent xmppServiceIntent;
	private ServiceConnection xmppServiceConnection;
	private XMPPChatServiceAdapter serviceAdapter;
	private Stub chatCallback;
	private ListView chatView;
	private Handler handler;
	private NotificationManager notificationMGR;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		chatItemList = new ArrayList<ChatItem>();
		handler = new Handler();

		setContentView(R.layout.mainchat);
		registerForContextMenu(getListView());
		registerXMPPService();
		createUICallback();
		setNotificationManager();
		setUserInput();
		setSendButton();
		setChatItems();
		setContactFromUri();
		setTitle(getText(R.string.chat_titlePrefix) + " " + jabberID);
	}

	@Override
	protected void onPause() {
		super.onPause();
		serviceAdapter.unregisterUICallback(chatCallback);
		unbindXMPPService();
	}

	@Override
	protected void onResume() {
		super.onResume();
		notificationMGR.cancel(NOTIFY_ID);
		bindXMPPService();
	}

	private void processMessageQueue() {
		List<String> queue = serviceAdapter.pullMessagesForContact(jabberID);
		for (String message : queue) {
			processIncomingMessageInHandler(jabberID, message);
		}
	}

	private void createUICallback() {
		chatCallback = new IXMPPChatCallback.Stub() {

			public void newMessage(String from, String message)
					throws RemoteException {
				processIncomingMessageInHandler(from, message);
			}
		};
	}

	private void processIncomingMessageInHandler(String from, String message) {
		final ChatItem newChatItem = new ChatItem(GetDateTimeHelper.setDate()
				+ ": " + from, message);
		handler.post(new Runnable() {
			public void run() {
				adapter.add(newChatItem);

			}
		});
	}

	private void registerXMPPService() {
		Log.i(TAG, "called startXMPPService()");
		xmppServiceIntent = new Intent(this, XMPPService.class);
		Uri chatURI = Uri.parse("chatwindow");
		xmppServiceIntent.setData(chatURI);
		xmppServiceIntent.setAction("de.hdmstuttgart.yaxim.XMPPSERVICE");

		xmppServiceConnection = new ServiceConnection() {

			public void onServiceConnected(ComponentName name, IBinder service) {
				Log.i(TAG, "called onServiceConnected()");
				serviceAdapter = new XMPPChatServiceAdapter(
						IXMPPChatService.Stub.asInterface(service), jabberID);
				serviceAdapter.registerUICallback(chatCallback);
				processMessageQueue();
			}

			public void onServiceDisconnected(ComponentName name) {
				Log.i(TAG, "called onServiceDisconnected()");
				serviceAdapter.unregisterUICallback(chatCallback);
			}

		};
	}

	private void unbindXMPPService() {
		try {
			unbindService(xmppServiceConnection);
		} catch (IllegalArgumentException e) {
			Log.e(TAG, "Service wasn't bound!");
		}
	}

	private void bindXMPPService() {
		bindService(xmppServiceIntent, xmppServiceConnection, BIND_AUTO_CREATE);
	}

	private void setSendButton() {
		send = (Button) findViewById(R.id.Chat_SendButton);
		View.OnClickListener onSend = getOnSetListener();
		send.setOnClickListener(onSend);
		send.setEnabled(false);
	}

	private void setChatItems() {
		chatView = (ListView) findViewById(android.R.id.list);
		adapter = new ChatWindowAdapter();
		chatView.setAdapter(adapter);
	}

	private void setUserInput() {
		userInput = (EditText) findViewById(R.id.Chat_UserInput);
		userInput.addTextChangedListener(this);
	}

	private void setContactFromUri() {
		Intent i = getIntent();
		jabberID = i.getDataString();
	}

	private View.OnClickListener getOnSetListener() {
		return new View.OnClickListener() {

			public void onClick(View v) {
				sendMessageIfNotNull();
			}
		};
	}

	private void sendMessageIfNotNull() {
		if (userInput.getText().length() >= 1) {
			if (serviceAdapter.isServiceAuthenticated())
				sendMessage(userInput.getText().toString());
			else
				showToastNotification(R.string.toast_connect_before_send);
		}
	}

	private void sendMessage(String message) {

		ChatItem newChatItem = new ChatItem(GetDateTimeHelper.setDate() + ": "
				+ getString(R.string.Global_Me), message);
		adapter.add(newChatItem);
		userInput.setText(null);
		send.setEnabled(false);
		serviceAdapter.sendMessage(jabberID, message);
	}

	class ChatWindowAdapter extends ArrayAdapter<ChatItem> {
		ChatWindowAdapter() {
			super(ChatWindow.this, android.R.layout.simple_list_item_1,
					chatItemList);
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

			wrapper.populateFrom(chatItemList.get(position));
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
		if (userInput.getText().length() >= 1) {
			userInput.setOnKeyListener(this);
			send.setEnabled(true);
		}
	}

	public void beforeTextChanged(CharSequence s, int start, int count,
			int after) {
		// TODO Auto-generated method stub

	}

	public void onTextChanged(CharSequence s, int start, int before, int count) {

	}

	private void setNotificationManager() {
		notificationMGR = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
	}

	private void showToastNotification(int message) {
		Toast tmptoast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
		tmptoast.show();
	}

}
