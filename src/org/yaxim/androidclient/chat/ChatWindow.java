package org.yaxim.androidclient.chat;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.regex.Matcher;

import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.provider.MediaStore;

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

import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Window;

import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.Uri;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.text.ClipboardManager;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
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

import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

@SuppressWarnings("deprecation") /* recent ClipboardManager only available since API 11 */
public class ChatWindow extends AppCompatActivity implements OnKeyListener,
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
			ChatConstants.RESOURCE, ChatConstants.MESSAGE, ChatConstants.MSGFLAGS,
			ChatConstants.ERROR, ChatConstants.CORRECTION, ChatConstants.EXTRA,
			ChatConstants.DELIVERY_STATUS, ChatConstants.PACKET_ID };

	private static final int[] PROJECTION_TO = new int[] { R.id.chat_date,
			R.id.chat_from, R.id.chat_message, R.id.chat_error };
	
	private static final int DELAY_NEWMSG = 3000;
	private static final int CHAT_MSG_LOADER = 0;
	private int lastlog_size = 200;
	private int lastlog_index = -1;
	private Uri cameraPictureUri = null;

	private static HashMap<String, String> messageDrafts = new HashMap<String, String>();

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
	protected boolean mIsMucPM = false;
	private boolean isContact = false;
	private Intent mChatServiceIntent;
	private ServiceConnection mChatServiceConnection;
	private XMPPChatServiceAdapter mChatServiceAdapter;
	private int mChatFontSize;
	private ActionBar actionBar;
	private ListView mListView;
	protected ChatWindowAdapter mChatAdapter;
	protected SearchView mSearchView;

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
	private final HashSet<Long> mReadMessages = new HashSet<Long>();

	private boolean mShowOrHide = true;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		mConfig = YaximApplication.getConfig();
		String titleUserid = setContactFromUri();
		Log.d(TAG, "onCreate, registering XMPP service");
		registerXMPPService();

		setTheme(mConfig.getTheme());
		super.onCreate(savedInstanceState);
		if (!mIsMucPM)
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
		long start_id = ChatHelper.getChatHistoryStartId(this, mWithJabberID, lastlog_size);
		return new CursorLoader(this, ChatProvider.CONTENT_URI, PROJECTION_FROM,
				"jid = ? AND _id > ?", new String[] { mWithJabberID, "" + start_id }, "date");
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
		if (mChatAdapter.getCursor() != null && mChatAdapter.getCursor().getCount() >= lastlog_size) {
			Log.d(TAG, "increaseLastLog: " + mChatAdapter.getCursor().getCount() + " += 200");
			lastlog_index = mChatAdapter.getCursor().getCount();
			lastlog_size = lastlog_index + 200;
			getSupportLoaderManager().restartLoader(CHAT_MSG_LOADER, null, this /*LoaderCallbacks<Cursor>*/);
		}
	}

	/* AbsListView.OnScrollListener */
	@Override
	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
		// if we have pending mark-read messages, delay them further. ScrollView doesn't like it otherwise
		if (mReadMessages.size() > 0) {
			mMarkHandler.removeCallbacks(mMarkRunnable);
			mMarkHandler.postDelayed(mMarkRunnable, DELAY_NEWMSG);
		}
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
		mChatServiceAdapter.sendFile(path, mWithJabberID, flags);
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
		String inputText = mChatInput.getText().toString();
		if (!TextUtils.isEmpty(inputText))
			messageDrafts.put(mWithJabberID, inputText);
		else
			messageDrafts.remove(mWithJabberID);
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
		String inputText = (messageDrafts.containsKey(mWithJabberID)) ? messageDrafts.get(mWithJabberID) : "";
		Intent i = getIntent();
		mChatInput = (EditText) findViewById(R.id.Chat_UserInput);
		mChatInput.addTextChangedListener(this);
		mChatInput.setOnKeyListener(this);
		mChatInput.addTextChangedListener(new StylingHelper.MessageEditorStyler(mChatInput));
		if (i.hasExtra(INTENT_EXTRA_MESSAGE)) {
			inputText += i.getExtras().getString(INTENT_EXTRA_MESSAGE);
			i.removeExtra(INTENT_EXTRA_MESSAGE);
		}
		mChatInput.setText(inputText);
		messageDrafts.remove(mWithJabberID);
	}
	private void handleSendIntent() {
		Intent i = getIntent();
		if (i.hasExtra(Intent.EXTRA_STREAM)) {
			Uri stream = (Uri)i.getParcelableExtra(Intent.EXTRA_STREAM);
			sendFile(stream, FileHttpUploadTask.F_RESIZE);
			i.removeExtra(Intent.EXTRA_STREAM);
		}
	}

	private String setContactFromUri() {
		Intent i = getIntent();
		Log.d(TAG, "setting contact from URI: "+mWithJabberID);
		mWithJabberID = i.getDataString(); // TODO: lowercase bare-JID, stringprep-normalize
		mIsMucPM = mWithJabberID.contains("/");
		String longName;
		if (i.hasExtra(INTENT_EXTRA_USERNAME)) {
			longName = i.getExtras().getString(INTENT_EXTRA_USERNAME);
			mUserScreenName = longName;
		} else {
			// we don't have a screen name, use the localpart of the JID (or domain JID if it is a domain)
			longName = mWithJabberID;
			mUserScreenName = mWithJabberID.split("@")[0];
		}
		if (mIsMucPM) {
			// re-extract nickname from localpart
			mUserScreenName = mWithJabberID.split("/")[1];
		}
		return longName;
	}

	// hack to work around item positions being invalidated on cursor change while the menu is open
	// the values will be populated
	String mContextMenuMessage = null;
	String mContextMenuQuote = null;
	String mContextMenuPacketID = null;
	long mContextMenuID = -1;

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenu.ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);

		AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;
		Cursor c = (Cursor)mListView.getItemAtPosition(info.position);
		mContextMenuMessage = c.getString(c.getColumnIndex(ChatProvider.ChatConstants.MESSAGE));
		mContextMenuPacketID = c.getString(c.getColumnIndex(ChatConstants.PACKET_ID));
		mContextMenuID = c.getLong(c.getColumnIndex("_id"));
		boolean from_me = c.getInt(c.getColumnIndex(ChatConstants.DIRECTION)) == ChatConstants.OUTGOING;
		String resource = c.getString(c.getColumnIndex(ChatConstants.RESOURCE));
		from_me = isFromMe(from_me, resource);
		mContextMenuQuote = getQuotedMessageFromContextMenu(from_me, info);

		getMenuInflater().inflate(R.menu.chat_contextmenu, menu);
		menu.findItem(R.id.chat_contextmenu_resend).setEnabled(from_me);
	}

	private String getQuotedMessageFromContextMenu(boolean from_me, AdapterContextMenuInfo info) {
		Cursor c = (Cursor)mListView.getItemAtPosition(info.position);
		String message = mContextMenuMessage;
		if (!from_me) {
			String jid = c.getString(c.getColumnIndex(ChatProvider.ChatConstants.JID));
			String resource = c.getString(c.getColumnIndex(ChatProvider.ChatConstants.RESOURCE));
			long timestamp = c.getLong(c.getColumnIndex(ChatProvider.ChatConstants.DATE));
			String ts = new SimpleDateFormat("HH:mm").format(new Date(timestamp));
			return String.format("%s [%s]:\n%s", jid2nickname(jid, resource), ts, XMPPHelper.quoteStringWithoutQuotes(message));
		}
		return XMPPHelper.quoteStringWithoutQuotes(message);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.chat_contextmenu_copy_text:
			ClipboardManager cm = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
			cm.setText(mContextMenuMessage);
			return true;
		case R.id.chat_contextmenu_quote:
			// insert quote into the current cursor position
			String quote = mContextMenuQuote;
			int position = Math.max(mChatInput.getSelectionStart(), 0);
			mChatInput.getText().insert(position, quote);
			position += quote.length();
			mChatInput.setSelection(position, position);
			Log.d(TAG, "quote!");
			return true;
		case R.id.chat_contextmenu_resend:
			final String pid = mContextMenuPacketID;
			final long upsert_id = mContextMenuID;
			ChatHelper.editTextDialog(this, R.string.chatmenu_resend, null,
					mContextMenuMessage, true, new ChatHelper.EditOk() {
						@Override
						public void ok(String result) {
							mChatServiceAdapter.sendMessage(mWithJabberID, result, pid, upsert_id);
							if (!mChatServiceAdapter.isServiceAuthenticated())
								showToastNotification(R.string.toast_stored_offline);
						}
					});
			Log.d(TAG, "resend!");
			return true;
		default:
			return super.onContextItemSelected((MenuItem) item);
		}
	}

	// specific for a roster/PM menu, overridden by MUC
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		if (isContact)
			inflater.inflate(R.menu.contact_options, menu);
		else {
			inflater.inflate(R.menu.noncontact_options, menu);
			menu.findItem(R.id.menu_add_friend).setVisible(!mIsMucPM);
		}
		return inflateGenericContactOptions(menu);
	}

	// used by subclasses
	public boolean inflateGenericContactOptions(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		//inflater.inflate(R.menu.contact_options, menu);
		inflater.inflate(R.menu.roster_item_contextmenu, menu);

		mSearchView = (SearchView)MenuItemCompat.getActionView(menu.findItem(R.id.app_bar_search));
		mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
			@Override
			public boolean onQueryTextSubmit(String query) {
				return true;
			}

			@Override
			public boolean onQueryTextChange(String query) {
				mChatAdapter.getFilter().filter(query.trim());
				return true;
			}
		});
		mSearchView.setQueryHint(getString(R.string.search_msg_hint));
		if (mChatServiceAdapter != null && mChatServiceAdapter.hasFileUpload()) {
			menu.findItem(R.id.roster_contextmenu_send).setVisible(true);
		}
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Log.d(TAG, "options item selected");
		switch (item.getItemId()) {
		case android.R.id.home:
			Intent intent = new Intent(this, MainWindow.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(intent);
			finish();
			return true;
		case R.id.roster_contextmenu_take_image:
			if (!mChatServiceAdapter.isServiceAuthenticated()) { showToastNotification(R.string.Global_authenticate_first); return true; }
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
			if (!mChatServiceAdapter.isServiceAuthenticated()) { showToastNotification(R.string.Global_authenticate_first); return true; }
			intent = new Intent(Intent.ACTION_GET_CONTENT);
			intent.setType("image/*");
			intent.addCategory(Intent.CATEGORY_OPENABLE);
			startActivityForResult(Intent.createChooser(intent, getString(R.string.roster_contextmenu_send_image)), REQUEST_IMAGE);
			return true;
		case R.id.roster_contextmenu_send_file:
			if (!mChatServiceAdapter.isServiceAuthenticated()) { showToastNotification(R.string.Global_authenticate_first); return true; }
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
		mChatServiceAdapter.sendMessage(mWithJabberID, message, null, -1);
		if (!mChatServiceAdapter.isServiceAuthenticated())
			showToastNotification(R.string.toast_stored_offline);
	}

	private boolean markAsReadDelayed(final long id, final int delay) {
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
		HashSet<Long> hs = (HashSet)mReadMessages.clone();
		Uri rowuri = Uri.parse("content://" + ChatProvider.AUTHORITY
			+ "/" + ChatProvider.TABLE_NAME);
		// create custom WHERE statement instead of relying on ContentResolvers whereArgs
		StringBuilder where = new StringBuilder();
		where.append("_id IN (");
		for (long id : hs) {
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
			setFilterQueryProvider(new FilterQueryProvider() {
				@Override
				public Cursor runQuery(CharSequence q) {
					return getContentResolver().query(ChatProvider.CONTENT_URI, PROJECTION_FROM,
							"jid = ? AND message LIKE ?", new String[] { mWithJabberID, "%" + q + "%" }, "date");
				}
			});
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View row = convertView;
			ChatItemWrapper wrapper = null;
			Cursor cursor = this.getCursor();
			cursor.moveToPosition(position);

			long dateMilliseconds = cursor.getLong(cursor
					.getColumnIndex(ChatProvider.ChatConstants.DATE));

			long _id = cursor.getLong(cursor
					.getColumnIndex(ChatProvider.ChatConstants._ID));
			String date = getDateString(dateMilliseconds);
			String message = cursor.getString(cursor
					.getColumnIndex(ChatProvider.ChatConstants.MESSAGE));
			int msgFlags = cursor.getInt(cursor
					.getColumnIndex(ChatConstants.MSGFLAGS));
			String error = cursor.getString(cursor
					.getColumnIndex(ChatConstants.ERROR));
			boolean correction = !TextUtils.isEmpty(cursor.getString(cursor
					.getColumnIndex(ChatConstants.CORRECTION)));
			String extra = cursor.getString(cursor
					.getColumnIndex(ChatConstants.EXTRA));
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
			// work around for 0.9.3 not setting MF_CORRECT but storing LMC field
			if (correction)
				msgFlags |= ChatConstants.MF_CORRECT;

			wrapper.populateFrom(date, from_me, jid2nickname(jid, resource), message, msgFlags,
					error, extra, delivery_status, mScreenName);
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
			mDateView = (TextView) mRowView.findViewById(R.id.chat_date);
			mFromView = (TextView) mRowView.findViewById(R.id.chat_from);
			mMessageView = (TextView) mRowView.findViewById(R.id.chat_message);
			mErrorView = (TextView) mRowView.findViewById(R.id.chat_error);
			mIconView = (ImageView) mRowView.findViewById(R.id.iconView);
		}


		void populateFrom(String date, boolean from_me, String from, String message, int msgFlags,
				String error, final String extra, int delivery_status, String highlight_text) {
			if ((msgFlags & ChatConstants.MF_CORRECT) != 0)
				date = "\u270d " + date; /* prepend Writing Hand */
			else if ((msgFlags & ChatConstants.MF_DELAY) != 0)
				date = "\u23F1 " + date; /* prepend Stopwatch */
			mDateView.setText(date);
			TypedValue tv = new TypedValue();
			if (from_me) {
				getTheme().resolveAttribute(R.attr.ChatMsgHeaderMeColor, tv, true);
				mFromView.setText(getString(R.string.chat_from_me));
				mFromView.setTextColor(tv.data);
				from = mConfig.userName;
			} else {
				nick2Color(from, tv);
				mFromView.setText(from + ":");
				mFromView.setTextColor(tv.data);
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
				mIconView.setImageResource(R.drawable.ic_chat_msg_status_queued);
				break;
			case ChatConstants.DS_SENT_OR_READ:
				mIconView.setImageResource(R.drawable.ic_chat_msg_status_unread);
				mRowView.setBackgroundColor(0x00000000); // default is transparent
				break;
			case ChatConstants.DS_ACKED:
				mIconView.setImageResource(R.drawable.ic_chat_msg_status_ok);
				mRowView.setBackgroundColor(0x00000000); // default is transparent
				break;
			case ChatConstants.DS_FAILED:
				mIconView.setImageResource(R.drawable.ic_chat_msg_status_failed);
				mRowView.setBackgroundColor(0x30ff0000); // default is transparent
				break;
			}

			SpannableStringBuilder body = MessageStylingHelper.formatMessage(message,
					from, highlight_text, mMessageView.getCurrentTextColor());
			eu.siacs.conversations.utils.StylingHelper.handleTextQuotes(body, mMessageView.getCurrentTextColor(), getResources().getDisplayMetrics());
			MessageStylingHelper.applyEmojiScaling(body, 5.0f); /* >5.0 will exceed Emoji rendering limit of 150px */
			mMessageView.setText(body);

			mMessageView.setTextSize(TypedValue.COMPLEX_UNIT_SP, chatWindow.mChatFontSize);
			mDateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, chatWindow.mChatFontSize*2/3);
			mFromView.setTextSize(TypedValue.COMPLEX_UNIT_SP, chatWindow.mChatFontSize*2/3);
			mErrorView.setTextSize(TypedValue.COMPLEX_UNIT_SP, chatWindow.mChatFontSize*2/3);
			mErrorView.setText(error);
			mErrorView.setVisibility(TextUtils.isEmpty(error) ? View.GONE : View.VISIBLE);
			// these calls must be in the exact right order.
			Linkify.addLinks(mMessageView, Linkify.MAP_ADDRESSES | Linkify.WEB_URLS);
			Linkify.addLinks(mMessageView, XMPPHelper.XEP_PATTERN, null, null, new Linkify.TransformFilter() {
				@Override
				public String transformUrl(Matcher matcher, String s) {
					return String.format((Locale)null, "https://xmpp.org/extensions/xep-%s.html", matcher.group(1));
					//return s.replace("XEP-", "https://xmpp.org/extensions/xep-") + ".html";
				}
			});

			// Android's default phone linkifuckation makes 13:37 two phone numbers
			Linkify.addLinks(mMessageView, XMPPHelper.PHONE, "tel:", Linkify.sPhoneNumberMatchFilter, Linkify.sPhoneNumberTransformFilter);
			// Android's default email linkifuckation breaks xmpp: URIs
			Linkify.addLinks(mMessageView, XMPPHelper.XMPP_PATTERN, "xmpp");
			Linkify.addLinks(mMessageView, XMPPHelper.EMAIL_ADDRESS, "mailto:");
			ImageView iv = (ImageView)mRowView.findViewById(R.id.chat_image);
			boolean has_extra = !TextUtils.isEmpty(extra);
			iv.setVisibility(has_extra ? View.VISIBLE : View.GONE);
			if (has_extra) {
				if (extra.equals(message))
					mMessageView.setVisibility(View.GONE);
				UrlImageViewHelper.setUrlDrawable(iv, extra, android.R.drawable.ic_menu_report_image, new UrlImageViewCallback() {
					@Override
					public void onLoaded(ImageView imageView, Bitmap bitmap, String s, boolean b) {
						if (bitmap == null) {
							// error loading, display URL again
							mMessageView.setVisibility(View.VISIBLE);
						}
					}
				});
				iv.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse(extra)));
					}
				});
				iv.setOnLongClickListener(new View.OnLongClickListener() {
					@Override
					public boolean onLongClick(View view) {
						ChatWindow.this.openContextMenu(view);
						return true;
					}
				});
			} else
				mMessageView.setVisibility(View.VISIBLE);
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

	// this method is a "virtual" placeholder for the MUC activity
	public boolean isFromMe(boolean from_me, String resource) {
			return from_me;
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
