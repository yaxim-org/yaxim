package de.hdmstuttgart.yaxim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.ExpandableListContextMenuInfo;
import de.hdmstuttgart.yaxim.IXMPPRosterCallback.Stub;
import de.hdmstuttgart.yaxim.data.RosterItem;
import de.hdmstuttgart.yaxim.dialogs.AboutDialog;
import de.hdmstuttgart.yaxim.dialogs.AddRosterItemDialog;
import de.hdmstuttgart.yaxim.dialogs.ChangeStatusDialog;
import de.hdmstuttgart.yaxim.dialogs.FirstStartDialog;
import de.hdmstuttgart.yaxim.dialogs.MoveRosterItemToGroupDialog;
import de.hdmstuttgart.yaxim.dialogs.RemoveRosterItemDialog;
import de.hdmstuttgart.yaxim.dialogs.RenameRosterGroupDialog;
import de.hdmstuttgart.yaxim.dialogs.RenameRosterItemDialog;
import de.hdmstuttgart.yaxim.preferences.AccountPrefs;
import de.hdmstuttgart.yaxim.preferences.MainPrefs;
import de.hdmstuttgart.yaxim.service.IXMPPRosterService;
import de.hdmstuttgart.yaxim.service.XMPPService;
import de.hdmstuttgart.yaxim.util.AdapterConstants;
import de.hdmstuttgart.yaxim.util.ConnectionState;
import de.hdmstuttgart.yaxim.util.ExpandableRosterAdapter;
import de.hdmstuttgart.yaxim.util.PreferenceConstants;
import de.hdmstuttgart.yaxim.util.StatusMode;

public class MainWindow extends GenericExpandableListActivity {

	private static final int MENU_CONNECT = Menu.FIRST + 1;
	private static final int MENU_ADD_FRIEND = Menu.FIRST + 2;
	private static final int MENU_SHOW_HIDE = Menu.FIRST + 3;
	private static final int MENU_STATUS = Menu.FIRST + 4;
	private static final int MENU_EXIT = Menu.FIRST + 5;
	private static final int MENU_SETTINGS = Menu.FIRST + 6;
	private static final int MENU_ACC_SET = Menu.FIRST + 7;
	private static final int MENU_ABOUT = Menu.FIRST + 8;

	private static final String TAG = "MainWindow";
	private static final int DIALOG_CONNECTING = 1;
	
	private final List<ArrayList<HashMap<String, RosterItem>>> rosterEntryList = new ArrayList<ArrayList<HashMap<String, RosterItem>>>();
	private final List<HashMap<String, String>> rosterGroupList = new ArrayList<HashMap<String, String>>();
	private Handler mainHandler = new Handler();
	
	private Intent xmppServiceIntent;
	private ServiceConnection xmppServiceConnection;
	private XMPPRosterServiceAdapter serviceAdapter;
	private Stub rosterCallback;
	private ExpandableRosterAdapter rosterListAdapter;
	private ProgressDialog progressDialog;
	private boolean showOffline;
	private boolean isConnected;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		getPreferences(PreferenceManager.getDefaultSharedPreferences(this));
		showFirstStartUpDialogIfPrefsEmpty();
		registerXMPPService();
		createUICallback();
		setContentView(R.layout.main);
		registerForContextMenu(getExpandableListView());
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
		bindXMPPService();
	}

	private void createRosterIfConnected() {
		if ((serviceAdapter != null) && (serviceAdapter.isAuthenticated())) {
			createRoster();
		}
	}

	private void setIsConnected() {
		if (serviceAdapter != null)
			isConnected = serviceAdapter.isAuthenticated();
		else
			isConnected = false;
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

		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.roster_contextmenu, menu);

		int groupPosition = ExpandableListView
				.getPackedPositionGroup(info.packedPosition);
		String groupName = rosterGroupList.get(groupPosition).get(
				AdapterConstants.GROUP_NAME[0]);

		if (isChild) {
			menu.setGroupVisible(R.id.roster_contacts_group, true);
			menu.setGroupVisible(R.id.roster_groups_group, false);
			menu.setHeaderTitle(R.string.MenuContext_ContactHeaderTitle);
		} else {
			if (groupName.equals(AdapterConstants.EMPTY_GROUP)) {
				menu.setGroupVisible(R.id.roster_contacts_group, false);
				menu.setGroupVisible(R.id.roster_groups_group, false);
				menu.setHeaderTitle(R.string.MenuContext_HeaderTitleDisabled);
			} else {
				menu.setGroupVisible(R.id.roster_contacts_group, false);
				menu.setGroupVisible(R.id.roster_groups_group, true);
				menu.setHeaderTitle(R.string.MenuContext_GroupsHeaderTitle);
			}
		}
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

			String user = rosterEntryList.get(groupPosition).get(childPosition)
					.get(AdapterConstants.CONTACT_ID).jabberID;

			int itemID = item.getItemId();

			switch (itemID) {
			case R.id.roster_openchat:
				startChatActivity(user);
				return true;
				
			case R.id.roster_delete_contact:
				RemoveRosterItemDialog deleteRosterItem = new RemoveRosterItemDialog(
						this, serviceAdapter, user);
				deleteRosterItem.show();
				return true;
				
			case R.id.roster_rename_contact:
				new RenameRosterItemDialog(this, serviceAdapter, user).show();
				return true;
				
			case R.id.roster_editContactGroup:
				new MoveRosterItemToGroupDialog(this, serviceAdapter, user)
						.show();
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
			case R.id.roster_rename_group:
				new RenameRosterGroupDialog(this, serviceAdapter, seletedGroup)
						.show();
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

	private void startChatActivity(String user) {
		Intent chatIntent = new Intent(this,
				de.hdmstuttgart.yaxim.chat.ChatWindow.class);
		Uri userNameUri = Uri.parse(user);
		chatIntent.setData(userNameUri);
		startActivity(chatIntent);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		populateMainMenu(menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		return applyMainMenuChoice(item);
	}

	private void populateMainMenu(Menu menu) {
		menu.add(Menu.NONE, MENU_CONNECT, Menu.NONE,
				(getConnectDisconnectText())).setIcon(
				getConnectDisconnectIcon());
		menu.add(Menu.NONE, MENU_ADD_FRIEND, Menu.NONE,
				(getString(R.string.Menu_addFriend))).setIcon(
				android.R.drawable.ic_menu_add);
		menu.add(Menu.NONE, MENU_SHOW_HIDE, Menu.NONE, getShowHideMenuText())
				.setIcon(getShowHideMenuIcon());
		menu.add(Menu.NONE, MENU_STATUS, Menu.NONE,
				(getString(R.string.Menu_Status))).setIcon(
				android.R.drawable.ic_menu_myplaces);
		menu.add(Menu.NONE, MENU_EXIT, Menu.NONE,
				(getString(R.string.Global_Exit))).setIcon(
				android.R.drawable.ic_menu_close_clear_cancel);
		menu.add(Menu.NONE, MENU_SETTINGS, Menu.NONE,
				(getString(R.string.Menu_Settings))).setIcon(
				android.R.drawable.ic_menu_preferences);
		menu.add(Menu.NONE, MENU_ACC_SET, Menu.NONE,
				(getString(R.string.Menu_AccSettings))).setIcon(
				android.R.drawable.ic_menu_manage);
		menu.add(Menu.NONE, MENU_ABOUT, Menu.NONE,
				(getString(R.string.Menu_about))).setIcon(R.drawable.about);
	}

	private int getShowHideMenuIcon() {
		return showOffline ? R.drawable.ic_menu_block
				: android.R.drawable.ic_menu_view;
	}

	private String getShowHideMenuText() {
		return showOffline ? getString(R.string.Menu_HideOff)
				: getString(R.string.Menu_ShowOff);
	}

	private boolean applyMainMenuChoice(MenuItem item) {

		int itemID = item.getItemId();

		switch (itemID) {
		case MENU_CONNECT:
			toggleConnection(item);
			return true;

		case MENU_ADD_FRIEND:
			if (serviceAdapter.isAuthenticated()) {
				new AddRosterItemDialog(this, serviceAdapter).show();
			} else {
				showToastNotification(R.string.Global_authenticate_first);
			}
			return true;

		case MENU_SHOW_HIDE:
			showOffline = !showOffline;
			updateRoster();
			item.setIcon(getShowHideMenuIcon());
			item.setTitle(getShowHideMenuText());
			return true;

		case MENU_STATUS:
			if (serviceAdapter.isAuthenticated()) {
				new ChangeStatusDialog(this, serviceAdapter).show();
			} else {
				showToastNotification(R.string.Global_authenticate_first);
			}
			return true;

		case MENU_EXIT:
			stopService(xmppServiceIntent);
			finish();
			return true;

		case MENU_SETTINGS:
			startActivity(new Intent(this, MainPrefs.class));
			return true;

		case MENU_ACC_SET:
			startActivity(new Intent(this, AccountPrefs.class));
			return true;

		case MENU_ABOUT:
			new AboutDialog(this, serviceAdapter).show();
			return true;

		}
		
		return false;

	}

	@Override
	public boolean onChildClick(ExpandableListView parent, View v,
			int groupPosition, int childPosition, long id) {

		Log.i(TAG, "Called onChildClick()");
		String user = rosterEntryList.get(groupPosition).get(childPosition)
				.get(AdapterConstants.CONTACT_ID).jabberID;
		startChatActivity(user);

		return true;
	}

	private void toggleConnection(MenuItem item) {
		if (serviceAdapter.isAuthenticated()) {
			(new Thread() {
				public void run() {
					serviceAdapter.disconnect();
					stopService(xmppServiceIntent);
				}
			}).start();

			clearRoster();
			isConnected = false;

		} else {
			showDialog(DIALOG_CONNECTING);
			(new Thread() {
				public void run() {
					startService(xmppServiceIntent);
				}
			}).start();
			isConnected = true;
		}

		item.setIcon(getConnectDisconnectIcon());
		item.setTitle(getConnectDisconnectText());
	}

	private int getConnectDisconnectIcon() {
		if (isConnected) {
			return R.drawable.yaxim_menu_disconnect;
		}
		return R.drawable.yaxim_menu_connect;
	}

	private String getConnectDisconnectText() {
		if (isConnected) {
			return getString(R.string.Menu_disconnect);
		}
		return getString(R.string.Menu_connect);
	}

	private void clearRoster() {
		rosterEntryList.clear();
		rosterGroupList.clear();
		if (rosterListAdapter != null)
			rosterListAdapter.notifyDataSetChanged();
	}

	private void registerXMPPService() {
		Log.i(TAG, "called startXMPPService()");
		xmppServiceIntent = new Intent(this, XMPPService.class);
		xmppServiceIntent.setAction("de.hdmstuttgart.yaxim.XMPPSERVICE");

		xmppServiceConnection = new ServiceConnection() {

			public void onServiceConnected(ComponentName name, IBinder service) {
				Log.i(TAG, "called onServiceConnected()");
				serviceAdapter = new XMPPRosterServiceAdapter(
						IXMPPRosterService.Stub.asInterface(service));
				serviceAdapter.registerUICallback(rosterCallback);
				createRosterIfConnected();
				setIsConnected();
				Log.i(TAG, "getConnectionState(): "
						+ serviceAdapter.getConnectionState());
				if (serviceAdapter.getConnectionState() == ConnectionState.CONNECTING)
					showDialog(DIALOG_CONNECTING);
				else if (progressDialog != null && progressDialog.isShowing())
					dismissDialog(DIALOG_CONNECTING);
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
//		createRosterEntryList();
//		createRosterGroupList();

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
			if (showOffline || (rosterEntryItem.getStatusMode() != StatusMode.offline)) {
				rosterItemList.add(rosterEntry);
			}
		}
		return rosterItemList;
	}

	private void createRosterGroupList() {
		for (String rosterGroupName : serviceAdapter.getRosterGroups()) {
			HashMap<String, String> tmpGroupMap = new HashMap<String, String>();
			tmpGroupMap.put(AdapterConstants.GROUP_NAME[0], rosterGroupName);
			rosterGroupList.add(tmpGroupMap);
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
						isConnected = true;
						if (progressDialog.isShowing())
							dismissDialog(DIALOG_CONNECTING);
					}
				});
			}

			public void connectionFailed(final boolean willReconnect)
					throws RemoteException {
				mainHandler.post(new Runnable() {
					public void run() {
						showToastNotification(R.string.toast_connectfail_message);
						isConnected = false;
						if (willReconnect)
							showDialog(DIALOG_CONNECTING);
						else if (progressDialog.isShowing())
							dismissDialog(DIALOG_CONNECTING);
					}
				});
			}
		};
	}

	public void expandGroups() {
		for (int count = 0; count < getExpandableListAdapter().getGroupCount(); count++) {
			getExpandableListView().expandGroup(count);
		}
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case DIALOG_CONNECTING:
			progressDialog = new ProgressDialog(this);
			progressDialog.setIcon(android.R.drawable.ic_dialog_info);
			progressDialog.setTitle(R.string.dialog_connect_title);
			progressDialog.setMessage(getText(R.string.dialog_connect_message));
			return progressDialog;
		default:
			return super.onCreateDialog(id);
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
	}
}
