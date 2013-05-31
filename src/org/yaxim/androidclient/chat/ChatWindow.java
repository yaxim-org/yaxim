package org.yaxim.androidclient.chat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

import org.jivesoftware.smack.Chat;
import org.yaxim.androidclient.MainWindow;
import org.yaxim.androidclient.R;
import org.yaxim.androidclient.YaximApplication;
import org.yaxim.androidclient.data.ChatProvider;
import org.yaxim.androidclient.data.ChatProvider.ChatConstants;
import org.yaxim.androidclient.data.RosterProvider;
import org.yaxim.androidclient.data.YaximConfiguration;
import org.yaxim.androidclient.service.IXMPPChatService;
import org.yaxim.androidclient.service.IXMPPMucService;
import org.yaxim.androidclient.service.XMPPService;
import org.yaxim.androidclient.util.StatusMode;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockListActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.Window;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.ColorStateList;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Handler;
import android.os.RemoteException;
import android.text.ClipboardManager;
import android.text.Editable;
import android.text.Spannable;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.style.URLSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnKeyListener;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
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
			ChatProvider.ChatConstants.RESOURCE, ChatProvider.ChatConstants.MESSAGE, 
			ChatProvider.ChatConstants.DELIVERY_STATUS };

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
	private Intent mChatServiceIntent;
	private Intent mMucServiceIntent;
	private ServiceConnection mChatServiceConnection;
	private ServiceConnection mMucServiceConnection;
	private XMPPChatServiceAdapter mChatServiceAdapter;
	private XMPPMucServiceAdapter mMucServiceAdapter;
	private int mChatFontSize;
	private ActionBar actionBar;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		setContactFromUri();
		Log.d(TAG, "onCreate, registering XMPP service");
		registerXMPPService();
		Log.d(TAG, "onCreate, registered xmpp service, is: "+mMucServiceAdapter+" and "+mChatServiceAdapter);

		setTheme(YaximApplication.getConfig(this).getTheme());
		super.onCreate(savedInstanceState);
		
		mChatFontSize = Integer.valueOf(YaximApplication.getConfig(this).chatFontSize);

		requestWindowFeature(Window.FEATURE_ACTION_BAR);
		setContentView(R.layout.mainchat);

		
		actionBar = getSupportActionBar();
		actionBar.setHomeButtonEnabled(true);
		actionBar.setDisplayHomeAsUpEnabled(true);

		Log.d(TAG, "registrs for contextmenu...");
		registerForContextMenu(getListView());
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

	@Override
	protected void onResume() {
		super.onResume();
		updateContactStatus();
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus)
			bindXMPPService();
		else
			unbindXMPPService();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (hasWindowFocus()) unbindXMPPService();
		getContentResolver().unregisterContentObserver(mContactObserver);
	}

	private void registerXMPPService() {
		Log.i(TAG, "called startXMPPService()");
		mChatServiceIntent = new Intent(this, XMPPService.class);
		Uri chatURI = Uri.parse(mWithJabberID);
		mChatServiceIntent.setData(chatURI);
		mChatServiceIntent.setAction("org.yaxim.androidclient.XMPPSERVICE");
		
		mChatServiceConnection = new ServiceConnection() {
			public void onServiceConnected(ComponentName name, IBinder service) {
				Log.i(TAG, "called onServiceConnected() (for ChatService)");
				mChatServiceAdapter = new XMPPChatServiceAdapter(
						IXMPPChatService.Stub.asInterface(service),
						mWithJabberID);
				
				mChatServiceAdapter.clearNotifications(mWithJabberID);
			}

			public void onServiceDisconnected(ComponentName name) {
				Log.i(TAG, "called onServiceDisconnected() (for ChatService)");
			}

		};
		
		mMucServiceIntent = new Intent(this, XMPPService.class);
		Uri dtaUri = Uri.parse(mWithJabberID+"?chat");
		mMucServiceIntent.setData(dtaUri);
		mMucServiceIntent.setAction("org.yaxim.androidclient.XMPPSERVICE");

		mMucServiceConnection = new ServiceConnection() {
			public void onServiceConnected(ComponentName name, IBinder service) {
				mMucServiceAdapter = new XMPPMucServiceAdapter(
						IXMPPMucService.Stub.asInterface(service), 
						mWithJabberID);
				supportInvalidateOptionsMenu();
			}
			public void onServiceDisconnected(ComponentName name) {
			}
		};
	
	}

	private void unbindXMPPService() {
		try {
			unbindService(mChatServiceConnection);
			unbindService(mMucServiceConnection);
		} catch (IllegalArgumentException e) {
			Log.e(TAG, "Service wasn't bound!");
		}
	}

	private void bindXMPPService() {
		bindService(mChatServiceIntent, mChatServiceConnection, BIND_AUTO_CREATE);
		bindService(mMucServiceIntent, mMucServiceConnection, BIND_AUTO_CREATE);
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
		Log.d(TAG, "setting contact from URI: "+mWithJabberID);
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

	private CharSequence getMessageFromContextMenu(android.view.MenuItem item) {
		View target = ((AdapterContextMenuInfo)item.getMenuInfo()).targetView;
		TextView message = (TextView)target.findViewById(R.id.chat_message);
		return message.getText();
	}

	@Override
	public boolean onContextItemSelected(android.view.MenuItem item) {
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
	

	@Override
	public boolean onCreateOptionsMenu(com.actionbarsherlock.view.Menu menu) {
		if(mMucServiceAdapter != null && mMucServiceAdapter.isRoom()) {
			Log.d(TAG, "creating options menu, we're a muc");
			MenuInflater inflater = getSupportMenuInflater(); 
			inflater.inflate(R.menu.chat_options, menu);
			return true;
		}
		return false;
	}

	@Override
	public boolean onPrepareOptionsMenu(com.actionbarsherlock.view.Menu menu) {
		Log.d(TAG, "preparing options menu "+mMucServiceAdapter);
		if(mMucServiceAdapter != null && mMucServiceAdapter.isRoom()) {
			Log.d(TAG, "prepare mucserviceadapter thinks we're are room");
			com.actionbarsherlock.view.MenuItem item = menu.findItem(R.id.chat_optionsmenu_userlist); // TODO: find new icon
			//TypedValue tv = new TypedValue();
			//getTheme().resolveAttribute(R.attr.AllFriends, tv, true);
			item.setIcon(R.drawable.ic_groupchat); // TODO: make themed
			item.setTitle(R.string.Menu_userlist);
			return true;
		} 
		return false;
	}
	
	@Override
	public boolean onOptionsItemSelected(com.actionbarsherlock.view.MenuItem item) {
		Log.d(TAG, "options item selected");
		switch (item.getItemId()) {
		case android.R.id.home:
			Intent intent = new Intent(this, MainWindow.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(intent);
			return true;
		case R.id.chat_optionsmenu_userlist:
			showUserList();
			return true;
		default:
			return super.onOptionsItemSelected(item);
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
		mChatServiceAdapter.sendMessage(mWithJabberID, message);
		if (!mChatServiceAdapter.isServiceAuthenticated())
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
			String resource = cursor.getString(
					cursor.getColumnIndex(ChatProvider.ChatConstants.RESOURCE)
					);
			int delivery_status = cursor.getInt(cursor
					.getColumnIndex(ChatProvider.ChatConstants.DELIVERY_STATUS));

			if (row == null) {
				LayoutInflater inflater = getLayoutInflater();
				row = inflater.inflate(R.layout.chatrow, null);
				wrapper = new ChatItemWrapper(row, ChatWindow.this);
				row.setTag(wrapper);
			} else {
				wrapper = (ChatItemWrapper) row.getTag();
			}

			if (!from_me && delivery_status == ChatConstants.DS_NEW) {
				markAsReadDelayed(_id, DELAY_NEWMSG);
			}

			String from = jid;
			if (jid.equals(mJID))
				from = mScreenName;
			from=from+"/"+resource;
			if(mMucServiceAdapter != null && mMucServiceAdapter.isRoom()) {
				from = resource;
			}
			wrapper.populateFrom(date, from_me, from, message, delivery_status);
			return row;
		}
	}

	private String getDateString(long milliSeconds) {
		SimpleDateFormat dateFormater = new SimpleDateFormat("yy-MM-dd HH:mm:ss");
		Date date = new Date(milliSeconds);
		return dateFormater.format(date);
	}

	public class ChatItemWrapper {
		private TextView mDateView = null;
		private TextView mFromView = null;
		private TextView mMessageView = null;
		private ImageView mIconView = null;

		private final View mRowView;
		private ChatWindow chatWindow;

		ChatItemWrapper(View row, ChatWindow chatWindow) {
			this.mRowView = row;
			this.chatWindow = chatWindow;
		}


		void populateFrom(String date, boolean from_me, String from, String message,
				int delivery_status) {
			getDateView().setText(date);
			TypedValue tv = new TypedValue();
			if (from_me) {
				getTheme().resolveAttribute(R.attr.ChatMsgHeaderMeColor, tv, true);
				getDateView().setTextColor(tv.data);
				getFromView().setText(getString(R.string.chat_from_me));
				getFromView().setTextColor(tv.data);
			} else if(mMucServiceAdapter != null && mMucServiceAdapter.isRoom()) {
				getTheme().resolveAttribute(R.attr.ChatMsgHeaderYouColor, tv, true);
				getDateView().setTextColor(nick2Color(from));
				getFromView().setText(from + ":");
				getFromView().setTextColor(nick2Color(from));
				
			} else {
				getTheme().resolveAttribute(R.attr.ChatMsgHeaderYouColor, tv, true);
				getDateView().setTextColor(tv.data);
				getFromView().setText(from + ":");
				getFromView().setTextColor(tv.data);
			}
			switch (delivery_status) {
			case ChatConstants.DS_NEW:
				ColorDrawable layers[] = new ColorDrawable[2];
				getTheme().resolveAttribute(R.attr.ChatNewMessageColor, tv, true);
				layers[0] = new ColorDrawable(tv.data);
				if (from_me) {
					// message stored for later transmission
					getTheme().resolveAttribute(R.attr.ChatStoredMessageColor, tv, true);
					layers[1] = new ColorDrawable(tv.data);
				} else {
					layers[1] = new ColorDrawable(0x00000000);
				}
				TransitionDrawable backgroundColorAnimation = new
					TransitionDrawable(layers);
				int l = mRowView.getPaddingLeft();
				int t = mRowView.getPaddingTop();
				int r = mRowView.getPaddingRight();
				int b = mRowView.getPaddingBottom();
				mRowView.setBackgroundDrawable(backgroundColorAnimation);
				mRowView.setPadding(l, t, r, b);
				backgroundColorAnimation.setCrossFadeEnabled(true);
				backgroundColorAnimation.startTransition(DELAY_NEWMSG);
				getIconView().setImageResource(R.drawable.ic_chat_msg_status_queued);
				break;
			case ChatConstants.DS_SENT_OR_READ:
				getIconView().setImageResource(R.drawable.ic_chat_msg_status_unread);
				mRowView.setBackgroundColor(0x00000000); // default is transparent
				break;
			case ChatConstants.DS_ACKED:
				getIconView().setImageResource(R.drawable.ic_chat_msg_status_ok);
				mRowView.setBackgroundColor(0x00000000); // default is transparent
				break;
			case ChatConstants.DS_FAILED:
				getIconView().setImageResource(R.drawable.ic_chat_msg_status_failed);
				mRowView.setBackgroundColor(0x30ff0000); // default is transparent
				break;
			}
			getMessageView().setText(message);
			getMessageView().setTextSize(TypedValue.COMPLEX_UNIT_SP, chatWindow.mChatFontSize);
			getDateView().setTextSize(TypedValue.COMPLEX_UNIT_SP, chatWindow.mChatFontSize*2/3);
			getFromView().setTextSize(TypedValue.COMPLEX_UNIT_SP, chatWindow.mChatFontSize*2/3);
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

		ImageView getIconView() {
			if (mIconView == null) {
				mIconView = (ImageView) mRowView
						.findViewById(R.id.iconView);
			}
			return mIconView;
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

	private void showUserList() {
		if(mMucServiceAdapter != null && mMucServiceAdapter.isRoom()) {
			final String[] users = mMucServiceAdapter.getUserList();
			AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(ChatWindow.this)
									.setTitle("Users in room "+mWithJabberID)
									.setNeutralButton("Close", null);
			
			ArrayAdapter<String> adapter = new ArrayAdapter<String>(ChatWindow.this, R.layout.single_string) {
			    @Override
			    public View getView(int position, View convertView, ViewGroup parent) {
			        View v = super.getView(position, convertView, parent);
			        ((TextView) v).setTextColor( nick2Color(getItem(position)) );
			        return v;
			    }
			};
			for(String user : users)
				adapter.add(user);
			Log.d(TAG, "adapter has values: "+adapter.getCount());
			dialogBuilder.setAdapter(adapter, new OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					String postfix=mChatInput.getSelectionStart()==0 ? ", " : ""; 
					mChatInput.getText().insert(mChatInput.getSelectionStart(), users[which]+postfix);
				}
			});
			AlertDialog dialog = dialogBuilder.create();
			dialog.show();
		}
	}

	
	private static final String[] STATUS_QUERY = new String[] {
		RosterProvider.RosterConstants.STATUS_MODE,
		RosterProvider.RosterConstants.STATUS_MESSAGE,
	};
	private void updateContactStatus() {
		Cursor cursor = getContentResolver().query(RosterProvider.CONTENT_URI, STATUS_QUERY,
					RosterProvider.RosterConstants.JID + " = ?", new String[] { mWithJabberID }, null);
		int MODE_IDX = cursor.getColumnIndex(RosterProvider.RosterConstants.STATUS_MODE);
		int MSG_IDX = cursor.getColumnIndex(RosterProvider.RosterConstants.STATUS_MESSAGE);

		if (cursor.getCount() == 1) {
			cursor.moveToFirst();
			int status_mode = cursor.getInt(MODE_IDX);
			String status_message = cursor.getString(MSG_IDX);
			Log.d(TAG, "contact status changed: " + status_mode + " " + status_message);
			mSubTitle.setVisibility((status_message != null && status_message.length() != 0)?
					View.VISIBLE : View.GONE);
			mSubTitle.setText(status_message);
			if (mServiceAdapter == null || !mServiceAdapter.isServiceAuthenticated())
				status_mode = 0; // override icon if we are offline
			mStatusMode.setImageResource(StatusMode.values()[status_mode].getDrawableId());
		}
		cursor.close();
	}

	final int nick2Color(String nick) {
		nick = nick.toLowerCase();
		
		Checksum nickCRC = new CRC32();
		nickCRC.update(nick.getBytes(), 0, nick.length());
		int nickInt = (int)nickCRC.getValue();
		Random rand = new Random(nickInt);
		float r1 = -0.15f + ( rand.nextFloat() * (0.15f - -0.15f) );
		float r2 = -0.1f + ( rand.nextFloat() * (0.1f - -0.1f) );
		int blueShift = rand.nextBoolean() ? 45 : -45;
		
		float h, s, v;
		h=Math.abs( nickInt%360 );

		s=0.5f; v=0.5f;
		if(YaximApplication.getConfig(this).getTheme() == R.style.YaximDarkTheme) {
			s=0.75f + r1;
			v=0.9f + r2;
			if(h<=255.0f && h>=225.0f) {
				h = h + blueShift;
			}
		} else if(YaximApplication.getConfig(this).getTheme() == R.style.YaximLightTheme) {
			s=0.7f + r1; 
			v=0.8f + r2;
		}
		
		/*Log.d(TAG, String.format(
				"nick2Color(%s): nickInt: %d, r1: %f, r2: %f, noBlue: %s, h: %f, s: %f, v: %f", 
				nick, nickInt, r1, r2, blueShift, h, s, v));*/
		
		int nickColor = Color.HSVToColor(0xFF, new float[]{h, s, v});
		
		return nickColor;
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
