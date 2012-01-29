package org.yaxim.androidclient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.yaxim.androidclient.data.ChatProvider;
import org.yaxim.androidclient.data.RosterItem;
import org.yaxim.androidclient.dialogs.AddRosterItemDialog;
import org.yaxim.androidclient.dialogs.ChangeStatusDialog;
import org.yaxim.androidclient.dialogs.FirstStartDialog;
import org.yaxim.androidclient.dialogs.GroupNameView;
import org.yaxim.androidclient.preferences.AccountPrefs;
import org.yaxim.androidclient.preferences.MainPrefs;
import org.yaxim.androidclient.service.XMPPService;
import org.yaxim.androidclient.util.AdapterConstants;
import org.yaxim.androidclient.util.ConnectionState;
import org.yaxim.androidclient.util.ExpandableRosterAdapter;
import org.yaxim.androidclient.util.PreferenceConstants;
import org.yaxim.androidclient.util.StatusMode;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
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
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ExpandableListView.ExpandableListContextMenuInfo;
import org.yaxim.androidclient.IXMPPRosterCallback;
import org.yaxim.androidclient.R;
import org.yaxim.androidclient.IXMPPRosterCallback.Stub;
import org.yaxim.androidclient.service.IXMPPRosterService;

import com.markupartist.android.widget.ActionBar;
import com.markupartist.android.widget.ActionBar.Action;

public class MainWindow extends GenericExpandableListActivity {

	private static final String TAG = "MainWindow";
	
	private static final int DIALOG_CHANGE_STATUS_ID = 0;

	private final List<ArrayList<HashMap<String, RosterItem>>> rosterEntryList = new ArrayList<ArrayList<HashMap<String, RosterItem>>>();
	private final List<HashMap<String, String>> rosterGroupList = new ArrayList<HashMap<String, String>>();
	private Handler mainHandler = new Handler();

	private Intent xmppServiceIntent;
	private ServiceConnection xmppServiceConnection;
	private XMPPRosterServiceAdapter serviceAdapter;
	private Stub rosterCallback;
	private ExpandableRosterAdapter rosterListAdapter;
	private TextView mConnectingText;
	private boolean showOffline;

	private String mStatusMessage;
	private StatusMode mStatusMode;

	private ActionBar actionBar;
	private ChangeStatusAction changeStatusAction;
	private ToggleOfflineContactsAction toggleOfflineContactsAction;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		showFirstStartUpDialogIfPrefsEmpty();
		registerXMPPService();
		createUICallback();
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setupContenView();

		actionBar = (ActionBar) findViewById(R.id.actionbar);
		actionBar.setTitle(R.string.app_name);
		actionBar.setSubTitle(mStatusMessage);

		toggleOfflineContactsAction = new ToggleOfflineContactsAction();
		actionBar.addAction(toggleOfflineContactsAction);

		changeStatusAction = new ChangeStatusAction();
		actionBar.setHomeAction(changeStatusAction);
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
			if (serviceAdapter.isAuthenticated()) {
				showDialog(DIALOG_CHANGE_STATUS_ID);
			} else {
				showToastNotification(R.string.Global_authenticate_first);
			}
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

	void setupContenView() {
		setContentView(R.layout.main);
		mConnectingText = (TextView)findViewById(R.id.error_view);
		registerForContextMenu(getExpandableListView());
		getExpandableListView().requestFocus();
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (serviceAdapter != null)
			serviceAdapter.unregisterUICallback(rosterCallback);
		unbindXMPPService();
	}

	@Override
	protected void onResume() {
		super.onResume();
		getPreferences(PreferenceManager.getDefaultSharedPreferences(this));
		bindXMPPService();

		// Causes the toggle button to show correct state on application start
		toggleOfflineContactsAction.invalidate();
	}


	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		Log.d(TAG, "onConfigurationChanged");
		getExpandableListView().requestFocus();
	}

	private void createRosterIfConnected() {
		if ((serviceAdapter != null) && (serviceAdapter.isAuthenticated())) {
			createRoster();
		}
	}

	private boolean isConnected() {
		return serviceAdapter != null && serviceAdapter.isAuthenticated();
	}
	private boolean isConnecting() {
		return serviceAdapter != null && serviceAdapter.getConnectionState() == ConnectionState.CONNECTING;
	}

	public void updateRoster() {
		if (serviceAdapter.isAuthenticated()
				&& getExpandableListAdapter() != null) {
			rosterEntryList.clear();
			createRosterEntryList();
			rosterGroupList.clear();
			createRosterGroupList();
			rosterListAdapter.notifyDataSetChanged();
		}
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

		int groupPosition = ExpandableListView
				.getPackedPositionGroup(info.packedPosition);
		String menuName = rosterGroupList.get(groupPosition).get(
				AdapterConstants.GROUP_NAME[0]);

		// display contact menu for contacts
		menu.setGroupVisible(R.id.roster_contextmenu_contact_menu, isChild);
		// display group menu for non-standard group
		menu.setGroupVisible(R.id.roster_contextmenu_group_menu, !isChild &&
				!menuName.equals(AdapterConstants.EMPTY_GROUP));
		if (isChild) {
			int childPosition = ExpandableListView
					.getPackedPositionChild(packedPosition);
			menuName = rosterEntryList.get(groupPosition).get(childPosition)
				.get(AdapterConstants.CONTACT_ID).screenName;
		}
		menu.setHeaderTitle(getString(R.string.roster_contextmenu_title, menuName));
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case DIALOG_CHANGE_STATUS_ID:
			return new ChangeStatusDialog(this);
		}

		return null;
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
		gv.setGroupList(serviceAdapter.getRosterGroups());
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
		int groupPosition = ExpandableListView
				.getPackedPositionGroup(contextMenuInfo.packedPosition);
		int childPosition = ExpandableListView
				.getPackedPositionChild(contextMenuInfo.packedPosition);

		if (isChild(contextMenuInfo.packedPosition)) {

			String userJid = rosterEntryList.get(groupPosition).get(childPosition)
					.get(AdapterConstants.CONTACT_ID).jabberID;
			String userName = rosterEntryList.get(groupPosition).get(childPosition)
				.get(AdapterConstants.CONTACT_ID).screenName;

			int itemID = item.getItemId();

			switch (itemID) {
			case R.id.roster_contextmenu_contact_open_chat:
				startChatActivity(userJid, userName);
				return true;

			case R.id.roster_contextmenu_contact_delmsg:
				removeChatHistoryDialog(userJid, userName);
				return true;

			case R.id.roster_contextmenu_contact_delete:
				removeRosterItemDialog(userJid, userName);
				return true;

			case R.id.roster_contextmenu_contact_rename:
				renameRosterItemDialog(userJid, userName);
				return true;

			case R.id.roster_contextmenu_contact_request_auth:
				serviceAdapter.requestAuthorizationForRosterItem(userJid);
				return true;

			case R.id.roster_contextmenu_contact_change_group:
				moveRosterItemToGroupDialog(userJid);
				return true;

			case R.id.roster_exit:
				closeContextMenu();
				return true;
			}
		} else {

			int itemID = item.getItemId();
			String seletedGroup = rosterGroupList.get(groupPosition).get(
					AdapterConstants.GROUP_NAME[0]);

			switch (itemID) {
			case R.id.roster_contextmenu_group_rename:
				renameRosterGroupDialog(seletedGroup);
				return true;

			case R.id.roster_exit:
				closeContextMenu();
				return true;
			}
		}
		return false;
	}

	private boolean isChild(long packedPosition) {
		int type = ExpandableListView.getPackedPositionType(packedPosition);
		return (type == ExpandableListView.PACKED_POSITION_TYPE_CHILD);
	}

	private void startChatActivity(String user, String userName) {
		Intent chatIntent = new Intent(this,
				org.yaxim.androidclient.chat.ChatWindow.class);
		Uri userNameUri = Uri.parse(user);
		chatIntent.setData(userNameUri);
		chatIntent.putExtra(org.yaxim.androidclient.chat.ChatWindow.INTENT_EXTRA_USERNAME, userName);
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

		// do not save "offline" to prefs, or else!
		if (statusMode == StatusMode.offline) {
			serviceAdapter.disconnect();
			setConnectingStatus(false);
			stopService(xmppServiceIntent);
			return;
		}

		SharedPreferences.Editor prefedit = PreferenceManager
				.getDefaultSharedPreferences(this).edit();
		prefedit.putString(PreferenceConstants.STATUS_MODE, statusMode.name());
		prefedit.putString(PreferenceConstants.STATUS_MESSAGE, message);
		prefedit.commit();

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
			if (serviceAdapter.isAuthenticated()) {
				showDialog(DIALOG_CHANGE_STATUS_ID);
			} else {
				showToastNotification(R.string.Global_authenticate_first);
			}
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

		Log.i(TAG, "Called onChildClick()");
		String userJid = rosterEntryList.get(groupPosition).get(childPosition)
				.get(AdapterConstants.CONTACT_ID).jabberID;
		String userName = rosterEntryList.get(groupPosition).get(childPosition)
			.get(AdapterConstants.CONTACT_ID).screenName;
		startChatActivity(userJid, userName);

		return true;
	}

	private void setConnectingStatus(boolean isConnecting) {
		_setProgressBarIndeterminateVisibility(isConnecting);
		changeStatusAction.invalidate();

		String lastStatus;

		if (serviceAdapter != null && (lastStatus =
					serviceAdapter.getConnectionStateString()) != null) {
			mConnectingText.setVisibility(View.VISIBLE);
			mConnectingText.setText(lastStatus);
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

	public void toggleConnection() {
		boolean oldState = isConnected() || isConnecting();
		PreferenceManager.getDefaultSharedPreferences(this).edit().
			putBoolean(PreferenceConstants.CONN_STARTUP, !oldState).commit();
		setConnectingStatus(!oldState);
		if (oldState) {
			(new Thread() {
				public void run() {
					serviceAdapter.disconnect();
					stopService(xmppServiceIntent);
				}
			}).start();

		} else {
			(new Thread() {
				public void run() {
					startService(xmppServiceIntent);
				}
			}).start();
		}

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

	private void clearRoster() {
		rosterEntryList.clear();
		rosterGroupList.clear();
		if (rosterListAdapter != null) {
			rosterListAdapter.notifyDataSetChanged();
		}
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
				createRosterIfConnected();
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
		createRosterEntryList();
		createRosterGroupList();

		rosterListAdapter = new ExpandableRosterAdapter(this, rosterGroupList,
				R.layout.maingroup_row, AdapterConstants.GROUP_NAME,
				new int[] { R.id.groupname }, rosterEntryList,
				R.layout.mainchild_row, AdapterConstants.CHILD_DATA_KEYS,
				new int[] { R.id.roster_screenname, R.id.roster_statusmsg });

		setListAdapter(rosterListAdapter);
	}

	private void createRosterEntryList() {
		List<String> rosterGroups = serviceAdapter.getRosterGroups();

		for (String rosterGroup : rosterGroups) {
			ArrayList<HashMap<String, RosterItem>> rosterGroupItems = getRosterGroupItems(rosterGroup);
			if (rosterGroupItems.size() > 0)
				rosterEntryList.add(rosterGroupItems);
		}
	}

	private ArrayList<HashMap<String, RosterItem>> getRosterGroupItems(
			String group) {
		ArrayList<HashMap<String, RosterItem>> rosterItemList = new ArrayList<HashMap<String, RosterItem>>();

		List<RosterItem> rosterItems = serviceAdapter.getGroupItems(group);

		for (RosterItem rosterEntryItem : rosterItems) {
			HashMap<String, RosterItem> rosterEntry = new HashMap<String, RosterItem>();
			rosterEntry.put(AdapterConstants.CONTACT_ID, rosterEntryItem);
			if (showOffline
					|| (rosterEntryItem.getStatusMode() != StatusMode.offline)) {
				rosterItemList.add(rosterEntry);
			}
		}
		return rosterItemList;
	}

	private void createRosterGroupList() {
		for (String rosterGroupName : serviceAdapter.getRosterGroups()) {
			if (getRosterGroupItems(rosterGroupName).size() > 0) {
				HashMap<String, String> tmpGroupMap = new HashMap<String, String>();
				tmpGroupMap.put(AdapterConstants.GROUP_NAME[0], rosterGroupName);
				rosterGroupList.add(tmpGroupMap);
			}
		}
	}

	public void createRoster() {
		Log.i(TAG, "called createRoster()");
		if (serviceAdapter.isAuthenticated()) {
			clearRoster();
			registerListAdapter();
			expandGroups();
		}
	}

	private void createUICallback() {
		rosterCallback = new IXMPPRosterCallback.Stub() {

			public void rosterChanged() throws RemoteException {
				Log.i(TAG, "called rosterChanged()");
				mainHandler.post(new Runnable() {
					public void run() {
						updateRoster();
					}
				});
			}

			public void connectionSuccessful() throws RemoteException {
				mainHandler.post(new Runnable() {

					public void run() {
						createRosterIfConnected();
						getExpandableListView().requestFocus();
						setConnectingStatus(false);
					}
				});
			}

			public void connectionFailed(final boolean willReconnect)
						throws RemoteException {
				mainHandler.post(new Runnable() {
					public void run() {
						showToastNotification(R.string.toast_connectfail_message);
						setConnectingStatus(willReconnect);
					}
				});
			}
		};
	}

	public void expandGroups() {
		Log.d(TAG, "expandGroups(): " + rosterGroupList.size() + " vs " + getExpandableListAdapter().getGroupCount());
		for (int count = 0; count < getExpandableListAdapter().getGroupCount(); count++) {
			try {
				getExpandableListView().expandGroup(count);
			} catch (IndexOutOfBoundsException e) {
				Log.d(TAG, "Oops, why did I try to expand an empty group?");
			}
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
			showFirstStartUpDialog();
		}
	}

	private void showFirstStartUpDialog() {
		new FirstStartDialog(this, serviceAdapter).show();
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
}
