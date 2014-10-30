package org.yaxim.androidclient.chat;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.yaxim.androidclient.MainWindow;
import org.yaxim.androidclient.R;
import org.yaxim.androidclient.YaximApplication;
import org.yaxim.androidclient.data.ChatProvider;
import org.yaxim.androidclient.data.ChatProvider.ChatConstants;
import org.yaxim.androidclient.data.RosterProvider;
import org.yaxim.androidclient.service.IXMPPChatService;
import org.yaxim.androidclient.service.XMPPService;
import org.yaxim.androidclient.util.StatusMode;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockListActivity;
import com.actionbarsherlock.view.Window;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Handler;
import android.text.ClipboardManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnKeyListener;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

@SuppressWarnings("deprecation") /* recent ClipboardManager only available since API 11 */
public class ChatWindow extends SherlockListActivity implements OnKeyListener,
		TextWatcher {

	public static final String INTENT_EXTRA_USERNAME = ChatWindow.class.getName() + ".username";
	public static final String INTENT_EXTRA_MESSAGE = ChatWindow.class.getName() + ".message";
	
	private static final String TAG = "yaxim.ChatWindow";
	private static final String[] PROJECTION_FROM = new String[] {
			ChatProvider.ChatConstants._ID, ChatProvider.ChatConstants.DATE,
			ChatProvider.ChatConstants.DIRECTION, ChatProvider.ChatConstants.JID,
			ChatProvider.ChatConstants.MESSAGE, ChatProvider.ChatConstants.DELIVERY_STATUS };

	private static final int[] PROJECTION_TO = new int[] { R.id.chat_date,
			R.id.chat_from, R.id.chat_message };
	
	private static final int DELAY_NEWMSG = 2000;

	private ContentObserver mContactObserver = new ContactObserver();
	private ImageView mStatusMode;
	private TextView mTitle;
	private TextView mSubTitle;
	private Button mSendButton = null;
	private EditText mChatInput = null;
	private String mWithJabberID = null;
	private String mUserScreenName = null;
	private Intent mServiceIntent;
	private ServiceConnection mServiceConnection;
	private XMPPChatServiceAdapter mServiceAdapter;
	private int mChatFontSize;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		setTheme(YaximApplication.getConfig(this).getTheme());
		super.onCreate(savedInstanceState);
	
		mChatFontSize = Integer.valueOf(YaximApplication.getConfig(this).chatFontSize);

		requestWindowFeature(Window.FEATURE_ACTION_BAR);
		setContentView(R.layout.mainchat);
		
		getContentResolver().registerContentObserver(RosterProvider.CONTENT_URI,
				true, mContactObserver);

		ActionBar actionBar = getSupportActionBar();
		actionBar.setHomeButtonEnabled(true);
		actionBar.setDisplayHomeAsUpEnabled(true);

		registerForContextMenu(getListView());
		setContactFromUri();
		registerXMPPService();
		setSendButton();
		setUserInput();
		
		String titleUserid;
		if (mUserScreenName != null) {
			titleUserid = mUserScreenName;
		} else {
			titleUserid = mWithJabberID;
		}

		setCustomTitle(titleUserid);

		setChatWindowAdapter();
	}

	private void setCustomTitle(String title) {
		LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
		View layout = inflater.inflate(R.layout.chat_action_title, null);
		mStatusMode = (ImageView)layout.findViewById(R.id.action_bar_status);
		mTitle = (TextView)layout.findViewById(R.id.action_bar_title);
		mSubTitle = (TextView)layout.findViewById(R.id.action_bar_subtitle);
		mTitle.setText(title);

		setTitle(null);
		getSupportActionBar().setCustomView(layout);
		getSupportActionBar().setDisplayShowCustomEnabled(true);
	}

	private void setChatWindowAdapter() {
		String selection = ChatConstants.JID + "='" + mWithJabberID + "'";
		Cursor cursor = managedQuery(ChatProvider.CONTENT_URI, PROJECTION_FROM,
				selection, null, null);
		ListAdapter adapter = new ChatWindowAdapter(cursor, PROJECTION_FROM,
				PROJECTION_TO, mWithJabberID, mUserScreenName);

		setListAdapter(adapter);
	}

	protected boolean needs_to_bind_unbind = false;

	@Override
	protected void onResume() {
		super.onResume();
		updateContactStatus();
		needs_to_bind_unbind = true;
	}

	@Override
	protected void onPause() {
		super.onPause();
		needs_to_bind_unbind = true;
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (!needs_to_bind_unbind)
			return;
		if (hasFocus)
			bindXMPPService();
		else
			unbindXMPPService();
		needs_to_bind_unbind = false;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (hasWindowFocus()) unbindXMPPService();
		getContentResolver().unregisterContentObserver(mContactObserver);
	}

	private void registerXMPPService() {
		Log.i(TAG, "called startXMPPService()");
		mServiceIntent = new Intent(this, XMPPService.class);
		Uri chatURI = Uri.parse(mWithJabberID);
		mServiceIntent.setData(chatURI);
		mServiceIntent.setAction("org.yaxim.androidclient.XMPPSERVICE");

		mServiceConnection = new ServiceConnection() {

			public void onServiceConnected(ComponentName name, IBinder service) {
				Log.i(TAG, "called onServiceConnected()");
				mServiceAdapter = new XMPPChatServiceAdapter(
						IXMPPChatService.Stub.asInterface(service),
						mWithJabberID);
				
				mServiceAdapter.clearNotifications(mWithJabberID);
				updateContactStatus();
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
		Intent i = getIntent();
		mChatInput = (EditText) findViewById(R.id.Chat_UserInput);
		mChatInput.addTextChangedListener(this);
		if (i.hasExtra(INTENT_EXTRA_MESSAGE)) {
			mChatInput.setText(i.getExtras().getString(INTENT_EXTRA_MESSAGE));
		}
	}

	private void setContactFromUri() {
		Intent i = getIntent();
		mWithJabberID = i.getDataString().toLowerCase();
		if (i.hasExtra(INTENT_EXTRA_USERNAME)) {
			mUserScreenName = i.getExtras().getString(INTENT_EXTRA_USERNAME);
		} else {
			mUserScreenName = mWithJabberID;
		}
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenu.ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);

		View target = ((AdapterContextMenuInfo)menuInfo).targetView;
		TextView from = (TextView)target.findViewById(R.id.chat_from);
		getMenuInflater().inflate(R.menu.chat_contextmenu, menu);
		if (!from.getText().equals(getString(R.string.chat_from_me))) {
			menu.findItem(R.id.chat_contextmenu_resend).setEnabled(false);
		}
	}

	private CharSequence getMessageFromContextMenu(MenuItem item) {
		View target = ((AdapterContextMenuInfo)item.getMenuInfo()).targetView;
		TextView message = (TextView)target.findViewById(R.id.chat_message);
		return message.getText();
	}

	public boolean onContextItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.chat_contextmenu_copy_text:
			ClipboardManager cm = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
			cm.setText(getMessageFromContextMenu(item));
			return true;
		case R.id.chat_contextmenu_resend:
			sendMessage(getMessageFromContextMenu(item).toString());
			Log.d(TAG, "resend!");
			return true;
		default:
			return super.onContextItemSelected((android.view.MenuItem) item);
		}
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
			sendMessage(mChatInput.getText().toString());
		}
	}

	private void sendMessage(String message) {
		mChatInput.setText(null);
		mSendButton.setEnabled(false);
		mServiceAdapter.sendMessage(mWithJabberID, message);
		if (!mServiceAdapter.isServiceAuthenticated())
			showToastNotification(R.string.toast_stored_offline);
	}

	private void markAsReadDelayed(final int id, final int delay) {
		new Thread() {
			@Override
			public void run() {
				try { Thread.sleep(delay); } catch (Exception e) {}
				markAsRead(id);
			}
		}.start();
	}
	
	private void markAsRead(int id) {
		Uri rowuri = Uri.parse("content://" + ChatProvider.AUTHORITY
			+ "/" + ChatProvider.TABLE_NAME + "/" + id);
		Log.d(TAG, "markAsRead: " + rowuri);
		ContentValues values = new ContentValues();
		values.put(ChatConstants.DELIVERY_STATUS, ChatConstants.DS_SENT_OR_READ);
		getContentResolver().update(rowuri, values, null, null);
	}

	class ChatWindowAdapter extends SimpleCursorAdapter {
		String mScreenName, mJID;

		ChatWindowAdapter(Cursor cursor, String[] from, int[] to,
				String JID, String screenName) {
			super(ChatWindow.this, android.R.layout.simple_list_item_1, cursor,
					from, to);
			mScreenName = screenName;
			mJID = JID;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View row = convertView;
			ChatItemWrapper wrapper = null;
			Cursor cursor = this.getCursor();
			cursor.moveToPosition(position);

			long dateMilliseconds = cursor.getLong(cursor
					.getColumnIndex(ChatProvider.ChatConstants.DATE));

			int _id = cursor.getInt(cursor
					.getColumnIndex(ChatProvider.ChatConstants._ID));
			String date = getDateString(dateMilliseconds);
			String message = cursor.getString(cursor
					.getColumnIndex(ChatProvider.ChatConstants.MESSAGE));
			boolean from_me = (cursor.getInt(cursor
					.getColumnIndex(ChatProvider.ChatConstants.DIRECTION)) ==
					ChatConstants.OUTGOING);
			String jid = cursor.getString(cursor
					.getColumnIndex(ChatProvider.ChatConstants.JID));
			int delivery_status = cursor.getInt(cursor
					.getColumnIndex(ChatProvider.ChatConstants.DELIVERY_STATUS));

			if (row == null) {
				LayoutInflater inflater = getLayoutInflater();
				row = inflater.inflate(R.layout.chatrow, null);
				wrapper = new ChatItemWrapper(row);
				row.setTag(wrapper);
			} else {
				wrapper = (ChatItemWrapper) row.getTag();
			}

			if (!from_me && delivery_status == ChatConstants.DS_NEW) {
				markAsReadDelayed(_id, DELAY_NEWMSG);
			}

			final String from;
			if (!from_me) {
				from = jid.equals(mJID) ? mScreenName : jid;
			} else {
				from = null;
			}
			wrapper.populateFrom(date, from, message, delivery_status);

			return row;
		}
	}

	private String getDateString(long milliSeconds) {
		SimpleDateFormat dateFormater = new SimpleDateFormat("yy-MM-dd HH:mm:ss");
		Date date = new Date(milliSeconds);
		return dateFormater.format(date);
	}

	public class ChatItemWrapper {
		private final TextView dateView;
		private final TextView fromView;
		private final TextView messageView;
		private final ImageView iconView;

		private final int myColor;
		private final int yourColor;

		private final View mRowView;
		private final TypedValue mValue;

		ChatItemWrapper(View row) {
			mRowView = row;

			dateView = (TextView) mRowView.findViewById(R.id.chat_date);
			fromView = (TextView) mRowView.findViewById(R.id.chat_from);
			messageView = (TextView) mRowView.findViewById(R.id.chat_message);
			iconView = (ImageView) mRowView.findViewById(R.id.iconView);

			// Setup theme colors (since we just reuse them)
			mValue = new TypedValue();
			getTheme().resolveAttribute(R.attr.ChatMsgHeaderMeColor, mValue, true);
			myColor = mValue.data;

			getTheme().resolveAttribute(R.attr.ChatMsgHeaderYouColor, mValue, true);
			yourColor = mValue.data;
		}

		void populateFrom(String date, String from, String message, int deliveryStatus) {
			final boolean fromMe = from == null;

			dateView.setText(date);
			dateView.setTextColor(fromMe ? myColor : yourColor);

			final String fromText = fromMe
					? getString(R.string.chat_from_me)
					: getString(R.string.chat_from_you, from);
			fromView.setText(fromText);
			fromView.setTextColor(fromMe ? myColor : yourColor);

			if (message.startsWith("/me ")) {
				message = "\u25CF " + message.replaceFirst("^/me ",
						(fromMe ? getString(R.string.chat_action_from_me) : from) + " ");
				messageView.setTypeface(null, android.graphics.Typeface.ITALIC);
			} else {
				messageView.setTypeface(null, 0);
			}
			messageView.setText(message);

			switch (deliveryStatus) {
			case ChatConstants.DS_NEW:
				ColorDrawable layers[] = new ColorDrawable[2];
				getTheme().resolveAttribute(R.attr.ChatNewMessageColor, mValue, true);
				layers[0] = new ColorDrawable(mValue.data);
				if (fromMe) {
					// message stored for later transmission
					getTheme().resolveAttribute(R.attr.ChatStoredMessageColor, mValue, true);
					layers[1] = new ColorDrawable(mValue.data);
				} else {
					layers[1] = new ColorDrawable(0x00000000);
				}
				TransitionDrawable backgroundColorAnimation = new TransitionDrawable(layers);
				mRowView.setBackgroundDrawable(backgroundColorAnimation);
				backgroundColorAnimation.setCrossFadeEnabled(true);
				backgroundColorAnimation.startTransition(DELAY_NEWMSG);
				iconView.setImageResource(R.drawable.ic_chat_msg_status_queued);
				break;
			case ChatConstants.DS_SENT_OR_READ:
				iconView.setImageResource(R.drawable.ic_chat_msg_status_unread);
				mRowView.setBackgroundColor(0x00000000); // default is transparent
				break;
			case ChatConstants.DS_ACKED:
				iconView.setImageResource(R.drawable.ic_chat_msg_status_ok);
				mRowView.setBackgroundColor(0x00000000); // default is transparent
				break;
			case ChatConstants.DS_FAILED:
				iconView.setImageResource(R.drawable.ic_chat_msg_status_failed);
				mRowView.setBackgroundColor(0x30ff0000); // default is transparent
				break;
			case ChatConstants.DS_OTR_START:
				iconView.setImageResource(R.drawable.ic_chat_msg_status_ok);
				mRowView.setBackgroundColor(0x00000000); // default is transparent
				messageView.setText(getString(R.string.chat_attempt_otr_start, from));
				messageView.setTypeface(null, android.graphics.Typeface.ITALIC);
				break;
			case ChatConstants.DS_OTR_UNKNOWN:
				iconView.setImageResource(R.drawable.ic_chat_msg_status_failed);
				mRowView.setBackgroundColor(0x30ff0000); // default is transparent
				messageView.setText(getString(R.string.chat_unreadable_message));
				messageView.setTypeface(null, android.graphics.Typeface.ITALIC);
				break;
			}

			messageView.setTextSize(TypedValue.COMPLEX_UNIT_SP, mChatFontSize);
			dateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, mChatFontSize * 2/3);
			dateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, mChatFontSize * 2/3);
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

	private void showToastNotification(int message) {
		Toast toastNotification = Toast.makeText(this, message,
				Toast.LENGTH_SHORT);
		toastNotification.show();
	}

	@Override
	public boolean onOptionsItemSelected(com.actionbarsherlock.view.MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			Intent intent = new Intent(this, MainWindow.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(intent);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	private static final String[] STATUS_QUERY = new String[] {
		RosterProvider.RosterConstants.ALIAS,
		RosterProvider.RosterConstants.STATUS_MODE,
		RosterProvider.RosterConstants.STATUS_MESSAGE,
	};
	private void updateContactStatus() {
		Cursor cursor = getContentResolver().query(RosterProvider.CONTENT_URI, STATUS_QUERY,
					RosterProvider.RosterConstants.JID + " = ?", new String[] { mWithJabberID }, null);
		int ALIAS_IDX = cursor.getColumnIndex(RosterProvider.RosterConstants.ALIAS);
		int MODE_IDX = cursor.getColumnIndex(RosterProvider.RosterConstants.STATUS_MODE);
		int MSG_IDX = cursor.getColumnIndex(RosterProvider.RosterConstants.STATUS_MESSAGE);

		if (cursor.getCount() == 1) {
			cursor.moveToFirst();
			int status_mode = cursor.getInt(MODE_IDX);
			String status_message = cursor.getString(MSG_IDX);
			Log.d(TAG, "contact status changed: " + status_mode + " " + status_message);
			mTitle.setText(cursor.getString(ALIAS_IDX));
			mSubTitle.setVisibility((status_message != null && status_message.length() != 0)?
					View.VISIBLE : View.GONE);
			mSubTitle.setText(status_message);
			if (mServiceAdapter == null || !mServiceAdapter.isServiceAuthenticated())
				status_mode = 0; // override icon if we are offline
			mStatusMode.setImageResource(StatusMode.values()[status_mode].getDrawableId());
		}
		cursor.close();
	}

	private class ContactObserver extends ContentObserver {
		public ContactObserver() {
			super(new Handler());
		}

		public void onChange(boolean selfChange) {
			Log.d(TAG, "ContactObserver.onChange: " + selfChange);
			updateContactStatus();
		}
	}
}
