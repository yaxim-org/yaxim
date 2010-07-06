package org.yaxim.androidclient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.yaxim.androidclient.data.RosterItem;
import org.yaxim.androidclient.dialogs.AboutDialog;
import org.yaxim.androidclient.dialogs.AddRosterItemDialog;
import org.yaxim.androidclient.dialogs.ChangeStatusDialog;
import org.yaxim.androidclient.dialogs.FirstStartDialog;
import org.yaxim.androidclient.dialogs.MoveRosterItemToGroupDialog;
import org.yaxim.androidclient.dialogs.RemoveRosterItemDialog;
import org.yaxim.androidclient.dialogs.RenameRosterGroupDialog;
import org.yaxim.androidclient.dialogs.RenameRosterItemDialog;
import org.yaxim.androidclient.preferences.AccountPrefs;
import org.yaxim.androidclient.preferences.MainPrefs;
import org.yaxim.androidclient.service.XMPPService;
import org.yaxim.androidclient.util.AdapterConstants;
import org.yaxim.androidclient.util.ConnectionState;
import org.yaxim.androidclient.util.ExpandableRosterAdapter;
import org.yaxim.androidclient.util.PreferenceConstants;
import org.yaxim.androidclient.util.StatusMode;

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
import android.view.Window;
import android.widget.ExpandableListView;
import android.widget.TextView;
import android.widget.ExpandableListView.ExpandableListContextMenuInfo;
import org.yaxim.androidclient.IXMPPRosterCallback;
import org.yaxim.androidclient.R;
import org.yaxim.androidclient.IXMPPRosterCallback.Stub;
import org.yaxim.androidclient.service.IXMPPRosterService;

public class MainWindow extends GenericExpandableListActivity {

	private static final String TAG = "MainWindow";

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

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		showFirstStartUpDialogIfPrefsEmpty();
		registerXMPPService();
		createUICallback();
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
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

		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.roster_contextmenu, menu);

		int groupPosition = ExpandableListView
				.getPackedPositionGroup(info.packedPosition);
		String groupName = rosterGroupList.get(groupPosition).get(
				AdapterConstants.GROUP_NAME[0]);

		String menuTitle = getString(R.string.roster_contextmenu_title);

		if (isChild) {
			menu.setGroupVisible(R.id.roster_contextmenu_contact_menu, true);
			menu.setGroupVisible(R.id.roster_contextmenu_group_menu, false);
			menu.setHeaderTitle(menuTitle + "");
		} else {
			if (groupName.equals(AdapterConstants.EMPTY_GROUP)) {
				menu.setGroupVisible(R.id.roster_contextmenu_contact_menu,
						false);
				menu.setGroupVisible(R.id.roster_contextmenu_group_menu, false);
				menu.setHeaderTitle(menuTitle + groupName);
			} else {
				menu.setGroupVisible(R.id.roster_contextmenu_contact_menu,
						false);
				menu.setGroupVisible(R.id.roster_contextmenu_group_menu, true);
				menu.setHeaderTitle(menuTitle + groupName);
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

			String userJid = rosterEntryList.get(groupPosition).get(childPosition)
					.get(AdapterConstants.CONTACT_ID).jabberID;
			String userName = rosterEntryList.get(groupPosition).get(childPosition)
				.get(AdapterConstants.CONTACT_ID).screenName;

			int itemID = item.getItemId();

			switch (itemID) {
			case R.id.roster_contextmenu_contact_open_chat:
				startChatActivity(userJid, userName);
				return true;

			case R.id.roster_contextmenu_contact_delete:
				new RemoveRosterItemDialog(this, serviceAdapter, userJid).show();
				return true;

			case R.id.roster_contextmenu_contact_rename:
				new RenameRosterItemDialog(this, serviceAdapter, userJid).show();
				return true;

			case R.id.roster_contextmenu_contact_request_auth:
				serviceAdapter.requestAuthorizationForRosterItem(userJid);
				return true;

			case R.id.roster_contextmenu_contact_change_group:
				new MoveRosterItemToGroupDialog(this, serviceAdapter, userJid)
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
			case R.id.roster_contextmenu_group_rename:
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
		case R.id.menu_connect:
			toggleConnection(item);
			return true;

		case R.id.menu_add_friend:
			if (serviceAdapter.isAuthenticated()) {
				new AddRosterItemDialog(this, serviceAdapter).show();
			} else {
				showToastNotification(R.string.Global_authenticate_first);
			}
			return true;

		case R.id.menu_show_hide:
			showOffline = !showOffline;
			PreferenceManager.getDefaultSharedPreferences(this).edit().
				putBoolean(PreferenceConstants.SHOW_OFFLINE, showOffline).commit();
			updateRoster();
			return true;

		case R.id.menu_status:
			if (serviceAdapter.isAuthenticated()) {
				new ChangeStatusDialog(this, serviceAdapter).show();
			} else {
				showToastNotification(R.string.Global_authenticate_first);
			}
			return true;

		case R.id.menu_exit:
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
			new AboutDialog(this, serviceAdapter).show();
			return true;

		}

		return false;

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
		setProgressBarIndeterminateVisibility(isConnecting);
		String conn, lastStatus;
		if (isConnecting) {
			conn = getString(R.string.conn_connecting);
		} else if (isConnected()) {
			conn = getString(R.string.conn_online);
		} else {
			conn = getString(R.string.conn_offline);
		}
		setTitle(getString(R.string.conn_title, conn));

		if (serviceAdapter != null && (lastStatus =
					serviceAdapter.getConnectionStateString()) != null) {
			mConnectingText.setVisibility(View.VISIBLE);
			mConnectingText.setText(lastStatus);
		} else
			mConnectingText.setVisibility(View.GONE);
	}

	private void toggleConnection(MenuItem item) {
		if (isConnected() || isConnecting()) {
			setConnectingStatus(false);
			(new Thread() {
				public void run() {
					serviceAdapter.disconnect();
					stopService(xmppServiceIntent);
				}
			}).start();

		} else {
			setConnectingStatus(true);
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
		for (int count = 0; count < getExpandableListAdapter().getGroupCount(); count++) {
			getExpandableListView().expandGroup(count);
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
