package org.yaxim.androidclient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.yaxim.androidclient.data.ChatProvider;
import org.yaxim.androidclient.data.RosterProvider;
import org.yaxim.androidclient.data.YaximConfiguration;
import org.yaxim.androidclient.dialogs.AddRosterItemDialog;
import org.yaxim.androidclient.dialogs.ChangeStatusDialog;
import org.yaxim.androidclient.dialogs.FirstStartDialog;
import org.yaxim.androidclient.dialogs.GroupNameView;
import org.yaxim.androidclient.preferences.AccountPrefs;
import org.yaxim.androidclient.preferences.MainPrefs;
import org.yaxim.androidclient.service.XMPPService;
import org.yaxim.androidclient.util.AdapterConstants;
import org.yaxim.androidclient.util.ConnectionState;
import org.yaxim.androidclient.util.PreferenceConstants;
import org.yaxim.androidclient.util.StatusMode;

import android.app.AlertDialog;
import android.app.ExpandableListActivity;
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
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ImageButton;
import android.widget.ImageView;
import org.yaxim.androidclient.util.SimpleCursorTreeAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ExpandableListView.ExpandableListContextMenuInfo;
import org.yaxim.androidclient.IXMPPRosterCallback;
import org.yaxim.androidclient.R;
import org.yaxim.androidclient.IXMPPRosterCallback.Stub;
import org.yaxim.androidclient.service.IXMPPRosterService;

import com.markupartist.android.widget.ActionBar;
import com.markupartist.android.widget.ActionBar.Action;

import com.nullwire.trace.ExceptionHandler;

public class MainWindow extends ExpandableListActivity {

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

	private ActionBar actionBar;
	private ChangeStatusAction changeStatusAction;
	private ToggleOfflineContactsAction toggleOfflineContactsAction;

	private ContentObserver mRosterObserver = new RosterObserver();
	private HashMap<String, Boolean> mGroupsExpanded = new HashMap<String, Boolean>();


	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mConfig = new YaximConfiguration(PreferenceManager
				.getDefaultSharedPreferences(this));
		registerCrashReporter();

		showFirstStartUpDialogIfPrefsEmpty();
		getContentResolver().registerContentObserver(RosterProvider.GROUPS_URI,
				true, mRosterObserver);
		registerXMPPService();
		createUICallback();
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setupContenView();
		registerListAdapter();

		actionBar = (ActionBar) findViewById(R.id.actionbar);
		actionBar.setTitle(R.string.app_name);
		actionBar.setSubTitle(mStatusMessage);

		toggleOfflineContactsAction = new ToggleOfflineContactsAction();
		actionBar.addAction(toggleOfflineContactsAction);

		changeStatusAction = new ChangeStatusAction();
		actionBar.setHomeAction(changeStatusAction);
	}
	@Override
	public void onDestroy() {
		super.onDestroy();
		getContentResolver().unregisterContentObserver(mRosterObserver);
	}

	private abstract class AbstractAction implements Action {

		/** Causes the view to reload the {@link Drawable}. */
		void invalidate() {
			ImageButton imageButton = (ImageButton) actionBar
					.findViewWithTag(this);
			imageButton.setImageResource(getDrawable());
		}
	}

	private class ChangeStatusAction extends AbstractAction {
		public void performAction(View view) {
			new ChangeStatusDialog(MainWindow.this).show();
		}
		
		public int getDrawable() {

			boolean showOffline = !isConnected() || isConnecting()
					|| getStatusMode() == null;

			if (showOffline) {
				return StatusMode.offline.getDrawableId();
			}

			return getStatusMode().getDrawableId();
		}
	}

	private class ToggleOfflineContactsAction extends AbstractAction {

		public int getDrawable() {
			if (showOffline) {
				return R.drawable.ic_action_online_friends;
			}

			return R.drawable.ic_action_all_friends;
		}

		public void performAction(View view) {
			setOfflinceContactsVisibility(!showOffline);
			updateRoster();
		}
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
		updateRoster();
		bindXMPPService();

		YaximApplication.getApp(this).mMTM.bindDisplayActivity(this);
		// Causes the toggle button to show correct state on application start
		toggleOfflineContactsAction.invalidate();

		// handle SEND action
		handleSendIntent();
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
				isChild ? RosterProvider.RosterConstants.ALIAS
					: RosterProvider.RosterConstants.GROUP);

		// display contact menu for contacts
		menu.setGroupVisible(R.id.roster_contextmenu_contact_menu, isChild);
		// display group menu for non-standard group
		menu.setGroupVisible(R.id.roster_contextmenu_group_menu, !isChild &&
				!menuName.equals(AdapterConstants.EMPTY_GROUP));

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
		final EditText input = new EditText(this);
		input.setTransformationMethod(android.text.method.SingleLineTransformationMethod.getInstance());
		input.setText(text);
		new AlertDialog.Builder(this)
			.setTitle(titleId)
			.setMessage(message)
			.setView(input)
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

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		return applyMenuContextChoice(item);
	}

	private boolean applyMenuContextChoice(MenuItem item) {

		ExpandableListContextMenuInfo contextMenuInfo = (ExpandableListContextMenuInfo) item
				.getMenuInfo();
		long packedPosition = contextMenuInfo.packedPosition;

		if (isChild(packedPosition)) {

			String userJid = getPackedItemRow(packedPosition,
					RosterProvider.RosterConstants.JID);
			String userName = getPackedItemRow(packedPosition,
					RosterProvider.RosterConstants.ALIAS);
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
			String seletedGroup = getPackedItemRow(packedPosition,
					RosterProvider.RosterConstants.GROUP);
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

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		setMenuItem(menu, R.id.menu_connect, getConnectDisconnectIcon(),
				getConnectDisconnectText());
		setMenuItem(menu, R.id.menu_show_hide, getShowHideMenuIcon(),
				getShowHideMenuText());
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		return applyMainMenuChoice(item);
	}

	private int getShowHideMenuIcon() {
		return showOffline ? R.drawable.ic_menu_online_friends
				: R.drawable.ic_menu_all_friends;
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

	public static String getStatusTitle(Context context, String status, String statusMessage) {
		status = context.getString(StatusMode.fromString(status).getTextId());

		if (statusMessage.length() > 0) {
			status = status + " (" + statusMessage + ")";
		}

		return status;
	}

	public void setAndSaveStatus(StatusMode statusMode, String message) {
		setStatus(statusMode, message);


		SharedPreferences.Editor prefedit = PreferenceManager
				.getDefaultSharedPreferences(this).edit();
		// do not save "offline" to prefs, or else!
		if (statusMode != StatusMode.offline)
			prefedit.putString(PreferenceConstants.STATUS_MODE, statusMode.name());
		prefedit.putString(PreferenceConstants.STATUS_MESSAGE, message);
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
		changeStatusAction.invalidate();

		if (mStatusMessage.equals("")) {
			actionBar.setSubTitle(null);
		} else {
			actionBar.setSubTitle(mStatusMessage);
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
			.create().show();
	}

	private boolean applyMainMenuChoice(MenuItem item) {

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

		case R.id.menu_acc_set:
			startActivity(new Intent(this, AccountPrefs.class));
			return true;

		case R.id.menu_about:
			aboutDialog();
			return true;

		}

		return false;

	}

	/** Sets if all contacts are shown in the roster or online contacts only. */
	private void setOfflinceContactsVisibility(boolean showOffline) {
		this.showOffline = showOffline;
		toggleOfflineContactsAction.invalidate();

		PreferenceManager.getDefaultSharedPreferences(this).edit().
			putBoolean(PreferenceConstants.SHOW_OFFLINE, showOffline).commit();
	}

	@Override
	public boolean onChildClick(ExpandableListView parent, View v,
			int groupPosition, int childPosition, long id) {

		long packedPosition = ExpandableListView.getPackedPositionForChild(groupPosition, childPosition);
		String userJid = getPackedItemRow(packedPosition,
				RosterProvider.RosterConstants.JID);
		String userName = getPackedItemRow(packedPosition,
				RosterProvider.RosterConstants.ALIAS);
		startChatActivity(userJid, userName, null);

		return true;
	}

	private void setConnectingStatus(boolean isConnecting) {
		_setProgressBarIndeterminateVisibility(isConnecting);
		changeStatusAction.invalidate();

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
	
	/**
	 * Sets the visibility of the indeterminate progress bar in the action bar.
	 * Name starts with an underscore, becuase super
	 * {@link #setProgressBarIndeterminateVisibility(boolean)} is final.
	 */
	private void _setProgressBarIndeterminateVisibility(boolean visibility) {
		if (visibility) {
			actionBar.setProgressBarVisibility(View.VISIBLE);
		} else {
			actionBar.setProgressBarVisibility(View.GONE);
		}
	}

	public void startConnection() {
		setConnectingStatus(true);
		(new Thread() {
			public void run() {
				startService(xmppServiceIntent);
			}
		}).start();
	}

	// this function changes the prefs to keep the connection
	// according to the requested state
	private void toggleConnection() {
		boolean oldState = isConnected() || isConnecting();
		PreferenceManager.getDefaultSharedPreferences(this).edit().
			putBoolean(PreferenceConstants.CONN_STARTUP, !oldState).commit();
		if (oldState) {
			setConnectingStatus(false);
			(new Thread() {
				public void run() {
					serviceAdapter.disconnect();
					stopService(xmppServiceIntent);
				}
			}).start();

		} else
			startConnection();
	}

	private int getConnectDisconnectIcon() {
		if (isConnected() || isConnecting()) {
			return R.drawable.yaxim_menu_disconnect;
		}
		return R.drawable.yaxim_menu_connect;
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

			public void onServiceConnected(ComponentName name, IBinder service) {
				Log.i(TAG, "called onServiceConnected()");
				serviceAdapter = new XMPPRosterServiceAdapter(
						IXMPPRosterService.Stub.asInterface(service));
				serviceAdapter.registerUICallback(rosterCallback);
				Log.i(TAG, "getConnectionState(): "
						+ serviceAdapter.getConnectionState());
				setConnectingStatus(serviceAdapter.getConnectionState() == ConnectionState.CONNECTING);
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
					public void run() {
						Log.d(TAG, "connectionStatusChanged: " + isConnected + "/" + willReconnect);
						setConnectingStatus(!isConnected && willReconnect);
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
				RosterProvider.RosterConstants.GROUP);
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

	private static final String[] GROUPS_QUERY = new String[] {
		RosterProvider.GroupsConstants._ID,
		RosterProvider.GroupsConstants.GROUP,
	};
	private static final String[] GROUPS_FROM = new String[] {
		RosterProvider.GroupsConstants.GROUP
	};
	private static final int[] GROUPS_TO = new int[] {
		R.id.groupname
	};
	private static final String[] ROSTER_QUERY = new String[] {
		RosterProvider.RosterConstants._ID,
		RosterProvider.RosterConstants.JID,
		RosterProvider.RosterConstants.ALIAS,
		RosterProvider.RosterConstants.STATUS_MODE,
		RosterProvider.RosterConstants.STATUS_MESSAGE,
	};

	public List<String> getRosterGroups() {
		// we want all, online and offline
		List<String> list = new ArrayList<String>();
		Cursor cursor = getContentResolver().query(RosterProvider.GROUPS_URI, GROUPS_QUERY,
					null, null, "roster_group");
		int idx = cursor.getColumnIndex(RosterProvider.GroupsConstants.GROUP);
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
					null, null, RosterProvider.RosterConstants.ALIAS);
		int JIDIdx = cursor.getColumnIndex(RosterProvider.RosterConstants.JID);
		int aliasIdx = cursor.getColumnIndex(RosterProvider.RosterConstants.ALIAS);
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
					new String[] { RosterProvider.RosterConstants.ALIAS,
						RosterProvider.RosterConstants.STATUS_MESSAGE,
						RosterProvider.RosterConstants.STATUS_MODE },
					new int[] { R.id.roster_screenname, R.id.roster_statusmsg,
						R.id.roster_icon });
		}

		public void requery() {
			String selectWhere = null;
			/* show all groups, including offline
			if (!showOffline)
				selectWhere = "status_mode > 0";
			*/
			Cursor cursor = getContentResolver().query(RosterProvider.GROUPS_URI, GROUPS_QUERY,
					selectWhere, null, "roster_group");
			Cursor oldCursor = getCursor();
			changeCursor(cursor);
			stopManagingCursor(oldCursor);
		}

		@Override
		protected Cursor getChildrenCursor(Cursor groupCursor) {
			// Given the group, we return a cursor for all the children within that group 
			String groupname = groupCursor.getString(1);

			String selectWhere = "roster_group = ?";
			if (!showOffline)
				selectWhere += "AND status_mode > 0";
			return getContentResolver().query(RosterProvider.CONTENT_URI, ROSTER_QUERY,
				selectWhere, new String[] { groupname }, null);
		}

		@Override
		protected void bindChildView(View view, Context context, Cursor cursor, boolean isLastChild) {
			super.bindChildView(view, context, cursor, isLastChild);
			TextView statusmsg = (TextView)view.findViewById(R.id.roster_statusmsg);
			boolean hasStatus = statusmsg.getText() != null && statusmsg.getText().length() > 0;
			statusmsg.setVisibility(hasStatus ? View.VISIBLE : View.GONE);
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
}
