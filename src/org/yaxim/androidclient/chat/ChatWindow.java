package org.yaxim.androidclient.chat;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.*;
import android.os.*;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.MenuInflater;

import org.yaxim.androidclient.FileHttpUploadTask;
import org.yaxim.androidclient.MainWindow;
import org.yaxim.androidclient.R;
import org.yaxim.androidclient.YaximApplication;
import org.yaxim.androidclient.data.ChatHelper;
import org.yaxim.androidclient.data.ChatProvider;
import org.yaxim.androidclient.data.ChatProvider.ChatConstants;
import org.yaxim.androidclient.data.RosterProvider;
import org.yaxim.androidclient.data.YaximConfiguration;
import org.yaxim.androidclient.service.IXMPPChatService;
import org.yaxim.androidclient.service.XMPPService;
import org.yaxim.androidclient.util.FileHelper;
import org.yaxim.androidclient.util.MessageStylingHelper;
import org.yaxim.androidclient.util.StatusMode;
import org.yaxim.androidclient.util.XMPPHelper;

import eu.siacs.conversations.utils.StylingHelper;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.Window;

import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.Uri;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.text.ClipboardManager;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.StyleSpan;
import android.text.util.Linkify;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnKeyListener;
import android.view.inputmethod.InputMethodManager;
import android.view.WindowManager;
import android.widget.*;
import android.widget.AdapterView.AdapterContextMenuInfo;

@SuppressWarnings("deprecation") /* recent ClipboardManager only available since API 11 */
public class ChatWindow extends SherlockFragmentActivity implements OnKeyListener,
		TextWatcher, LoaderManager.LoaderCallbacks<Cursor>, AbsListView.OnScrollListener {

	private static final int REQUEST_FILE = 1;
	private static final int REQUEST_IMAGE = 2;
	private static final int REQUEST_CAMERA = 3;

	public static final String INTENT_EXTRA_USERNAME = ChatWindow.class.getName() + ".username";
	public static final String INTENT_EXTRA_MESSAGE = ChatWindow.class.getName() + ".message";
	
	private static final String TAG = "yaxim.ChatWindow";
	private static final String[] PROJECTION_FROM = new String[] {
			ChatConstants._ID, ChatConstants.DATE,
			ChatConstants.DIRECTION, ChatConstants.JID,
			ChatConstants.RESOURCE, ChatConstants.MESSAGE,
			ChatConstants.ERROR,
			ChatConstants.DELIVERY_STATUS };

	private static final int[] PROJECTION_TO = new int[] { R.id.chat_date,
			R.id.chat_from, R.id.chat_message, R.id.chat_error };
	
	private static final int DELAY_NEWMSG = 3000;
	private static final int CHAT_MSG_LOADER = 0;
	private int lastlog_size = 200;
	private int lastlog_index = -1;
	private Uri cameraPictureUri = null;

	protected YaximConfiguration mConfig;
	private ContentObserver mContactObserver = new ContactObserver();
	private ImageView mStatusMode;
	private TextView mTitle;
	private TextView mSubTitle;
	private Button mSendButton = null;
	private ProgressBar mLoadingProgress;
	protected EditText mChatInput = null;
	protected String mWithJabberID = null;
	protected String mUserScreenName = null;
	private boolean isContact = false;
	private Intent mChatServiceIntent;
	private ServiceConnection mChatServiceConnection;
	private XMPPChatServiceAdapter mChatServiceAdapter;
	private int mChatFontSize;
	private ActionBar actionBar;
	private ListView mListView;
	protected ChatWindowAdapter mChatAdapter;

	volatile boolean mMarkRunnableQuit = false;
	private Runnable mMarkRunnable = new Runnable() {
		@Override
		public void run() {
			Log.d(TAG, "mMarkRunnable: running...");
			markReadMessagesInDb();
			Log.d(TAG, "mMarkRunnable: done...");
			if (mMarkRunnableQuit)
				mMarkThread.quit();
		}
	};
	private HandlerThread mMarkThread;
	private Handler mMarkHandler;
	private final HashSet<Integer> mReadMessages = new HashSet<Integer>();

	private boolean mShowOrHide = true;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		mConfig = YaximApplication.getConfig(this);
		setContactFromUri();
		Log.d(TAG, "onCreate, registering XMPP service");
		registerXMPPService();

		setTheme(mConfig.getTheme());
		super.onCreate(savedInstanceState);
		XMPPHelper.setStaticNFC(this, "xmpp:" + java.net.URLEncoder.encode(mWithJabberID) + "?roster;name=" + java.net.URLEncoder.encode(mUserScreenName));

		mChatFontSize = Integer.valueOf(mConfig.chatFontSize);

		requestWindowFeature(Window.FEATURE_ACTION_BAR);
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);

		setContentView(R.layout.mainchat);

		getContentResolver().registerContentObserver(RosterProvider.CONTENT_URI,
				true, mContactObserver);
		
		actionBar = getSupportActionBar();
		actionBar.setHomeButtonEnabled(true);
		actionBar.setDisplayHomeAsUpEnabled(true);

		// Setup the actual chat view
		mListView = (ListView) findViewById(android.R.id.list);
		mChatAdapter = new ChatWindowAdapter(null, PROJECTION_FROM, PROJECTION_TO,
				mWithJabberID, null);
		mListView.setAdapter(mChatAdapter);
		mListView.setOnScrollListener(this);

		Log.d(TAG, "registrs for contextmenu...");
		registerForContextMenu(mListView);
		setSendButton();
		setUserInput();
		
		String titleUserid;
		if (mUserScreenName != null) {
			titleUserid = mUserScreenName;
		} else {
			titleUserid = mWithJabberID;
		}

		setCustomTitle(titleUserid);

		// Setup the loader
		getSupportLoaderManager().initLoader(CHAT_MSG_LOADER, null, this);

		// Loading progress
		mLoadingProgress = (ProgressBar) findViewById(R.id.loading_progress);
		mLoadingProgress.setVisibility(View.VISIBLE);

		mMarkThread = new HandlerThread("MarkAsReadThread: " + mWithJabberID);
		mMarkThread.start();
		mMarkHandler = new Handler(mMarkThread.getLooper());
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

	@Override
	public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
		// There's only one Loader, so ...
		if (i == CHAT_MSG_LOADER) {
			String selection = null;
			Uri lastlog = new Uri.Builder().scheme("content").authority(ChatProvider.AUTHORITY)
				.appendPath("chats")
				.appendPath(mWithJabberID).appendPath(String.valueOf(lastlog_size))
				.build();
			return new CursorLoader(this, lastlog, PROJECTION_FROM,
					selection, null, "date");
		} else {
			Log.w(TAG, "Unknown loader id returned in LoaderCallbacks.onCreateLoader: " + i);
			return null;
		}
	}

	@Override
	public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
		mLoadingProgress.setVisibility(View.GONE);
		mChatAdapter.changeCursor(cursor);

		// Only do this the first time (show or hide the keyboard)
		if (mShowOrHide) {
			if (cursor.getCount() == 0) {
				showKeyboard();
			}
			mShowOrHide = false;
		}

		// correct position after loading more lastlog
		if (lastlog_index >= 0) {
			int delta = 1 + mChatAdapter.getCursor().getCount() - lastlog_index;
			mListView.setSelection(delta);
			lastlog_index = -1;
		}
	}

	@Override
	public void onLoaderReset(Loader<Cursor> cursorLoader) {
		// Make sure we don't leak the (memory of the) cursor
		mChatAdapter.changeCursor(null);
	}

	public void increaseLastLog() {
		// only trigger this if we already have a cursor and that was LIMITed by lastlog_size
		if (mChatAdapter.getCursor() != null && mChatAdapter.getCursor().getCount() == lastlog_size) {
			Log.d(TAG, "increaseLastLog: " + mChatAdapter.getCursor().getCount());
			lastlog_size += 200;
			lastlog_index = mChatAdapter.getCursor().getCount();
			getSupportLoaderManager().restartLoader(CHAT_MSG_LOADER, null, this /*LoaderCallbacks<Cursor>*/);
		}
	}

	/* AbsListView.OnScrollListener */
	@Override
	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
		// re-query the lastlog when reaching the first item
		if (visibleItemCount > 0 && firstVisibleItem == 0)
			increaseLastLog();
	}
	@Override
	public void onScrollStateChanged (AbsListView view, int scrollState) {
		// ignore, not needed for infinite scrolling
	}


	// onPause/onResume are not called on older Androids when the lockscreen is
	// right in front of a chat window. onWindowFocusChanged is toggled
	// when the MUC contacts are shown.
	// We need to count both events to reliably bind/unbind our service. Sigh.
	// We bind if at least one of them happens, and unbind when both are
	// reversed.
	protected int needs_to_bind_unbind = 0;

	protected void changeBoundness(int direction) {
		if (needs_to_bind_unbind == 0)
			bindXMPPService();
		needs_to_bind_unbind += direction;
		if (needs_to_bind_unbind == 0)
			unbindXMPPService();
	}

	public void sendFile(Uri path, int flags) {
		mChatServiceAdapter.sendFile(path, mWithJabberID, mChatInput.getText().toString(), flags);
		mChatInput.setText("");
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode != RESULT_OK)
			return;
		if (requestCode == REQUEST_FILE || requestCode == REQUEST_IMAGE) {
			Uri uri = data.getData();
			if (uri != null) {
				int flags = 0;
				if (requestCode == REQUEST_IMAGE)
					flags |= FileHttpUploadTask.F_RESIZE;
				sendFile(uri, flags);
			}
		} else if (requestCode == REQUEST_CAMERA && cameraPictureUri != null) {
			sendFile(cameraPictureUri, FileHttpUploadTask.F_RESIZE);
		}
	}

	@Override
	protected void onResume() {
		Log.d(TAG, "onResume");
		super.onResume();
		updateContactStatus();
		changeBoundness(+1);
	}

	@Override
	protected void onPause() {
		Log.d(TAG, "onPause");
		super.onPause();
		changeBoundness(-1);
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		Log.d(TAG, "onWindowFocusChanged: " + hasFocus);
		super.onWindowFocusChanged(hasFocus);
		changeBoundness(hasFocus ? +1 : -1);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		getContentResolver().unregisterContentObserver(mContactObserver);
		// XXX: quitSafely would be better, but needs API r18
		mMarkRunnableQuit = true;
		mMarkHandler.post(mMarkRunnable);
	}

	protected void registerXMPPService() {
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
				updateContactStatus();
				handleSendIntent();
			}

			public void onServiceDisconnected(ComponentName name) {
				Log.i(TAG, "called onServiceDisconnected() (for ChatService)");
			}

		};
	}

	protected void unbindXMPPService() {
		try {
			unbindService(mChatServiceConnection);
		} catch (IllegalArgumentException e) {
			Log.e(TAG, "Service wasn't bound!");
		}
	}

	protected void bindXMPPService() {
		bindService(mChatServiceIntent, mChatServiceConnection, BIND_AUTO_CREATE);
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
		mChatInput.setOnKeyListener(this);
		mChatInput.addTextChangedListener(new StylingHelper.MessageEditorStyler(mChatInput));
		if (i.hasExtra(INTENT_EXTRA_MESSAGE)) {
			mChatInput.setText(i.getExtras().getString(INTENT_EXTRA_MESSAGE));
			i.removeExtra(INTENT_EXTRA_MESSAGE);
		}
	}
	private void handleSendIntent() {
		Intent i = getIntent();
		if (i.hasExtra(Intent.EXTRA_STREAM)) {
			Uri stream = (Uri)i.getParcelableExtra(Intent.EXTRA_STREAM);
			sendFile(stream, FileHttpUploadTask.F_RESIZE);
			i.removeExtra(Intent.EXTRA_STREAM);
		}
	}

	private void setContactFromUri() {
		Intent i = getIntent();
		mWithJabberID = i.getDataString(); // TODO: lowercase bare-JID, stringprep-normalize
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

	private String getMessageFromContextMenu(android.view.MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo)item.getMenuInfo();
		Cursor c = (Cursor)mListView.getItemAtPosition(info.position);
		return c.getString(c.getColumnIndex(ChatProvider.ChatConstants.MESSAGE));
	}

	private String getQuotedMessageFromContextMenu(android.view.MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo)item.getMenuInfo();
		Cursor c = (Cursor)mListView.getItemAtPosition(info.position);
		boolean from_me = (c.getInt(c.getColumnIndex(ChatProvider.ChatConstants.DIRECTION)) ==
				ChatConstants.OUTGOING);
		String message = c.getString(c.getColumnIndex(ChatProvider.ChatConstants.MESSAGE));
		if (!from_me) {
			String jid = c.getString(c.getColumnIndex(ChatProvider.ChatConstants.JID));
			String resource = c.getString(c.getColumnIndex(ChatProvider.ChatConstants.RESOURCE));
			long timestamp = c.getLong(c.getColumnIndex(ChatProvider.ChatConstants.DATE));
			String ts = new SimpleDateFormat("HH:mm").format(new Date(timestamp));
			return String.format("%s [%s]:\n%s", jid2nickname(jid, resource), ts, XMPPHelper.quoteString(message));
		}
		return XMPPHelper.quoteString(message);
	}

	@Override
	public boolean onContextItemSelected(android.view.MenuItem item) {
		switch (item.getItemId()) {
		case R.id.chat_contextmenu_copy_text:
			ClipboardManager cm = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
			cm.setText(getMessageFromContextMenu(item));
			return true;
		case R.id.chat_contextmenu_quote:
			// insert quote into the current cursor position
			String quote = getQuotedMessageFromContextMenu(item);
			int position = Math.max(mChatInput.getSelectionStart(), 0);
			mChatInput.getText().insert(position, quote);
			position += quote.length();
			mChatInput.setSelection(position, position);
			Log.d(TAG, "quote!");
			return true;
		case R.id.chat_contextmenu_resend:
			sendMessage(getMessageFromContextMenu(item));
			Log.d(TAG, "resend!");
			return true;
		default:
			return super.onContextItemSelected((android.view.MenuItem) item);
		}
	}
	

	public boolean onCreateOptionsMenu(com.actionbarsherlock.view.Menu menu) {
		MenuInflater inflater = getSupportMenuInflater();
		if (isContact)
			inflater.inflate(R.menu.contact_options, menu);
		else
			inflater.inflate(R.menu.noncontact_options, menu);
		return inflateGenericContactOptions(menu);
	}

	public boolean inflateGenericContactOptions(com.actionbarsherlock.view.Menu menu) {
		MenuInflater inflater = getSupportMenuInflater();
		//inflater.inflate(R.menu.contact_options, menu);
		inflater.inflate(R.menu.roster_item_contextmenu, menu);
		if (mConfig.fileUploadDomain != null) {
			menu.findItem(R.id.roster_contextmenu_send).setVisible(true);
		}
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(com.actionbarsherlock.view.MenuItem item) {
		Log.d(TAG, "options item selected");
		switch (item.getItemId()) {
		case android.R.id.home:
			Intent intent = new Intent(this, MainWindow.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(intent);
			finish();
			return true;
		case R.id.roster_contextmenu_take_image:
			intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
			File tempFile = FileHelper.createImageFile(this);
			if (tempFile == null) {
				Toast.makeText(this, "Error creating file!", Toast.LENGTH_SHORT).show();
				return true;
			}
			cameraPictureUri = Uri.fromFile(tempFile);
			// TODO: wrap file Uri into a FileProvider to target Android M+
			intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraPictureUri);
			startActivityForResult(Intent.createChooser(intent, getString(R.string.roster_contextmenu_take_image)), REQUEST_CAMERA);
			return true;
		case R.id.roster_contextmenu_send_image:
			intent = new Intent(Intent.ACTION_GET_CONTENT);
			intent.setType("image/*");
			intent.addCategory(Intent.CATEGORY_OPENABLE);
			startActivityForResult(Intent.createChooser(intent, getString(R.string.roster_contextmenu_send_image)), REQUEST_IMAGE);
			return true;
		case R.id.roster_contextmenu_send_file:
			Intent fileIntent = new Intent(Intent.ACTION_GET_CONTENT);
			fileIntent.setType("*/*");
			fileIntent.addCategory(Intent.CATEGORY_OPENABLE);
			startActivityForResult(Intent.createChooser(fileIntent, getString(R.string.roster_contextmenu_send_file)), REQUEST_FILE);
			return true;

		// items that require an authenticated connection
		case R.id.roster_contextmenu_contact_delete:
		case R.id.roster_contextmenu_contact_rename:
		case R.id.roster_contextmenu_contact_request_auth:
		case R.id.roster_contextmenu_contact_change_group:
			if (!mChatServiceAdapter.isServiceAuthenticated()) { showToastNotification(R.string.Global_authenticate_first); return true; }
			// fall through to default handler
		default:
			return ChatHelper.handleJidOptions(this, item.getItemId(), mWithJabberID, mUserScreenName);
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

	private boolean markAsReadDelayed(final int id, final int delay) {
		if (mReadMessages.contains(id)) {
			return false;
		}
		mMarkHandler.removeCallbacks(mMarkRunnable);
		mReadMessages.add(id);
		mMarkHandler.postDelayed(mMarkRunnable, delay);
		return true;
	}
	
	private void markReadMessagesInDb() {
		if (mReadMessages.size() == 0)
			return;
		HashSet<Integer> hs = (HashSet)mReadMessages.clone();
		Uri rowuri = Uri.parse("content://" + ChatProvider.AUTHORITY
			+ "/" + ChatProvider.TABLE_NAME);
		// create custom WHERE statement instead of relying on ContentResolvers whereArgs
		StringBuilder where = new StringBuilder();
		where.append("_id IN (");
		for (int id : hs) {
			where.append(id);
			where.append(",");
		}
		// ',' --> ')'
		where.setCharAt(where.length()-1, ')');
		Log.d(TAG, "markAsRead: " + where);
		ContentValues values = new ContentValues();
		values.put(ChatConstants.DELIVERY_STATUS, ChatConstants.DS_SENT_OR_READ);
		getContentResolver().update(rowuri, values, where.toString(), null);
		// XXX: is this the right place?
		mReadMessages.removeAll(hs);
	}
	
	public String jid2nickname(String jid, String resource) {
		String from = jid;
		if (jid.equals(mWithJabberID))
			from = mUserScreenName;
		return from;
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
			String error = cursor.getString(cursor
					.getColumnIndex(ChatConstants.ERROR));
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
				if (!markAsReadDelayed(_id, DELAY_NEWMSG))
					delivery_status = ChatConstants.DS_SENT_OR_READ;

			}

			wrapper.populateFrom(date, from_me, jid2nickname(jid, resource), message, error, delivery_status, mScreenName);
			return row;
		}
	}

	private String getDateString(long milliSeconds) {
		SimpleDateFormat dateFormater = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Date date = new Date(milliSeconds);
		return dateFormater.format(date);
	}

	public class ChatItemWrapper {
		private TextView mDateView = null;
		private TextView mFromView = null;
		private TextView mMessageView = null;
		private TextView mErrorView = null;
		private ImageView mIconView = null;

		private final View mRowView;
		private ChatWindow chatWindow;

		ChatItemWrapper(View row, ChatWindow chatWindow) {
			this.mRowView = row;
			this.chatWindow = chatWindow;
		}


		void populateFrom(String date, boolean from_me, String from, String message,
				String error, int delivery_status, String highlight_text) {
			getDateView().setText(date);
			TypedValue tv = new TypedValue();
			if (from_me) {
				getTheme().resolveAttribute(R.attr.ChatMsgHeaderMeColor, tv, true);
				getFromView().setText(getString(R.string.chat_from_me));
				getFromView().setTextColor(tv.data);
				from = mConfig.userName;
			} else {
				nick2Color(from, tv);
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

			SpannableStringBuilder body = MessageStylingHelper.formatMessage(message,
					from, highlight_text, getMessageView().getCurrentTextColor());
			eu.siacs.conversations.utils.StylingHelper.handleTextQuotes(body, getMessageView().getCurrentTextColor(), getResources().getDisplayMetrics());
			MessageStylingHelper.applyEmojiScaling(body, 5.0f); /* >5.0 will exceed Emoji rendering limit of 150px */
			getMessageView().setText(body);

			getMessageView().setTextSize(TypedValue.COMPLEX_UNIT_SP, chatWindow.mChatFontSize);
			getDateView().setTextSize(TypedValue.COMPLEX_UNIT_SP, chatWindow.mChatFontSize*2/3);
			getFromView().setTextSize(TypedValue.COMPLEX_UNIT_SP, chatWindow.mChatFontSize*2/3);
			getErrorView().setTextSize(TypedValue.COMPLEX_UNIT_SP, chatWindow.mChatFontSize*2/3);
			getErrorView().setText(error);
			getErrorView().setVisibility(TextUtils.isEmpty(error) ? View.GONE : View.VISIBLE);
			// these calls must be in the exact right order.
			Linkify.addLinks(getMessageView(), Linkify.MAP_ADDRESSES | Linkify.WEB_URLS);
			// Android's default phone linkifuckation makes 13:37 two phone numbers
			Linkify.addLinks(getMessageView(), XMPPHelper.PHONE, "tel:");
			// Android's default email linkifuckation breaks xmpp: URIs
			Linkify.addLinks(getMessageView(), XMPPHelper.XMPP_PATTERN, "xmpp");
			Linkify.addLinks(getMessageView(), XMPPHelper.EMAIL_ADDRESS, "mailto:");
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
		TextView getErrorView() {
			if (mErrorView == null) {
				mErrorView = (TextView) mRowView
					.findViewById(R.id.chat_error);
			}
			return mErrorView;
		}

		ImageView getIconView() {
			if (mIconView == null) {
				mIconView = (ImageView) mRowView
						.findViewById(R.id.iconView);
			}
			return mIconView;
		}

	}

	// OnKeyListener
	@Override
	public boolean onKey(View v, int keyCode, KeyEvent event) {
		if (event.getAction() == KeyEvent.ACTION_DOWN
				&& keyCode == KeyEvent.KEYCODE_ENTER) {
			sendMessageIfNotNull();
			return true;
		}
		return false;

	}

	// TextWatcher
	@Override
	public void afterTextChanged(Editable s) {
		mSendButton.setEnabled(mChatInput.getText().length() >= 1);
	}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count,
			int after) {
	}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count) {
	}

	private void showToastNotification(int message) {
		Toast toastNotification = Toast.makeText(this, message,
				Toast.LENGTH_SHORT);
		toastNotification.show();
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

		isContact = cursor.getCount() == 1;
		if (isContact) {
			cursor.moveToFirst();
			int status_mode = cursor.getInt(MODE_IDX);
			if (status_mode == StatusMode.subscribe.ordinal())
				isContact = false;
			String status_message = cursor.getString(MSG_IDX);
			Log.d(TAG, "contact status changed: " + status_mode + " " + status_message);
			mTitle.setText(cursor.getString(ALIAS_IDX));
			mSubTitle.setVisibility((status_message != null && status_message.length() != 0)?
					View.VISIBLE : View.GONE);
			mSubTitle.setText(status_message);
			
			if (mChatServiceAdapter == null || !mChatServiceAdapter.isServiceAuthenticated())
				status_mode = 0; // override icon if we are offline
			mStatusMode.setImageResource(StatusMode.values()[status_mode].getDrawableId());
		}
		cursor.close();
		invalidateOptionsMenu();
	}

	// this method is a "virtual" placeholder for the MUC activity
	public void nick2Color(String nick, TypedValue tv) {
		getTheme().resolveAttribute(R.attr.ChatMsgHeaderYouColor, tv, true);
	}
	
	public ListView getListView() {
		return mListView;
	}

	private void showKeyboard() {
		mChatInput.requestFocus();
		new Handler(getMainLooper()).postDelayed(new Runnable() {
			@Override
			public void run() {
				InputMethodManager keyboard = (InputMethodManager)
						getSystemService(Context.INPUT_METHOD_SERVICE);
				keyboard.showSoftInput(mChatInput, InputMethodManager.SHOW_IMPLICIT);
			}
		}, 200);
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
