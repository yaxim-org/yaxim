package org.yaxim.androidclient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.jivesoftware.smackx.muc.MultiUserChat;
import org.yaxim.androidclient.chat.MUCChatWindow;
import org.yaxim.androidclient.chat.XMPPChatServiceAdapter;
import org.yaxim.androidclient.data.ChatHelper;
import org.yaxim.androidclient.data.ChatProvider;
import org.yaxim.androidclient.data.ChatProvider.ChatConstants;
import org.yaxim.androidclient.data.ChatRoomHelper;
import org.yaxim.androidclient.data.RosterProvider;
import org.yaxim.androidclient.data.RosterProvider.RosterConstants;
import org.yaxim.androidclient.data.YaximConfiguration;
import org.yaxim.androidclient.dialogs.AddRosterItemDialog;
import org.yaxim.androidclient.dialogs.ChangeStatusDialog;
import org.yaxim.androidclient.dialogs.ConfirmDialog;
import org.yaxim.androidclient.dialogs.EditMUCDialog;
import org.yaxim.androidclient.dialogs.FirstStartDialog;
import org.yaxim.androidclient.dialogs.GroupNameView;
import org.yaxim.androidclient.preferences.AccountPrefs;
import org.yaxim.androidclient.preferences.MainPrefs;
import org.yaxim.androidclient.service.IXMPPChatService;
import org.yaxim.androidclient.service.IXMPPMucService;
import org.yaxim.androidclient.service.XMPPService;
import org.yaxim.androidclient.util.ConnectionState;
import org.yaxim.androidclient.util.PreferenceConstants;
import org.yaxim.androidclient.util.StatusMode;
import org.yaxim.androidclient.util.XMPPHelper;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.ComponentName;
import android.content.DialogInterface.OnClickListener;
import android.content.ContentResolver;
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
import android.opengl.Visibility;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.View;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

import org.yaxim.androidclient.util.SimpleCursorTreeAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ExpandableListView.ExpandableListContextMenuInfo;
import org.yaxim.androidclient.IXMPPRosterCallback;
import org.yaxim.androidclient.R;
import org.yaxim.androidclient.IXMPPRosterCallback.Stub;
import org.yaxim.androidclient.service.IXMPPRosterService;


import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockExpandableListActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.Window;
import com.nullwire.trace.ExceptionHandler;

public class MainWindow extends SherlockExpandableListActivity {

	private static final String TAG = "yaxim.MainWindow";

	private YaximConfiguration mConfig;

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

	private ActionBar actionBar;
	private String mTheme;
	private boolean mHandledIntent = false;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.i(TAG, getString(R.string.build_version));
		mConfig = YaximApplication.getConfig(this);
		mTheme = mConfig.theme;
		setTheme(mConfig.getTheme());
		super.onCreate(savedInstanceState);
		
		requestWindowFeature(Window.FEATURE_ACTION_BAR);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		actionBar = getSupportActionBar();
		actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_TITLE, ActionBar.DISPLAY_SHOW_TITLE);
		actionBar.setHomeButtonEnabled(true);
		registerCrashReporter();

		getContentResolver().registerContentObserver(RosterProvider.CONTENT_URI,
				true, mRosterObserver);
		getContentResolver().registerContentObserver(ChatProvider.CONTENT_URI,
				true, mChatObserver);
		registerXMPPService();
		createUICallback();
		setupContenView();
		registerListAdapter();

		mHandledIntent = (getIntent().getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		getContentResolver().unregisterContentObserver(mRosterObserver);
		getContentResolver().unregisterContentObserver(mChatObserver);
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
	protected void onNewIntent(Intent i) {
		setIntent(i);
		mHandledIntent = false;
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (serviceAdapter != null)
			serviceAdapter.unregisterUICallback(rosterCallback);

		YaximApplication.getApp(this).mMTM.unbindDisplayActivity(this);
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

		YaximApplication.getApp(this).mMTM.bindDisplayActivity(this);

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

	public boolean openChatWithJid(String jid, String text) {
		Log.d(TAG, "openChatWithJid: " + jid);

		List<String[]> contacts = ChatHelper.getRosterContacts(this, ChatHelper.ROSTER_FILTER_ALL);
		for (String[] c : contacts) {
			if (jid.equalsIgnoreCase(c[0])) {
				// found it
				startChatActivity(c[0], c[1], text);
				finish();
				return true;
			}
		}
		// if we have a message, open chat to JID
		if (text != null) {
			startChatActivity(jid, jid, text);
			finish();
			return true;
		}
		return false;
	}

	public boolean isJabberIntentAction(String action) {
		return Intent.ACTION_VIEW.equals(action) ||
			android.nfc.NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action);
	}
	public boolean transmogrifyXmppUri(Intent intent) {
		Uri data = intent.getData();
		if ("xmpp".equalsIgnoreCase(data.getScheme())) {
			if (data.isOpaque()) {
				// cheat around android's unwillingness to parse opaque URIs
				data = Uri.parse(data.toString().replaceFirst(":", "://").replace(';', '&'));
			}
		} else if ("yax.im".equalsIgnoreCase(data.getHost())) {
			// convert URI fragment (after # sign) into xmpp URI
			String jid = data.getFragment();
			data = Uri.parse("xmpp://" + jid);
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
				else return false;
			} catch (Exception e) {
				Log.d(TAG, "Failed to parse URI " + data);
				return false;
			}
		} else
			return false;
		Log.d(TAG, "transmogrifyXmppUri: " + intent.getData() + " --> " + data);
		intent.setData(data);
		return true;
	}

	public void handleJabberIntent() {
		Intent intent = getIntent();
		Log.d(TAG, "handleJabberIntent: " + intent);
		String action = intent.getAction();
		Uri data = intent.getData();
		if (action == null || data == null || mHandledIntent)
			return;
		if (action.equals(Intent.ACTION_SENDTO) && data.getHost().equals("jabber")) {
			// 1. look for JID in roster; 2. attempt to add
			String jid = data.getPathSegments().get(0);
			if (!openChatWithJid(jid, null) &&
			    !addToRosterDialog(jid))
				finish();
		} else if (isJabberIntentAction(action) && transmogrifyXmppUri(intent)) {
			data = intent.getData();
			String jid = data.getAuthority();
			String body = data.getQueryParameter("body");
			String name = data.getQueryParameter("name");
			String preauth = data.getQueryParameter("preauth");
			if (data.getQueryParameter("roster") != null || data.getQueryParameter("subscribe") != null) {
				addToRosterDialog(jid, name, preauth);
			} else if (data.getQueryParameter("join") != null) {
				// TODO: nickname
				new EditMUCDialog(this, jid, data.getQueryParameter("body"),
					null, data.getQueryParameter("password")).withNick(mConfig.userName).show();
			} else if (!openChatWithJid(jid, body) &&
				   !addToRosterDialog(jid, name, preauth)) {
				finish();
			} else return;
		} else return;
		// clear the intent data to prevent re-triggering
		getIntent().setData(null);
		mHandledIntent = true;
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
		loadUnreadCounters();
		rosterListAdapter.requery();
		restoreGroupsExpanded();
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
			getMenuInflater().inflate(R.menu.roster_item_contextmenu, menu);
			menuName = String.format("%s (%s)",
				getPackedItemRow(packedPosition, RosterConstants.ALIAS),
				getPackedItemRow(packedPosition, RosterConstants.JID));
			isMuc = ChatRoomHelper.isRoom(this, getPackedItemRow(packedPosition, RosterConstants.JID));
		} else {
			menuName = getPackedItemRow(packedPosition, RosterConstants.GROUP);
			if (menuName.equals("") || menuName.equals(RosterConstants.MUCS))
				return; // no options for default menu
			getMenuInflater().inflate(R.menu.roster_group_contextmenu, menu);
		}

		// display contact menu for contacts
		menu.setGroupVisible(R.id.roster_contextmenu_item_menu, isChild);
		menu.setGroupVisible(R.id.roster_contextmenu_contact_menu, isChild && !isMuc);
		// display group menu for non-standard groups
		menu.setGroupVisible(R.id.roster_contextmenu_group_menu, !isChild &&
				(menuName.length() > 0));
		// display stripped down menu for MUCs
		menu.setGroupVisible(R.id.roster_contextmenu_muc_menu, isChild && isMuc);

		menu.setHeaderTitle(getString(R.string.roster_contextmenu_title, menuName));
	}

	void removeChatHistory(final String JID) {
		getContentResolver().delete(ChatProvider.CONTENT_URI,
				ChatProvider.ChatConstants.JID + " = ?", new String[] { JID });
	}

	void removeRosterItemDialog(final String JID, final String userName) {
		new AlertDialog.Builder(this)
			.setTitle(R.string.deleteRosterItem_title)
			.setMessage(getString(R.string.deleteRosterItem_text, userName, JID))
			.setPositiveButton(android.R.string.yes,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							serviceAdapter.removeRosterItem(JID);
						}
					})
			.setNegativeButton(android.R.string.no, null)
			.create().show();
	}

	boolean addToRosterDialog(String jid, String alias, String token) {
		if (serviceAdapter != null && serviceAdapter.isAuthenticated()) {
			new AddRosterItemDialog(this, serviceAdapter, jid)
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
			.setNegativeButton(R.string.subscription_reject,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							serviceAdapter.sendPresenceRequest(jid, "unsubscribed");
						}
					})
			.create().show();
	}

	abstract class EditOk {
		abstract public void ok(String result);
	}

	void editTextDialog(int titleId, CharSequence message, String text,
			final EditOk ok) {
		LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
		View layout = inflater.inflate(R.layout.edittext_dialog,
		                               (ViewGroup) findViewById(R.id.layout_root));

		TextView messageView = (TextView) layout.findViewById(R.id.text);
		messageView.setText(message);
		final EditText input = (EditText) layout.findViewById(R.id.editText);
		input.setTransformationMethod(android.text.method.SingleLineTransformationMethod.getInstance());
		input.setText(text);
		new AlertDialog.Builder(this)
			.setTitle(titleId)
			.setView(layout)
			.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							String newName = input.getText().toString();
							if (newName.length() != 0)
								ok.ok(newName);
						}})
			.setNegativeButton(android.R.string.cancel, null)
			.create().show();
	}

	void renameRosterItemDialog(final String JID, final String userName) {
		String newUserName = userName;
		if (JID.equals(userName))
			newUserName = XMPPHelper.capitalizeString(JID.split("@")[0]);
		editTextDialog(R.string.RenameEntry_title,
				getString(R.string.RenameEntry_summ, userName, JID),
				newUserName, new EditOk() {
					public void ok(String result) {
						serviceAdapter.renameRosterItem(JID, result);
					}
				});
	}

	void renameRosterGroupDialog(final String groupName) {
		editTextDialog(R.string.RenameGroup_title,
				getString(R.string.RenameGroup_summ, groupName),
				groupName, new EditOk() {
					public void ok(String result) {
						serviceAdapter.renameRosterGroup(groupName, result);
					}
				});
	}

	void moveRosterItemToGroupDialog(final String jabberID) {
		LayoutInflater inflater = (LayoutInflater)getSystemService(
			      LAYOUT_INFLATER_SERVICE);
		View group = inflater.inflate(R.layout.moverosterentrytogroupview, null, false);
		final GroupNameView gv = (GroupNameView)group.findViewById(R.id.moverosterentrytogroupview_gv);
		gv.setGroupList(getRosterGroups());
		new AlertDialog.Builder(this)
			.setTitle(R.string.MoveRosterEntryToGroupDialog_title)
			.setView(group)
			.setPositiveButton(android.R.string.ok,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						Log.d(TAG, "new group: " + gv.getGroupName());
						serviceAdapter.moveRosterItemToGroup(jabberID,
								gv.getGroupName());
					}
				})
			.setNegativeButton(android.R.string.cancel, null)
			.create().show();
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
			case R.id.roster_contextmenu_contact_open_chat:
				startChatActivity(userJid, userName, null);
				return true;

			case R.id.roster_contextmenu_contact_mark_as_read:
				ChatHelper.markAsRead(this, userJid);
				return true;

			case R.id.roster_contextmenu_contact_delmsg:
				ChatHelper.removeChatHistoryDialog(this, userJid, userName);
				return true;

			case R.id.roster_contextmenu_contact_delete:
				if (!isConnected()) { showToastNotification(R.string.Global_authenticate_first); return true; }
				removeRosterItemDialog(userJid, userName);
				return true;

			case R.id.roster_contextmenu_contact_rename:
				if (!isConnected()) { showToastNotification(R.string.Global_authenticate_first); return true; }
				renameRosterItemDialog(userJid, userName);
				return true;

			case R.id.roster_contextmenu_contact_request_auth:
				if (!isConnected()) { showToastNotification(R.string.Global_authenticate_first); return true; }
				serviceAdapter.sendPresenceRequest(userJid, "subscribe");
				return true;

			case R.id.roster_contextmenu_contact_change_group:
				if (!isConnected()) { showToastNotification(R.string.Global_authenticate_first); return true; }
				moveRosterItemToGroupDialog(userJid);
				return true;
			case R.id.roster_contextmenu_contact_share:
				XMPPHelper.shareLink(this, R.string.roster_contextmenu_contact_share,
						XMPPHelper.createRosterLinkHTTPS(userJid));
				return true;
			case R.id.roster_contextmenu_muc_share:
				XMPPHelper.shareLink(this, R.string.roster_contextmenu_contact_share,
						XMPPHelper.createMucLinkHTTPS(userJid));
				return true;

			case R.id.roster_contextmenu_muc_edit:
				new EditMUCDialog(this, userJid).show();
				return true;
			case R.id.roster_contextmenu_muc_leave:
				ConfirmDialog.showMucLeave(this, userJid);
				return true;
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

			}
		}
		return false;
	}

	private boolean isChild(long packedPosition) {
		int type = ExpandableListView.getPackedPositionType(packedPosition);
		return (type == ExpandableListView.PACKED_POSITION_TYPE_CHILD);
	}

	private void startChatActivity(String user, String userName, String message) {
		Intent chatIntent = new Intent(this,
				org.yaxim.androidclient.chat.ChatWindow.class);
		if (ChatRoomHelper.isRoom(this, user))
			chatIntent.setClass(this, MUCChatWindow.class);
		Uri userNameUri = Uri.parse(user);
		chatIntent.setData(userNameUri);
		chatIntent.putExtra(org.yaxim.androidclient.chat.ChatWindow.INTENT_EXTRA_USERNAME, userName);
		if (message != null) {
			chatIntent.putExtra(org.yaxim.androidclient.chat.ChatWindow.INTENT_EXTRA_MESSAGE, message);
		}
		startActivity(chatIntent);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getSupportMenuInflater().inflate(R.menu.roster_options, menu);
		return true;
	}

	void setMenuItem(Menu menu, int itemId, int iconId, CharSequence title) {
		com.actionbarsherlock.view.MenuItem item = menu.findItem(itemId);
		if (item == null)
			return;
		item.setIcon(iconId);
		item.setTitle(title);
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		setMenuItem(menu, R.id.menu_connect, getConnectDisconnectIcon(),
				getConnectDisconnectText());
		setMenuItem(menu, R.id.menu_show_hide, getShowHideMenuIcon(),
				getShowHideMenuText());
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(com.actionbarsherlock.view.MenuItem item) {
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
		actionBar.setIcon(getStatusActionIcon());

		if (mConfig.statusMessage.equals("")) {
			actionBar.setSubtitle(null);
		} else {
			actionBar.setSubtitle(mConfig.statusMessage);
		}
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

	private boolean applyMainMenuChoice(com.actionbarsherlock.view.MenuItem item) {

		int itemID = item.getItemId();

		switch (itemID) {
		case R.id.menu_connect:
			toggleConnection();
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
			new EditMUCDialog(this).withNick(mConfig.userName).show();
			return true;
		case R.id.menu_send_invitation:
			XMPPHelper.shareLink(this, R.string.Menu_send_invitation,
					XMPPHelper.createInvitationLinkHTTPS(mConfig.jabberID,
						mConfig.createInvitationCode()));
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
			startChatActivity(userJid, userName, i.getStringExtra(Intent.EXTRA_TEXT));
			finish();
		} else {
			StatusMode s = StatusMode.values()[c.getInt(c.getColumnIndexOrThrow(RosterConstants.STATUS_MODE))];
			if (s == StatusMode.subscribe)
				rosterAddRequestedDialog(userJid, userName,
					c.getString(c.getColumnIndexOrThrow(RosterConstants.STATUS_MESSAGE)));
			else
				startChatActivity(userJid, userName, null);
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
			if (cs == ConnectionState.DISCONNECTED && PreferenceManager.getDefaultSharedPreferences(this)
									.contains(PreferenceConstants.INITIAL_CREATE)) {
				// somehow, cs==OFFLINE is triggered twice, but cs==DISCONNECTED only once
				String error = serviceAdapter.getConnectionStateString().replace("conflict(-1) ", "");
				if (error.contains("\n")) // TODO: work around getConnectionStateString() returning two lines
					error = error.split("\n")[1];
				if (error.contains("SASL authentication failed")) // TODO: hack to circumvent old smack
					error = getString(R.string.StartupDialog_auth_failed);
				Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
				showFirstStartUpDialog();
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
			PreferenceManager.getDefaultSharedPreferences(this).edit().
				remove(PreferenceConstants.INITIAL_CREATE).commit();
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
		setListAdapter(rosterListAdapter);
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
		for (int count = 0; count < getExpandableListAdapter().getGroupCount(); count++) {
			String name = getGroupName(count);
			if (!mGroupsExpanded.containsKey(name))
				mGroupsExpanded.put(name, prefs.getBoolean("expanded_" + name, true));
			if (mGroupsExpanded.get(name))
				getExpandableListView().expandGroup(count);
			else
				getExpandableListView().collapseGroup(count);
		}
	}

	private void showFirstStartUpDialog() {
		if (mFirstStartDialog == null)
			mFirstStartDialog = new FirstStartDialog(this, serviceAdapter);
		mFirstStartDialog.show();
	}
	private void showFirstStartUpDialogIfPrefsEmpty() {
		Log.i(TAG, "showFirstStartUpDialogIfPrefsEmpty, JID: "
						+ mConfig.jabberID);
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this);
		if (mConfig.jabberID.length() < 3 || prefs.contains(PreferenceConstants.INITIAL_CREATE)) {
			// load preference defaults
			PreferenceManager.setDefaultValues(this, R.layout.mainprefs, false);
			PreferenceManager.setDefaultValues(this, R.layout.accountprefs, false);

			// prevent a start-up with empty JID
			prefs.edit().putBoolean(PreferenceConstants.CONN_STARTUP, false).commit();

			// show welcome dialog
			showFirstStartUpDialog();
		} else
			XMPPHelper.setNFCInvitation(this, mConfig);
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

	private void registerCrashReporter() {
		if (mConfig.reportCrash) {
			ExceptionHandler.register(this, "http://duenndns.de/yaxim-crash/");
		}
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
	private static final String[] GROUPS_QUERY = new String[] {
		RosterConstants._ID,
		RosterConstants.GROUP,
	};
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
	private static final String[] ROSTER_QUERY = new String[] {
		RosterConstants._ID,
		RosterConstants.JID,
		RosterConstants.ALIAS,
		RosterConstants.STATUS_MODE,
		RosterConstants.STATUS_MESSAGE,
	};

	public List<String> getRosterGroups() {
		// we want all, online and offline
		List<String> list = new ArrayList<String>();
		Cursor cursor = getContentResolver().query(RosterProvider.GROUPS_URI, GROUPS_QUERY,
					null, null, RosterConstants.GROUP);
		int idx = cursor.getColumnIndex(RosterConstants.GROUP);
		cursor.moveToFirst();
		while (!cursor.isAfterLast()) {
			list.add(cursor.getString(idx));
			cursor.moveToNext();
		}
		cursor.close();
		list.remove(RosterProvider.RosterConstants.MUCS);
		return list;
	}

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
				selectWhere, args, null);
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
			if (getExpandableListAdapter() != null)
				mainHandler.postDelayed(new Runnable() {
					public void run() {
						restoreGroupsExpanded();
					}}, 100);
		}
	}

	private HashMap<String, Integer> mUnreadCounters = new HashMap<String, Integer>();
	private void loadUnreadCounters() {
		final String[] PROJECTION = new String[] { ChatConstants.JID, "count(*)" };
		final String SELECTION = ChatConstants.DIRECTION + " = " + ChatConstants.INCOMING + " AND " +
			ChatConstants.DELIVERY_STATUS + " = " + ChatConstants.DS_NEW +
			") GROUP BY (" + ChatConstants.JID; // hack!

		Cursor c = getContentResolver().query(ChatProvider.CONTENT_URI,
				PROJECTION, SELECTION, null, null);
		mUnreadCounters.clear();
		if(c!=null){
			while (c.moveToNext())
				mUnreadCounters.put(c.getString(0), c.getInt(1));
			c.close();
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
