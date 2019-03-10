package org.yaxim.androidclient;

import java.util.HashMap;
import java.util.List;

import org.yaxim.androidclient.data.ChatHelper;
import org.yaxim.androidclient.data.ChatProvider;
import org.yaxim.androidclient.data.ChatProvider.ChatConstants;
import org.yaxim.androidclient.data.ChatRoomHelper;
import org.yaxim.androidclient.data.RosterProvider;
import org.yaxim.androidclient.data.RosterProvider.RosterConstants;
import org.yaxim.androidclient.dialogs.AddRosterItemDialog;
import org.yaxim.androidclient.dialogs.ChangeStatusDialog;
import org.yaxim.androidclient.dialogs.EditMUCDialog;
import org.yaxim.androidclient.dialogs.FirstStartDialog;
import org.yaxim.androidclient.list.SearchActivity;
import org.yaxim.androidclient.preferences.AccountPrefs;
import org.yaxim.androidclient.preferences.MainPrefs;
import org.yaxim.androidclient.preferences.NotificationPrefs;
import org.yaxim.androidclient.service.XMPPService;
import org.yaxim.androidclient.util.ConnectionState;
import org.yaxim.androidclient.util.PreferenceConstants;
import org.yaxim.androidclient.util.StatusMode;
import org.yaxim.androidclient.util.XMPPHelper;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.ClipboardManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.View;
import android.view.MenuItem;
import android.widget.ExpandableListView;
import android.widget.ImageView;

import org.yaxim.androidclient.util.SimpleCursorTreeAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ExpandableListView.ExpandableListContextMenuInfo;
import org.yaxim.androidclient.IXMPPRosterCallback.Stub;
import org.yaxim.androidclient.service.IXMPPRosterService;

import android.view.Menu;

import android.support.v7.app.ActionBar;

import me.leolin.shortcutbadger.ShortcutBadger;

public class MainWindow extends ThemedActivity implements ExpandableListView.OnChildClickListener {

	private static final String TAG = "yaxim.MainWindow";


	private Handler mainHandler = new Handler();

	private Intent xmppServiceIntent;
	private ServiceConnection xmppServiceConnection;
	private XMPPRosterServiceAdapter serviceAdapter;
	private Stub rosterCallback;
	private RosterExpListAdapter rosterListAdapter;
	private TextView mConnectingText;
	private FirstStartDialog mFirstStartDialog;

	private ContentObserver mRosterObserver = new RosterObserver();
	private ContentObserver mChatObserver = new ChatObserver();
	private HashMap<String, Boolean> mGroupsExpanded = new HashMap<String, Boolean>();

	private boolean mHandledIntent = false;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.i(TAG, getString(R.string.build_version));
		super.onCreate(savedInstanceState);
		
		getContentResolver().registerContentObserver(RosterProvider.CONTENT_URI,
				true, mRosterObserver);
		getContentResolver().registerContentObserver(ChatProvider.CONTENT_URI,
				true, mChatObserver);
		registerXMPPService();
		setupContenView();
		createUICallback();
		registerListAdapter();

		mHandledIntent = (getIntent().getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		getContentResolver().unregisterContentObserver(mRosterObserver);
		getContentResolver().unregisterContentObserver(mChatObserver);
	}

	public ExpandableListView getExpandableListView() {
		return (ExpandableListView)findViewById(android.R.id.list);
	}

	public int getStatusActionIcon() {
		boolean showOffline = !isConnected() || isConnecting()
					|| getStatusMode() == null;

		if (showOffline) {
			return StatusMode.offline.getDrawableId();
		}

		return getStatusMode().getDrawableId();
	}

	// need this to workaround unwanted OnGroupCollapse/Expand events
	boolean groupClicked = false;
	void handleGroupChange(int groupPosition, boolean isExpanded) {
		if (groupClicked) {
			try {
				String groupName = getGroupName(groupPosition);
				Log.d(TAG, "group status change: " + groupName + " -> " + isExpanded);
				mGroupsExpanded.put(groupName, isExpanded);
			} catch (NullPointerException e) {
				// sometimes, it fails to obtain the cursor. We can ignore it
			}
			groupClicked = false;
		}
	}


	void setupContenView() {
		setContentView(R.layout.main);
		mConnectingText = (TextView)findViewById(R.id.error_view);
		registerForContextMenu(getExpandableListView());
		getExpandableListView().requestFocus();

		getExpandableListView().setOnChildClickListener(this);
		getExpandableListView().setOnGroupClickListener(
			new ExpandableListView.OnGroupClickListener() {
				public boolean onGroupClick(ExpandableListView parent, View v, int groupPosition,
						long id) {
					groupClicked = true;
					return false;
				}
			});
		getExpandableListView().setOnGroupCollapseListener(
			new ExpandableListView.OnGroupCollapseListener() {
				public void onGroupCollapse(int groupPosition) {
					handleGroupChange(groupPosition, false);
				}
			});
		getExpandableListView().setOnGroupExpandListener(
			new ExpandableListView.OnGroupExpandListener() {
				public void onGroupExpand(int groupPosition) {
					handleGroupChange(groupPosition, true);
				}
			});
	}

	@Override
	protected void onTitleClicked(View view) {
		new ChangeStatusDialog(this, mConfig).show();
	}

	@Override
	protected void onNewIntent(Intent i) {
		setIntent(i);
		mHandledIntent = false;
	}

	protected void clearIntent() {
		getIntent().setData(null);
		mHandledIntent = true;
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (serviceAdapter != null)
			serviceAdapter.unregisterUICallback(rosterCallback);

		YaximApplication.getApp().mMTM.unbindDisplayActivity(this);
		unbindXMPPService();
		storeExpandedState();
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (mConfig.theme.equals(mTheme) == false) {
			// restart
			Intent restartIntent = new Intent(this, MainWindow.class);
			restartIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(restartIntent);
			finish();
		}

		showFirstStartUpDialogIfPrefsEmpty();

		displayOwnStatus();
		bindXMPPService();

		YaximApplication.getApp().mMTM.bindDisplayActivity(this);

		// handle SEND action
		handleSendIntent();
	}

	public void handleSendIntent() {
		Intent intent = getIntent();
		String action = intent.getAction();
		if (!mHandledIntent && (action != null) && (action.equals(Intent.ACTION_SEND))) {
			showToastNotification(R.string.chooseContact);
			setTitle(R.string.chooseContact);
		}
	}

	/** opens a ChatWindow / MUCChat window to the given JID, potentially sending the attached text.
	 *
	 * @return true if the chat was successfully opened.
	 */
	public boolean openChatWithJid(String jid, String text) {
		Log.d(TAG, "openChatWithJid: " + jid);

		// search for JID in roster, to obtain display name
		List<String[]> contacts = ChatHelper.getRosterContacts(this, ChatHelper.ROSTER_FILTER_ALL);
		for (String[] c : contacts) {
			if (jid.equalsIgnoreCase(c[0])) {
				// found it
				ChatHelper.startChatActivity(this, c[0], c[1], text);
				return true;
			}
		}
		// if we have a message, open chat to JID irregardless of roster
		if (text != null) {
			ChatHelper.startChatActivity(this, jid, jid, text);
			return true;
		}
		return false;
	}

	public boolean isJabberIntentAction(String action) {
		return Intent.ACTION_VIEW.equals(action) ||
			android.nfc.NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action);
	}

	public boolean transmogrifyXmppUri(Intent intent) {
		Uri data = transmogrifyXmppUriHelper(intent.getData());
		if (data != null) {
			intent.setData(data);
			return true;
		}
		return false;
	}
	public Uri transmogrifyXmppUriHelper(Uri uri) {
		Uri data = uri;
		if ("xmpp".equalsIgnoreCase(data.getScheme())) {
			if (data.isOpaque()) {
				// cheat around android's unwillingness to parse opaque URIs
				data = Uri.parse(data.toString().replaceFirst(":", "://").replace(';', '&'));
			}
		} else if ("yax.im".equalsIgnoreCase(data.getHost())) {
			// convert URI fragment (after # sign) into xmpp URI
			String jid = data.getFragment().replace(';', '&');
			data = Uri.parse("xmpp://" + XMPPHelper.jid2url(jid));
		} else if ("conversations.im".equalsIgnoreCase(data.getHost())) {
			try {
				List<String> segments = data.getPathSegments();
				String code = segments.get(0);
				String jid = segments.get(1);
				String token = "";
				if (!jid.contains("@")) {
					jid = segments.get(1) + "@" + segments.remove(2);
				}
				if (segments.size() > 2)
					token = "&preauth=" + segments.get(2);
				if ("i".equalsIgnoreCase(code))
					data = Uri.parse("xmpp://" + jid + "?roster" + token);
				else if ("j".equalsIgnoreCase(code))
					data = Uri.parse("xmpp://" + jid + "?join");
				else return null;
			} catch (Exception e) {
				Log.d(TAG, "Failed to parse URI " + data);
				return null;
			}
		} else
			return null;
		Log.d(TAG, "transmogrifyXmppUri: " + uri + " --> " + data);
		return data;
	}

	public synchronized void handleJabberIntent() {
		Intent intent = getIntent();
		Log.d(TAG, "handleJabberIntent: " + intent);
		String action = intent.getAction();
		Uri data = intent.getData();
		if (action == null || data == null || mHandledIntent)
			return;
		// ignore event if no account registered; TODO: handle xmpp://account@server?register
		if (mConfig.jabberID.length() < 3 || PreferenceManager.getDefaultSharedPreferences(this).contains(PreferenceConstants.FIRSTRUN))
			return;
		if (action.equals(Intent.ACTION_SENDTO) && data.getHost().equals("jabber")) {
			// 1. look for JID in roster; 2. attempt to add
			String jid = data.getPathSegments().get(0);
			if (openChatWithJid(jid, null) || addToRosterDialog(jid))
				clearIntent();
		} else if (isJabberIntentAction(action) && transmogrifyXmppUri(intent)) {
			if (handleXmppUri(intent.getData()))
				clearIntent();
		}
	}

	public boolean handleXmppUri(Uri data) {
		String jid = data.getAuthority();
		String body = data.getQueryParameter("body");
		if (TextUtils.isEmpty(jid)) {
			if (!TextUtils.isEmpty(body)) {
				// this is a body-less `xmpp:?message;body=TEXT` - convert to ACTION_SEND
				getIntent().setAction(Intent.ACTION_SEND)
						.setData(null)
						.putExtra(Intent.EXTRA_TEXT, body);
				handleSendIntent();
			}
			// stop processing if JID is empty
			return false;
		}
		String name = data.getQueryParameter("name");
		String preauth = data.getQueryParameter("preauth");
		if (data.getQueryParameter("register") != null) {
			showToastNotification(R.string.StartupDialog_no_more_accounts);
			// consume the event, even though not supported
			return true;
		}
		if (data.getQueryParameter("roster") != null || data.getQueryParameter("subscribe") != null) {
			return addToRosterDialog(jid, name, preauth);
		} else if (data.getQueryParameter("join") != null && !openChatWithJid(jid, null)) {
			new EditMUCDialog(this, jid, data.getQueryParameter("body"),
					null, data.getQueryParameter("password")).withNick(mConfig.userName).show();
			return true;
		} else if (openChatWithJid(jid, body) || addToRosterDialog(jid, name, preauth)) {
			return true;
		}
		return false;
	}

	@SuppressWarnings("deprecation") /* recent ClipboardManager only available since API 11 */
	public Uri xmppUriFromClipboard() {
		ClipboardManager cm = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
		if (cm.getText() == null)
			return null;
		String clip = cm.getText().toString();
		if (clip.contains("@") && XMPPHelper.XMPP_PATTERN.matcher("xmpp:" + clip).matches()) {
			return new Uri.Builder().scheme("xmpp").authority(clip).build();
		}
		return transmogrifyXmppUriHelper(Uri.parse(clip));
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		Log.d(TAG, "onConfigurationChanged");
		getExpandableListView().requestFocus();
	}

	private boolean isConnected() {
		return serviceAdapter != null && serviceAdapter.isAuthenticated();
	}
	private boolean isConnecting() {
		return serviceAdapter != null &&
			(serviceAdapter.getConnectionState() == ConnectionState.CONNECTING ||
			 serviceAdapter.getConnectionState() == ConnectionState.LOADING);
	}

	public void updateRoster() {
		rosterListAdapter.requery();
		restoreGroupsExpanded();
		new LoadUnreadTask().execute();
	}

	private StatusMode getContactStatusMode(Cursor c) {
		try {
			return StatusMode.values()[c.getInt(c.getColumnIndexOrThrow(RosterConstants.STATUS_MODE))];
		} catch (Exception e) {
			Log.e(TAG, "Invalid status for contact " + e.getMessage());
			return StatusMode.unknown;
		}
	}

	private StatusMode getItemStatusMode(long packedPosition) {
		int flatPosition = getExpandableListView().getFlatListPosition(packedPosition);
		Cursor c = (Cursor)getExpandableListView().getItemAtPosition(flatPosition);
		return getContactStatusMode(c);
	}

	private String getPackedItemRow(long packedPosition, String rowName) {
		int flatPosition = getExpandableListView().getFlatListPosition(packedPosition);
		Cursor c = (Cursor)getExpandableListView().getItemAtPosition(flatPosition);
		return c.getString(c.getColumnIndex(rowName));
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenu.ContextMenuInfo menuInfo) {
		ExpandableListView.ExpandableListContextMenuInfo info;

		try {
			info = (ExpandableListView.ExpandableListContextMenuInfo) menuInfo;
		} catch (ClassCastException e) {
			Log.e(TAG, "bad menuinfo: ", e);
			return;
		}

		long packedPosition = info.packedPosition;
		boolean isChild = isChild(packedPosition);

		// get the entry name for the item
		String menuName;
		boolean isMuc=false;
		if (isChild) {
			// do not show context menu before a contact has been added
			if (getItemStatusMode(packedPosition) == StatusMode.subscribe)
				return;
			getMenuInflater().inflate(R.menu.roster_item_contextmenu, menu);
			menuName = String.format("%s (%s)",
				getPackedItemRow(packedPosition, RosterConstants.ALIAS),
				getPackedItemRow(packedPosition, RosterConstants.JID));
			isMuc = ChatRoomHelper.isRoom(this, getPackedItemRow(packedPosition, RosterConstants.JID));
			if (isMuc) {
				getMenuInflater().inflate(R.menu.muc_options, menu);
				menu.findItem(R.id.chat_optionsmenu_userlist).setVisible(false);
			} else
				getMenuInflater().inflate(R.menu.contact_options, menu);
		} else {
			menuName = getPackedItemRow(packedPosition, RosterConstants.GROUP);
			if (menuName.equals("") || menuName.equals(RosterConstants.MUCS))
				return; // no options for default menu
			getMenuInflater().inflate(R.menu.roster_group_contextmenu, menu);
		}
		menu.setHeaderTitle(menuName);
	}

	void removeChatHistory(final String JID) {
		getContentResolver().delete(ChatProvider.CONTENT_URI,
				ChatProvider.ChatConstants.JID + " = ?", new String[] { JID });
	}

	boolean addToRosterDialog(String jid, String alias, String token) {
		if (serviceAdapter != null && serviceAdapter.isAuthenticated()) {
			new AddRosterItemDialog(this, jid)
				.setAlias(alias)
				.setToken(token)
				.show();
			return true;
		} else {
			showToastNotification(R.string.Global_authenticate_first);
			return false;
		}
	}
	boolean addToRosterDialog(String jid) {
		return addToRosterDialog(jid, null, null);
	}

	void rosterAddRequestedDialog(final String jid, final String alias, String message) {
		new AlertDialog.Builder(this)
			.setTitle(R.string.subscriptionRequest_title)
			.setMessage(getString(R.string.subscriptionRequest_text, alias,
						message != null ? message : ""))
			.setPositiveButton(R.string.subscription_accept,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							serviceAdapter.sendPresenceRequest(jid, "subscribed");
							// show dialog if not yet configured
							if (alias.equals(jid))
								addToRosterDialog(jid);
						}
					})
			.setNeutralButton(R.string.subscription_reject_all,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							serviceAdapter.sendPresenceRequest(null, "unsubscribed");
						}
					})
			.setNegativeButton(R.string.subscription_reject,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							serviceAdapter.sendPresenceRequest(jid, "unsubscribed");
						}
					})
			.create().show();
	}

	void renameRosterGroupDialog(final String groupName) {
		ChatHelper.editTextDialog(this, R.string.RenameGroup_title,
				getString(R.string.RenameGroup_summ, groupName),
				groupName, false, new ChatHelper.EditOk() {
					public void ok(String result) {
						serviceAdapter.renameRosterGroup(groupName, result);
					}
				});
	}

	public boolean onContextItemSelected(MenuItem item) {
		return applyMenuContextChoice(item);
	}

	private boolean applyMenuContextChoice(MenuItem item) {

		ExpandableListContextMenuInfo contextMenuInfo = (ExpandableListContextMenuInfo) item
				.getMenuInfo();
		long packedPosition = contextMenuInfo.packedPosition;

		if (isChild(packedPosition)) {

			String userJid = getPackedItemRow(packedPosition, RosterConstants.JID);
			String userName = getPackedItemRow(packedPosition, RosterConstants.ALIAS);
			Log.d(TAG, "action for contact " + userName + "/" + userJid);

			int itemID = item.getItemId();

			switch (itemID) {
			// items that require an authenticated connection
			case R.id.roster_contextmenu_contact_delete:
			case R.id.roster_contextmenu_contact_rename:
			case R.id.roster_contextmenu_contact_request_auth:
			case R.id.roster_contextmenu_contact_change_group:
				if (!isConnected()) { showToastNotification(R.string.Global_authenticate_first); return true; }
				// fall through to default handler
			default:
				return ChatHelper.handleJidOptions(this, itemID, userJid, userName);
			}
		} else {

			int itemID = item.getItemId();
			String seletedGroup = getPackedItemRow(packedPosition, RosterConstants.GROUP);
			Log.d(TAG, "action for group " + seletedGroup);

			switch (itemID) {
			case R.id.roster_contextmenu_group_rename:
				if (!isConnected()) { showToastNotification(R.string.Global_authenticate_first); return true; }
				renameRosterGroupDialog(seletedGroup);
				return true;
			case R.id.roster_contextmenu_ringtone:
				Intent ringToneIntent = new Intent(this, NotificationPrefs.class);
				ringToneIntent.putExtra("jid", seletedGroup);
				startActivity(ringToneIntent);
				return true;

			}
		}
		return false;
	}

	private boolean isChild(long packedPosition) {
		int type = ExpandableListView.getPackedPositionType(packedPosition);
		return (type == ExpandableListView.PACKED_POSITION_TYPE_CHILD);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.roster_options, menu);
		return true;
	}

	void setMenuItem(Menu menu, int itemId, int iconId, CharSequence title) {
		MenuItem item = menu.findItem(itemId);
		if (item == null)
			return;
		item.setIcon(iconId);
		item.setTitle(title);
	}
	void setMenuItemFromClipboard(Menu menu, int itemId) {
		MenuItem item = menu.findItem(itemId);
		if (item == null)
			return;
		Uri link = xmppUriFromClipboard();
		item.setVisible(link != null);
		if (link != null)
			item.setTitle(getString(R.string.Menu_addClipboard, link.getAuthority()));
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		setMenuItem(menu, R.id.menu_connect, getConnectDisconnectIcon(),
				getConnectDisconnectText());
		setMenuItem(menu, R.id.menu_show_hide, getShowHideMenuIcon(),
				getShowHideMenuText());
		setMenuItemFromClipboard(menu, R.id.menu_add_clipboard);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		return applyMainMenuChoice(item);
	}

	private int getShowHideMenuIcon() {
		TypedValue tv = new TypedValue();
		if (mConfig.showOffline) {
			getTheme().resolveAttribute(R.attr.OnlineFriends, tv, true);
			return tv.resourceId;
		}
		getTheme().resolveAttribute(R.attr.AllFriends, tv, true);
		return tv.resourceId;
	}

	private String getShowHideMenuText() {
		return mConfig.showOffline ? getString(R.string.Menu_HideOff)
				: getString(R.string.Menu_ShowOff);
	}

	public StatusMode getStatusMode() {
		return mConfig.getPresenceMode();
	}

	public void updateStatus(StatusMode statusMode) {
		displayOwnStatus();
		if (serviceAdapter == null)
			return; // we can't do anything, let's pray service will update from config

		// check if we are connected and want to go offline
		boolean needToDisconnect = (statusMode == StatusMode.offline) && isConnected();
		// check if we want to reconnect
		boolean needToConnect = (statusMode != StatusMode.offline) &&
				serviceAdapter.getConnectionState() == ConnectionState.OFFLINE;

		if (needToConnect || needToDisconnect)
			toggleConnection();
		else if (isConnected())
			serviceAdapter.setStatusFromConfig();
	}

	private void displayOwnStatus() {
		// This and many other things like it should be done with observer
		setIcon(getStatusActionIcon());
		setSubtitle(mConfig.statusMessage);
	}

	private void aboutDialog() {
		LayoutInflater inflater = (LayoutInflater)getSystemService(
			      LAYOUT_INFLATER_SERVICE);
		View about = inflater.inflate(R.layout.aboutview, null, false);
		String versionTitle = getString(R.string.AboutDialog_title);
		try {
			PackageInfo pi = getPackageManager()
						.getPackageInfo(getPackageName(), 0);
			versionTitle += " v" + pi.versionName;
		} catch (NameNotFoundException e) {
		}

		// fix translator-credits: hide if unset, format otherwise
		TextView tcv = (TextView)about.findViewById(R.id.translator_credits);
		if (tcv.getText().equals("translator-credits"))
			tcv.setVisibility(View.GONE);

		new AlertDialog.Builder(this)
			.setTitle(versionTitle)
			.setIcon(android.R.drawable.ic_dialog_info)
			.setView(about)
			.setNegativeButton(R.string.AboutDialog_DevelopersText, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int item) {
					if (mConfig.jabberID.length() < 3 || TextUtils.isEmpty(mConfig.userName)) {
						showToastNotification(R.string.Global_authenticate_first);
						return;
					}
					String jid = getString(R.string.yaxim_muc);
					if (!openChatWithJid(jid, null)) {
						new EditMUCDialog(MainWindow.this, jid, null,
								null, null).withNick(mConfig.userName).show();
					}
				}})
			.setPositiveButton(android.R.string.ok, null)
			.setNeutralButton(R.string.AboutDialog_Vote, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int item) {
					Intent market = new Intent(Intent.ACTION_VIEW,
						Uri.parse("market://details?id=" + getPackageName()));
					market.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
					try {
						startActivity(market);
					} catch (Exception e) {
						// do not crash
						Log.e(TAG, "could not go to market: " + e);
					}
				}
			})
			.create().show();
	}

	private boolean applyMainMenuChoice(MenuItem item) {

		int itemID = item.getItemId();

		switch (itemID) {
		case R.id.menu_connect:
			toggleConnection();
			return true;

		case R.id.menu_add_clipboard:
			Uri link = xmppUriFromClipboard();
			if (link != null)
				handleXmppUri(link);
			return true;

		case R.id.menu_add_friend:
			addToRosterDialog(null);
			return true;

		case R.id.menu_show_hide:
			setOfflinceContactsVisibility(!mConfig.showOffline);
			updateRoster();
			return true;
			
		case R.id.menu_markallread:
			ChatHelper.markAllAsRead(this);
			ShortcutBadger.applyCount(this, 0);
			return true;

		case android.R.id.home:
			new ChangeStatusDialog(this, mConfig).show();
			return true;

		case R.id.menu_exit:
			PreferenceManager.getDefaultSharedPreferences(this).edit().
				putBoolean(PreferenceConstants.CONN_STARTUP, false).commit();
			stopService(xmppServiceIntent);
			finish();
			return true;

		case R.id.menu_settings:
			startActivity(new Intent(this, MainPrefs.class));
			return true;

		case R.id.menu_about:
			aboutDialog();
			return true;
		case R.id.menu_muc:
			startActivity(new Intent(this, SearchActivity.class));
			return true;
		case R.id.menu_send_invitation:
			ChatHelper.showQrDialog(this, mConfig.jabberID, XMPPHelper.createInvitationLinkHTTPS(mConfig.jabberID,
						mConfig.createInvitationCode()), getString(R.string.Menu_send_invitation));
			return true;

		}

		return false;

	}

	/** Sets if all contacts are shown in the roster or online contacts only. */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB) // required for Sherlock's invalidateOptionsMenu */
	private void setOfflinceContactsVisibility(boolean showOffline) {
		PreferenceManager.getDefaultSharedPreferences(this).edit().
			putBoolean(PreferenceConstants.SHOW_OFFLINE, showOffline).commit();
		invalidateOptionsMenu();
	}

	@Override
	public boolean onChildClick(ExpandableListView parent, View v,
			int groupPosition, int childPosition, long id) {

		long packedPosition = ExpandableListView.getPackedPositionForChild(groupPosition, childPosition);
		Cursor c = (Cursor)getExpandableListView().getItemAtPosition(getExpandableListView().getFlatListPosition(packedPosition));
		String userJid = c.getString(c.getColumnIndexOrThrow(RosterConstants.JID));
		String userName = c.getString(c.getColumnIndexOrThrow(RosterConstants.ALIAS));
		Intent i = getIntent();
		if (!mHandledIntent && i.getAction() != null && i.getAction().equals(Intent.ACTION_SEND)) {
			// delegate ACTION_SEND to child window and close self
			Uri stream = (Uri)i.getParcelableExtra(Intent.EXTRA_STREAM);
			ChatHelper.startChatActivity(this, userJid, userName, i.getStringExtra(Intent.EXTRA_TEXT), stream);
			finish();
		} else {
			StatusMode s = getContactStatusMode(c);
			if (s == StatusMode.subscribe)
				rosterAddRequestedDialog(userJid, userName,
					c.getString(c.getColumnIndexOrThrow(RosterConstants.STATUS_MESSAGE)));
			else
				ChatHelper.startChatActivity(this, userJid, userName, null);
		}

		return true;
	}

	private void updateConnectionState(ConnectionState cs) {
		Log.d(TAG, "updateConnectionState: " + cs);
		displayOwnStatus();
		boolean spinTheSpinner = false;
		switch (cs) {
		case CONNECTING:
		case LOADING:
		case DISCONNECTING:
			spinTheSpinner = true;
		case DISCONNECTED:
		case RECONNECT_NETWORK:
		case RECONNECT_DELAYED:
		case OFFLINE:
			if (cs == ConnectionState.DISCONNECTED) {
				boolean firstRun = PreferenceManager.getDefaultSharedPreferences(this)
									.contains(PreferenceConstants.FIRSTRUN);
				String error = serviceAdapter.getConnectionStateString().replace("conflict(-1) ", "");
				if (error.contains("\n")) // TODO: work around getConnectionStateString() returning two lines
					error = error.split("\n")[1];
				if (error.contains("SASLError using")) {// TODO: hack to circumvent old smack
					error = getString(R.string.StartupDialog_auth_failed);
					firstRun = true;
				}
				if (firstRun) {
					Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
					showFirstStartUpDialog();
				}
			} else
			if (cs == ConnectionState.OFFLINE) // override with "Offline" string, no error message
				mConnectingText.setText(R.string.conn_offline);
			else
				mConnectingText.setText(serviceAdapter.getConnectionStateString());
			mConnectingText.setVisibility(View.VISIBLE);
			setSupportProgressBarIndeterminateVisibility(spinTheSpinner);
			break;
		case ONLINE:
			mConnectingText.setVisibility(View.GONE);
			setSupportProgressBarIndeterminateVisibility(false);
			SharedPreferences prefs = PreferenceManager
					.getDefaultSharedPreferences(this);
			if (prefs.contains(PreferenceConstants.FIRSTRUN)) {
				// in case we just registered, re-fire the Intent
				handleJabberIntent();
				prefs.edit().remove(PreferenceConstants.FIRSTRUN).commit();
			}
		}
	}
	
	public void startConnection(boolean create_account) {
		xmppServiceIntent.putExtra("create_account", create_account);
		startService(xmppServiceIntent);
	}

	// this function changes the prefs to keep the connection
	// according to the requested state
	private void toggleConnection() {
		if (!mConfig.jid_configured) {
			startActivity(new Intent(this, AccountPrefs.class));
			return;
		}
		boolean oldState = isConnected() || isConnecting();

		PreferenceManager.getDefaultSharedPreferences(this).edit().
			putBoolean(PreferenceConstants.CONN_STARTUP, !oldState).commit();
		if (oldState) {
			serviceAdapter.disconnect();
			stopService(xmppServiceIntent);
		} else
			startConnection(false);
	}

	private int getConnectDisconnectIcon() {
		if (isConnected() || isConnecting()) {
			return R.drawable.ic_menu_unplug;
		}
		return R.drawable.ic_menu_plug;
	}

	private String getConnectDisconnectText() {
		if (isConnected() || isConnecting()) {
			return getString(R.string.Menu_disconnect);
		}
		return getString(R.string.Menu_connect);
	}

	private void registerXMPPService() {
		Log.i(TAG, "called startXMPPService()");
		xmppServiceIntent = new Intent(this, XMPPService.class);
		xmppServiceIntent.setAction("org.yaxim.androidclient.XMPPSERVICE");

		xmppServiceConnection = new ServiceConnection() {

			@TargetApi(Build.VERSION_CODES.HONEYCOMB) // required for Sherlock's invalidateOptionsMenu */
			public void onServiceConnected(ComponentName name, IBinder service) {
				Log.i(TAG, "called onServiceConnected()");
				serviceAdapter = new XMPPRosterServiceAdapter(
						IXMPPRosterService.Stub.asInterface(service));
				serviceAdapter.registerUICallback(rosterCallback);
				Log.i(TAG, "getConnectionState(): "
						+ serviceAdapter.getConnectionState());
				invalidateOptionsMenu();	// to load the action bar contents on time for access to icons/progressbar
				ConnectionState cs = serviceAdapter.getConnectionState();
				updateConnectionState(cs);
				updateRoster();

				// when returning from prefs to main activity, apply new config
				if (mConfig.reconnect_required && cs == ConnectionState.ONLINE) {
					// login config changed, force reconnection
					serviceAdapter.disconnect();
					serviceAdapter.connect();
				} else if (mConfig.presence_required && isConnected())
					serviceAdapter.setStatusFromConfig();

				// handle server-related intents after connecting to the backend
				handleJabberIntent();
			}

			public void onServiceDisconnected(ComponentName name) {
				Log.i(TAG, "called onServiceDisconnected()");
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

	private void registerListAdapter() {

		rosterListAdapter = new RosterExpListAdapter(this);
		getExpandableListView().setAdapter(rosterListAdapter);
	}

	private void createUICallback() {
		rosterCallback = new IXMPPRosterCallback.Stub() {
			@Override
			public void connectionStateChanged(final int connectionstate)
						throws RemoteException {
				mainHandler.post(new Runnable() {
					@TargetApi(Build.VERSION_CODES.HONEYCOMB) // required for Sherlock's invalidateOptionsMenu */
					public void run() {
						ConnectionState cs = ConnectionState.values()[connectionstate];
						//Log.d(TAG, "connectionStatusChanged: " + cs);
						updateConnectionState(cs);
						invalidateOptionsMenu();
					}
				});
			}
		};
	}

	// store mGroupsExpanded into prefs (this is a hack, but SQLite /
	// content providers suck wrt. virtual groups)
	public void storeExpandedState() {
		SharedPreferences.Editor prefedit = PreferenceManager
				.getDefaultSharedPreferences(this).edit();
		for (HashMap.Entry<String, Boolean> item : mGroupsExpanded.entrySet()) {
			prefedit.putBoolean("expanded_" + item.getKey(), item.getValue());
		}
		prefedit.commit();
	}

	// get the name of a roster group from the cursor
	public String getGroupName(int groupId) {
		// default group is "" and MUC group is "\uFFFF"
		return java.net.URLEncoder.encode(getPackedItemRow(
					ExpandableListView.getPackedPositionForGroup(groupId),
						RosterConstants.GROUP));
	}

	public void restoreGroupsExpanded() {
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this);
		for (int count = 0; count < rosterListAdapter.getGroupCount(); count++) {
			String name = getGroupName(count);
			if (!mGroupsExpanded.containsKey(name))
				mGroupsExpanded.put(name, prefs.getBoolean("expanded_" + name, true));
			if (mGroupsExpanded.get(name))
				getExpandableListView().expandGroup(count);
			else
				getExpandableListView().collapseGroup(count);
		}
	}

	private void checkIgnoreBatteryOptimization() {
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (prefs.getLong(PreferenceConstants.DOZE_NAG, 0) > 0)
				return; // we asked the user already
			String pn = getPackageName();
			if (!((PowerManager) getSystemService(POWER_SERVICE)).isIgnoringBatteryOptimizations(pn)) {
				prefs.edit().putLong(PreferenceConstants.DOZE_NAG, System.currentTimeMillis()).commit();
				startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
						.setData(Uri.parse("package:" + pn)));

			}
		}
	}

	private void showFirstStartUpDialog() {
		String jid = null;
		String preauth = null;
		Intent i = getIntent();
		if (!mHandledIntent && isJabberIntentAction(i.getAction()) && transmogrifyXmppUri(i)) {
			Uri data = i.getData();
			if (data.getQueryParameter("register") != null) {
				jid = data.getAuthority();
				preauth = data.getQueryParameter("preauth");
				mHandledIntent = true;
			}
		}
		if (mFirstStartDialog == null)
			mFirstStartDialog = new FirstStartDialog(this, serviceAdapter);
		mFirstStartDialog.show();
		if (!TextUtils.isEmpty(jid))
			mFirstStartDialog.setJID(jid, preauth);
	}
	private void showFirstStartUpDialogIfPrefsEmpty() {
		Log.i(TAG, "showFirstStartUpDialogIfPrefsEmpty, JID: "
						+ mConfig.jabberID);
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this);
		if (mConfig.jabberID.length() < 3 || prefs.contains(PreferenceConstants.FIRSTRUN)) {
			// load preference defaults
			PreferenceManager.setDefaultValues(this, R.xml.mainprefs, false);
			PreferenceManager.setDefaultValues(this, R.xml.accountprefs, false);

			// prevent a start-up with empty JID
			prefs.edit().putBoolean(PreferenceConstants.CONN_STARTUP, false).commit();

			// show welcome dialog
			showFirstStartUpDialog();
		} else {
			XMPPHelper.setNFCInvitation(this, mConfig);
			// implement auto-connect when started from launcher
			if (!mConfig.autoConnect && Intent.ACTION_MAIN.equals(getIntent().getAction()))
				prefs.edit().putBoolean(PreferenceConstants.CONN_STARTUP, true).commit();
			checkIgnoreBatteryOptimization();
		}
	}

	public static Intent createIntent(Context context) {
		Intent i = new Intent(context, MainWindow.class);
		i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		return i;
	}

	protected void showToastNotification(int message) {
		Toast tmptoast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
		tmptoast.show();
	}

	private static final String OFFLINE_EXCLUSION =
			RosterConstants.STATUS_MODE + " > " + StatusMode.offline.ordinal();
	private static final String countAvailableMembers =
			"SELECT COUNT() FROM " + RosterProvider.TABLE_ROSTER + " inner_query" +
					" WHERE inner_query." + RosterConstants.GROUP + " = " +
					RosterProvider.QUERY_ALIAS + "." + RosterConstants.GROUP +
					" AND inner_query." + OFFLINE_EXCLUSION;
	private static final String countMembers =
			"SELECT COUNT() FROM " + RosterProvider.TABLE_ROSTER + " inner_query" +
					" WHERE inner_query." + RosterConstants.GROUP + " = " +
					RosterProvider.QUERY_ALIAS + "." + RosterConstants.GROUP;
	private static final String[] GROUPS_QUERY_COUNTED = new String[] {
		RosterConstants._ID,
		RosterConstants.GROUP,
		"(" + countAvailableMembers + ") || '/' || (" + countMembers + ") AS members"
	};

	final String countAvailableMembersTotals =
			"SELECT COUNT() FROM " + RosterProvider.TABLE_ROSTER + " inner_query" +
					" WHERE inner_query." + OFFLINE_EXCLUSION;
	final String countMembersTotals =
			"SELECT COUNT() FROM " + RosterProvider.TABLE_ROSTER;
	final String[] GROUPS_QUERY_CONTACTS_DISABLED = new String[] {
			RosterConstants._ID,
			"'' AS " + RosterConstants.GROUP,
			"(" + countAvailableMembersTotals + ") || '/' || (" + countMembersTotals + ") AS members",
			"MIN(" + RosterConstants._ID + ")" // cheat: aggregate function to only return a single entry
	};

	private static final String[] GROUPS_FROM = new String[] {
		RosterConstants.GROUP,
		"members"
	};
	private static final int[] GROUPS_TO = new int[] {
		R.id.groupname,
		R.id.members
	};
	// virtual boolean column `subscribe` to sort pending subscriptions to the top
	private static final String[] ROSTER_QUERY = new String[] {
		RosterConstants._ID,
		RosterConstants.JID,
		RosterConstants.ALIAS,
		RosterConstants.STATUS_MODE,
		RosterConstants.STATUS_MESSAGE,
		"(" + RosterConstants.STATUS_MODE + " == " + StatusMode.subscribe.ordinal() + ") AS subscribe",
	};

	public class RosterExpListAdapter extends SimpleCursorTreeAdapter {

		public RosterExpListAdapter(Context context) {
			super(context, /* cursor = */ null, 
					R.layout.maingroup_row, GROUPS_FROM, GROUPS_TO,
					R.layout.mainchild_row,
					new String[] {
						RosterConstants.ALIAS,
						RosterConstants.STATUS_MESSAGE,
						RosterConstants.STATUS_MODE
					},
					new int[] {
						R.id.roster_screenname,
						R.id.roster_statusmsg,
						R.id.roster_icon
					});
		}

		public void requery() {
			String selectWhere = null;
			if (!mConfig.showOffline)
				selectWhere = OFFLINE_EXCLUSION;

			Uri query_uri = RosterProvider.GROUPS_URI;
			String[] query = GROUPS_QUERY_COUNTED;
			if(!mConfig.enableGroups) {
				query = GROUPS_QUERY_CONTACTS_DISABLED;
				query_uri = RosterProvider.CONTENT_URI;
			}
			Cursor cursor = getContentResolver().query(query_uri,
					query, selectWhere, null, RosterConstants.GROUP);
			Cursor oldCursor = getCursor();
			changeCursor(cursor);
			if (oldCursor != null)
				stopManagingCursor(oldCursor);
		}

		@Override
		protected Cursor getChildrenCursor(Cursor groupCursor) {
			// Given the group, we return a cursor for all the children within that group
			String selectWhere;
			int idx = groupCursor.getColumnIndex(RosterConstants.GROUP);
			String groupname = groupCursor.getString(idx);
			String[] args = null;

			if(!mConfig.enableGroups) {
				selectWhere = mConfig.showOffline ? "" : OFFLINE_EXCLUSION;
			} else {
				selectWhere = mConfig.showOffline ? "" : OFFLINE_EXCLUSION + " AND ";
				selectWhere += RosterConstants.GROUP + " = ?";
				args = new String[] { groupname };
			}
			return getContentResolver().query(RosterProvider.CONTENT_URI, ROSTER_QUERY,
				selectWhere, args, "subscribe DESC, " + RosterConstants.ALIAS + " COLLATE NOCASE");
		}

		@Override
		protected void bindGroupView(View view, Context context, Cursor cursor, boolean isExpanded) {
			super.bindGroupView(view, context, cursor, isExpanded);
			if (cursor.getString(cursor.getColumnIndexOrThrow(RosterConstants.GROUP)).length() == 0) {
				TextView groupname = (TextView)view.findViewById(R.id.groupname);
				groupname.setText(mConfig.enableGroups ? R.string.default_group : R.string.all_contacts_group);
			} else
			if (cursor.getString(cursor.getColumnIndexOrThrow(RosterConstants.GROUP)).equals(RosterProvider.RosterConstants.MUCS)) {
				((TextView)view.findViewById(R.id.groupname)).setText(R.string.muc_group);
			}
		}

		@Override
		protected void bindChildView(View view, Context context, Cursor cursor, boolean isLastChild) {
			super.bindChildView(view, context, cursor, isLastChild);
			TextView statusmsg = (TextView)view.findViewById(R.id.roster_statusmsg);
			boolean hasStatus = statusmsg.getText() != null && statusmsg.getText().length() > 0;
			statusmsg.setVisibility(hasStatus ? View.VISIBLE : View.GONE);

			String jid = cursor.getString(cursor.getColumnIndex(RosterConstants.JID));
			TextView unreadmsg = (TextView)view.findViewById(R.id.roster_unreadmsg_cnt);
			Integer count = mUnreadCounters.get(jid);
			if (count == null)
				count = 0;
			unreadmsg.setText(count.toString());
			unreadmsg.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
			unreadmsg.bringToFront();
		}

		 protected void setViewImage(ImageView v, String value) {
			int presenceMode = Integer.parseInt(value);
			v.setImageResource(getIconForPresenceMode(presenceMode));
		 }

		private int getIconForPresenceMode(int presenceMode) {
			if (!isConnected()) // override icon if we are offline
				presenceMode = 0;
			return StatusMode.values()[presenceMode].getDrawableId();
		}
	}

	private class RosterObserver extends ContentObserver {
		public RosterObserver() {
			super(mainHandler);
		}
		public void onChange(boolean selfChange) {
			Log.d(TAG, "RosterObserver.onChange: " + selfChange);
			// work around race condition in ExpandableListView, which collapses
			// groups rand-f**king-omly
			if (rosterListAdapter != null)
				mainHandler.postDelayed(new Runnable() {
					public void run() {
						restoreGroupsExpanded();
					}}, 100);
		}
	}

	private HashMap<String, Integer> mUnreadCounters = new HashMap<String, Integer>();
	private class LoadUnreadTask extends AsyncTask<Void, Void, HashMap<String, Integer>> {
		@Override
		protected HashMap<String, Integer> doInBackground(Void...voids) {
			final String[] PROJECTION = new String[] { ChatConstants.JID, "count(*)" };
			final String SELECTION = ChatConstants.DIRECTION + " = " + ChatConstants.INCOMING + " AND " +
					ChatConstants.DELIVERY_STATUS + " = " + ChatConstants.DS_NEW +
					") GROUP BY (" + ChatConstants.JID; // hack!

			Cursor c = getContentResolver().query(ChatProvider.CONTENT_URI,
					PROJECTION, SELECTION, null, null);
			HashMap<String, Integer> result = new HashMap<String, Integer>();
			if(c!=null){
				while (c.moveToNext())
					result.put(c.getString(0), c.getInt(1));
				c.close();
			}
			return result;
		}

		@Override
		protected void onPostExecute(HashMap<String, Integer> result) {
			mUnreadCounters = result;
			getExpandableListView().invalidateViews();
		}

	}

	private class ChatObserver extends ContentObserver {
		public ChatObserver() {
			super(mainHandler);
		}
		public void onChange(boolean selfChange) {
			updateRoster();
		}
	}
	
}
