package org.yaxim.androidclient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.yaxim.androidclient.data.ChatProvider;
import org.yaxim.androidclient.data.ChatProvider.ChatConstants;
import org.yaxim.androidclient.data.RosterProvider;
import org.yaxim.androidclient.data.RosterProvider.RosterConstants;
import org.yaxim.androidclient.data.YaximConfiguration;
import org.yaxim.androidclient.dialogs.AddRosterItemDialog;
import org.yaxim.androidclient.dialogs.ChangeStatusDialog;
import org.yaxim.androidclient.dialogs.FirstStartDialog;
import org.yaxim.androidclient.dialogs.GroupNameView;
import org.yaxim.androidclient.preferences.MainPrefs;
import org.yaxim.androidclient.service.XMPPService;
import org.yaxim.androidclient.util.ConnectionState;
import org.yaxim.androidclient.util.PreferenceConstants;
import org.yaxim.androidclient.util.StatusMode;

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
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ImageView;
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
	private boolean showOffline;

	private String mStatusMessage;
	private StatusMode mStatusMode;

	private ContentObserver mRosterObserver = new RosterObserver();
	private ContentObserver mChatObserver = new ChatObserver();
	private HashMap<String, Boolean> mGroupsExpanded = new HashMap<String, Boolean>();

	private ActionBar actionBar;
	private String mTheme;

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
		mConfig = new YaximConfiguration(PreferenceManager
				.getDefaultSharedPreferences(this));
		registerCrashReporter();

		showFirstStartUpDialogIfPrefsEmpty();
		getContentResolver().registerContentObserver(RosterProvider.CONTENT_URI,
				true, mRosterObserver);
		getContentResolver().registerContentObserver(ChatProvider.CONTENT_URI,
				true, mChatObserver);
		registerXMPPService();
		createUICallback();
		setupContenView();
		registerListAdapter();

		actionBar.setSubtitle(mStatusMessage);
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
		String groupName = getGroupName(groupPosition);
		if (groupClicked) {
			Log.d(TAG, "group status change: " + groupName + " -> " + isExpanded);
			mGroupsExpanded.put(groupName, isExpanded);
			groupClicked = false;
		//} else {
		//	if (!mGroupsExpanded.containsKey(name))
		//		restoreGroupsExpanded();
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
		getPreferences(PreferenceManager.getDefaultSharedPreferences(this));
		String theme = PreferenceManager.getDefaultSharedPreferences(this).getString(PreferenceConstants.THEME, "dark");
		if (theme.equals(mTheme) == false) {
			// restart
			Intent restartIntent = new Intent(this, MainWindow.class);
			restartIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(restartIntent);
			finish();
		}
		updateRoster();
		bindXMPPService();

		YaximApplication.getApp(this).mMTM.bindDisplayActivity(this);

		// handle SEND action
		handleSendIntent();

		// handle imto:// intent after restoring service connection
		mainHandler.post(new Runnable() {
			public void run() {
				handleJabberIntent();
			}});
	}

	public void handleSendIntent() {
		Intent intent = getIntent();
		String action = intent.getAction();
		if ((action != null) && (action.equals(Intent.ACTION_SEND))) {
			final String message = intent.getStringExtra(Intent.EXTRA_TEXT);

			List<String[]> contacts = getRosterContacts();
			int num_contacts = contacts.size();

			if (num_contacts == 0) return;

			final CharSequence[] screenNames = new CharSequence[num_contacts];
			final CharSequence[] jids = new CharSequence[num_contacts];
			int idx = 0;
			for (String[] c : contacts) {
				jids[idx] = c[0];
				screenNames[idx] = c[1];
				idx++;
			}

			AlertDialog.Builder builder = new AlertDialog.Builder(MainWindow.this);
			builder.setTitle(getText(R.string.chooseContact))
				.setCancelable(true)
				.setItems(screenNames, new DialogInterface.OnClickListener() {

					public void onClick(DialogInterface dialog, int item) {
						dialog.dismiss();
						startChatActivity(jids[item].toString(), screenNames[item].toString(), message);
						finish();
					}
				}).setOnCancelListener(new DialogInterface.OnCancelListener() {

					public void onCancel(DialogInterface dialog) {
					  finish();
					}
				}).create().show();
		} else return;
	}

	public void handleJabberIntent() {
		Intent intent = getIntent();
		String action = intent.getAction();
		Uri data = intent.getData();
		if ((action != null) && (action.equals(Intent.ACTION_SENDTO))
				&& data != null && data.getHost().equals("jabber")) {
			String jid = data.getPathSegments().get(0);
			Log.d(TAG, "handleJabberIntent: " + jid);

			List<String[]> contacts = getRosterContacts();
			for (String[] c : contacts) {
				if (jid.equalsIgnoreCase(c[0])) {
					// found it
					startChatActivity(c[0], c[1], null);
					finish();
					return;
				}
			}
			// did not find in roster, try to add
			if (serviceAdapter != null && serviceAdapter.isAuthenticated()) {
				new AddRosterItemDialog(this, serviceAdapter, jid).show();
			} else {
				showToastNotification(R.string.Global_authenticate_first);
				finish();
			}
		}
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
		return serviceAdapter != null && serviceAdapter.getConnectionState() == ConnectionState.CONNECTING;
	}

	public void updateRoster() {
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

		getMenuInflater().inflate(R.menu.roster_contextmenu, menu);

		// get the entry name for the item
		String menuName = getPackedItemRow(packedPosition,
				isChild ? RosterConstants.ALIAS : RosterConstants.GROUP);

		// display contact menu for contacts
		menu.setGroupVisible(R.id.roster_contextmenu_contact_menu, isChild);
		// display group menu for non-standard group
		menu.setGroupVisible(R.id.roster_contextmenu_group_menu, !isChild &&
				(menuName.length() > 0));

		menu.setHeaderTitle(getString(R.string.roster_contextmenu_title, menuName));
	}

	void removeChatHistory(final String JID) {
		getContentResolver().delete(ChatProvider.CONTENT_URI,
				ChatProvider.ChatConstants.JID + " = ?", new String[] { JID });
	}

	void removeChatHistoryDialog(final String JID, final String userName) {
		new AlertDialog.Builder(this)
			.setTitle(R.string.deleteChatHistory_title)
			.setMessage(getString(R.string.deleteChatHistory_text, userName, JID))
			.setPositiveButton(android.R.string.yes,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							removeChatHistory(JID);
						}
					})
			.setNegativeButton(android.R.string.no, null)
			.create().show();
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
		editTextDialog(R.string.RenameEntry_title,
				getString(R.string.RenameEntry_summ, userName, JID),
				userName, new EditOk() {
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

			case R.id.roster_contextmenu_contact_delmsg:
				removeChatHistoryDialog(userJid, userName);
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
				serviceAdapter.requestAuthorizationForRosterItem(userJid);
				return true;

			case R.id.roster_contextmenu_contact_change_group:
				if (!isConnected()) { showToastNotification(R.string.Global_authenticate_first); return true; }
				moveRosterItemToGroupDialog(userJid);
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
		actionBar.setIcon(getStatusActionIcon());
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
		if (showOffline) {
			getTheme().resolveAttribute(R.attr.OnlineFriends, tv, true);
			return tv.resourceId;
		}
		getTheme().resolveAttribute(R.attr.AllFriends, tv, true);
		return tv.resourceId;
	}

	private String getShowHideMenuText() {
		return showOffline ? getString(R.string.Menu_HideOff)
				: getString(R.string.Menu_ShowOff);
	}

	public StatusMode getStatusMode() {
		return mStatusMode;
	}

	public String getStatusMessage() {
		return mStatusMessage;
	}

	public int getAccountPriority() {
		return mConfig.priority;
	}

	public static String getStatusTitle(Context context, String status, String statusMessage) {
		status = context.getString(StatusMode.fromString(status).getTextId());

		if (statusMessage.length() > 0) {
			status = status + " (" + statusMessage + ")";
		}

		return status;
	}

	public void setAndSaveStatus(StatusMode statusMode, String message, int priority) {
		setStatus(statusMode, message);


		SharedPreferences.Editor prefedit = PreferenceManager
				.getDefaultSharedPreferences(this).edit();
		// do not save "offline" to prefs, or else!
		if (statusMode != StatusMode.offline)
			prefedit.putString(PreferenceConstants.STATUS_MODE, statusMode.name());
		prefedit.putString(PreferenceConstants.STATUS_MESSAGE, message);
		prefedit.putString(PreferenceConstants.PRIORITY, String.valueOf(priority));
		prefedit.commit();

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

	private void setStatus(StatusMode statusMode, String message) {
		mStatusMode = statusMode;
		mStatusMessage = message;

		// This and many other things like it should be done with observer
		actionBar.setIcon(getStatusActionIcon());

		if (mStatusMessage.equals("")) {
			actionBar.setSubtitle(null);
		} else {
			actionBar.setSubtitle(mStatusMessage);
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
			if (serviceAdapter.isAuthenticated()) {
				new AddRosterItemDialog(this, serviceAdapter).show();
			} else {
				showToastNotification(R.string.Global_authenticate_first);
			}
			return true;

		case R.id.menu_show_hide:
			setOfflinceContactsVisibility(!showOffline);
			updateRoster();
			return true;

		case android.R.id.home:
		case R.id.menu_status:
			new ChangeStatusDialog(this).show();
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

		}

		return false;

	}

	/** Sets if all contacts are shown in the roster or online contacts only. */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB) // required for Sherlock's invalidateOptionsMenu */
	private void setOfflinceContactsVisibility(boolean showOffline) {
		this.showOffline = showOffline;
		invalidateOptionsMenu();

		PreferenceManager.getDefaultSharedPreferences(this).edit().
			putBoolean(PreferenceConstants.SHOW_OFFLINE, showOffline).commit();
	}

	@Override
	public boolean onChildClick(ExpandableListView parent, View v,
			int groupPosition, int childPosition, long id) {

		long packedPosition = ExpandableListView.getPackedPositionForChild(groupPosition, childPosition);
		String userJid = getPackedItemRow(packedPosition, RosterConstants.JID);
		String userName = getPackedItemRow(packedPosition, RosterConstants.ALIAS);
		startChatActivity(userJid, userName, null);

		return true;
	}

	private void setConnectingStatus(boolean isConnecting) {

		String lastStatus;

		if (serviceAdapter != null && !serviceAdapter.isAuthenticated() &&
				(lastStatus = serviceAdapter.getConnectionStateString()) != null) {
			mConnectingText.setVisibility(View.VISIBLE);
			mConnectingText.setText(lastStatus);
		} else
		if (serviceAdapter == null || serviceAdapter.isAuthenticated() == false) {
			mConnectingText.setVisibility(View.VISIBLE);
			mConnectingText.setText(R.string.conn_offline);
		} else
			mConnectingText.setVisibility(View.GONE);
	}
	
	public void startConnection(boolean create_account) {
		setConnectingStatus(true);
		xmppServiceIntent.putExtra("create_account", create_account);
		startService(xmppServiceIntent);
	}

	// this function changes the prefs to keep the connection
	// according to the requested state
	private void toggleConnection() {
		boolean oldState = isConnected() || isConnecting();
		PreferenceManager.getDefaultSharedPreferences(this).edit().
			putBoolean(PreferenceConstants.CONN_STARTUP, !oldState).commit();
		setSupportProgressBarIndeterminateVisibility(true);
		if (oldState) {
			setConnectingStatus(false);
			(new Thread() {
				public void run() {
					serviceAdapter.disconnect();
					stopService(xmppServiceIntent);
				}
			}).start();

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
				actionBar.setIcon(getStatusActionIcon());	// refresh on orientation change
				setConnectingStatus(serviceAdapter.getConnectionState() == ConnectionState.CONNECTING);
				setSupportProgressBarIndeterminateVisibility(serviceAdapter.getConnectionState() == ConnectionState.CONNECTING);
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
			public void connectionStatusChanged(final boolean isConnected,
						final boolean willReconnect)
						throws RemoteException {
				mainHandler.post(new Runnable() {
					@TargetApi(Build.VERSION_CODES.HONEYCOMB) // required for Sherlock's invalidateOptionsMenu */
					public void run() {
						Log.d(TAG, "connectionStatusChanged: " + isConnected + "/" + willReconnect);
						setConnectingStatus(!isConnected && willReconnect);
						setSupportProgressBarIndeterminateVisibility(false);
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
		return getPackedItemRow(ExpandableListView.getPackedPositionForGroup(groupId),
				RosterConstants.GROUP);
	}

	public void restoreGroupsExpanded() {
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this);
		for (int count = 0; count < getExpandableListAdapter().getGroupCount(); count++) {
			String name = getGroupName(count);
			if (!mGroupsExpanded.containsKey(name))
				mGroupsExpanded.put(name, prefs.getBoolean("expanded_" + name, true));
			Log.d(TAG, "restoreGroupsExpanded: " + name + ": " + mGroupsExpanded.get(name));
			if (mGroupsExpanded.get(name))
				getExpandableListView().expandGroup(count);
			else
				getExpandableListView().collapseGroup(count);
		}
	}

	private void showFirstStartUpDialogIfPrefsEmpty() {
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this);
		String configuredJabberID = prefs
				.getString(PreferenceConstants.JID, "");

		Log.i(TAG,
				"called showFirstStartUpDialogIfPrefsEmpty, string from pref was:"
						+ configuredJabberID);
		if (configuredJabberID.length() < 3) {
			// load preference defaults
			PreferenceManager.setDefaultValues(this, R.layout.mainprefs, false);
			PreferenceManager.setDefaultValues(this, R.layout.accountprefs, false);

			// show welcome dialog
			new FirstStartDialog(this, serviceAdapter).show();
		}
	}

	private void getPreferences(SharedPreferences prefs) {
		showOffline = prefs.getBoolean(PreferenceConstants.SHOW_OFFLINE, true);

		setStatus(StatusMode.fromString(prefs.getString(
				PreferenceConstants.STATUS_MODE, StatusMode.available.name())),
				prefs.getString(PreferenceConstants.STATUS_MESSAGE, ""));
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
			RosterConstants.STATUS_MODE + " != " + StatusMode.offline.ordinal();
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
		return list;
	}

	public List<String[]> getRosterContacts() {
		// we want all, online and offline
		List<String[]> list = new ArrayList<String[]>();
		Cursor cursor = getContentResolver().query(RosterProvider.CONTENT_URI, ROSTER_QUERY,
					null, null, RosterConstants.ALIAS);
		int JIDIdx = cursor.getColumnIndex(RosterConstants.JID);
		int aliasIdx = cursor.getColumnIndex(RosterConstants.ALIAS);
		cursor.moveToFirst();
		while (!cursor.isAfterLast()) {
			String jid = cursor.getString(JIDIdx);
			String alias = cursor.getString(aliasIdx);
			if ((alias == null) || (alias.length() == 0)) alias = jid;
			list.add(new String[] { jid, alias });
			cursor.moveToNext();
		}
		cursor.close();
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
			if (!showOffline)
				selectWhere = OFFLINE_EXCLUSION;
			Cursor cursor = getContentResolver().query(RosterProvider.GROUPS_URI,
					GROUPS_QUERY_COUNTED, selectWhere, null, RosterConstants.GROUP);
			Cursor oldCursor = getCursor();
			changeCursor(cursor);
			stopManagingCursor(oldCursor);
		}

		@Override
		protected Cursor getChildrenCursor(Cursor groupCursor) {
			// Given the group, we return a cursor for all the children within that group
			int idx = groupCursor.getColumnIndex(RosterConstants.GROUP);
			String groupname = groupCursor.getString(idx);

			String selectWhere = RosterConstants.GROUP + " = ?";
			if (!showOffline)
				selectWhere += " AND " + OFFLINE_EXCLUSION;
			return getContentResolver().query(RosterProvider.CONTENT_URI, ROSTER_QUERY,
				selectWhere, new String[] { groupname }, null);
		}

		@Override
		protected void bindGroupView(View view, Context context, Cursor cursor, boolean isExpanded) {
			super.bindGroupView(view, context, cursor, isExpanded);
			if (cursor.getString(cursor.getColumnIndexOrThrow(RosterConstants.GROUP)).length() == 0) {
				TextView groupname = (TextView)view.findViewById(R.id.groupname);
				groupname.setText(R.string.default_group);
			}
		}

		@Override
		protected void bindChildView(View view, Context context, Cursor cursor, boolean isLastChild) {
			super.bindChildView(view, context, cursor, isLastChild);
			TextView statusmsg = (TextView)view.findViewById(R.id.roster_statusmsg);
			boolean hasStatus = statusmsg.getText() != null && statusmsg.getText().length() > 0;
			statusmsg.setVisibility(hasStatus ? View.VISIBLE : View.GONE);

			int JIDIdx = cursor.getColumnIndex(RosterConstants.JID);
			String selection = ChatConstants.JID + " = '" + cursor.getString(JIDIdx) + "' AND " +
					ChatConstants.DIRECTION + " = " + ChatConstants.INCOMING + " AND " +
					ChatConstants.DELIVERY_STATUS + " = " + ChatConstants.DS_NEW;
			Cursor msgcursor = getContentResolver().query(ChatProvider.CONTENT_URI,
					new String[] { "count(" + ChatConstants.PACKET_ID + ")" },
					selection, null, null);
			msgcursor.moveToFirst();
			TextView unreadmsg = (TextView)view.findViewById(R.id.roster_unreadmsg_cnt);
			unreadmsg.setText(msgcursor.getString(0));
			unreadmsg.setVisibility(msgcursor.getInt(0) > 0 ? View.VISIBLE : View.GONE);
			unreadmsg.bringToFront();
			msgcursor.close();
		}

		 protected void setViewImage(ImageView v, String value) {
			int presenceMode = Integer.parseInt(value);
			v.setImageResource(getIconForPresenceMode(presenceMode));
		 }

		private int getIconForPresenceMode(int presenceMode) {
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

	private class ChatObserver extends ContentObserver {
		public ChatObserver() {
			super(mainHandler);
		}
		public void onChange(boolean selfChange) {
			updateRoster();
		}
	}
}
