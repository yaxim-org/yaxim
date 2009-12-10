package de.hdmstuttgart.yaxim.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import de.hdmstuttgart.yaxim.IXMPPRosterCallback;
import de.hdmstuttgart.yaxim.chat.IXMPPChatCallback;
import de.hdmstuttgart.yaxim.data.RosterItem;
import de.hdmstuttgart.yaxim.exceptions.YaximXMPPAdressMalformedException;
import de.hdmstuttgart.yaxim.exceptions.YaximXMPPException;
import de.hdmstuttgart.yaxim.util.PreferenceConstants;
import de.hdmstuttgart.yaxim.util.ConnectionState;
import de.hdmstuttgart.yaxim.util.StatusMode;
import de.hdmstuttgart.yaxim.util.XMPPHelper;

public class XMPPService extends GenericService {

	protected static final String TAG = "XMPPService";

	private HashSet<String> isBoundTo;

	private String jabServer;
	private String jabUsername;
	private String jabPassword;
	private String jabRessource;
	private int jabPort;
	private int jabPriority;
	private boolean connStartup;
	private boolean jabReconnect;
	private int jabReconnectCount;

	private boolean connectionDemanded;
	private boolean isConnected;
	private Thread connectingThread;

	private Smackable xmppAdapter;
	private IXMPPRosterService.Stub service2RosterConnection;
	private IXMPPChatService.Stub service2ChatConnection;
	private RemoteCallbackList<IXMPPRosterCallback> rosterCallbacks;
	private HashMap<String, RemoteCallbackList<IXMPPChatCallback>> chatCallbacks;

	private Handler mainHandler;

	@Override
	public IBinder onBind(Intent intent) {
		super.onBind(intent);
		String caller = intent.getDataString();
		if ((caller != null) && caller.equals("chatwindow"))
			return service2ChatConnection;

		return service2RosterConnection;
	}

	@Override
	public boolean onUnbind(Intent intent) {
		return super.onUnbind(intent);
	}

	@Override
	public void onCreate() {
		super.onCreate();
		rosterCallbacks = new RemoteCallbackList<IXMPPRosterCallback>();
		chatCallbacks = new HashMap<String, RemoteCallbackList<IXMPPChatCallback>>();
		isBoundTo = new HashSet<String>();
		mainHandler = new Handler();
		createServiceRosterStub();
		createServiceChatStub();
		getPreferences(PreferenceManager.getDefaultSharedPreferences(this));
		connectionDemanded = connStartup;
		if (connStartup) {
			/* start our own service so it remains in background even when unbound */
			Intent xmppServiceIntent = new Intent(this, XMPPService.class);
			xmppServiceIntent.setAction("de.hdmstuttgart.yaxim.XMPPSERVICE");
			startService(xmppServiceIntent);
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		rosterCallbacks.kill();
		for (String key : chatCallbacks.keySet()) {
			chatCallbacks.get(key).kill();
		}
		doDisconnect();
	}

	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
		initiateConnection();
	}

	public void initiateConnection() {
		if (xmppAdapter == null) {
			createAdapter();
			registerAdapterCallback();
		}
		doConnect();
	}

	private void createServiceChatStub() {
		service2ChatConnection = new IXMPPChatService.Stub() {

			public void registerChatCallback(IXMPPChatCallback callback,
					String jabberID) throws RemoteException {

				if (callback != null) {
					resetNotificationCounter();
					if (chatCallbacks.containsKey(jabberID))
						chatCallbacks.get(jabberID).register(callback);
					else {
						RemoteCallbackList<IXMPPChatCallback> chatCallback = new RemoteCallbackList<IXMPPChatCallback>();
						chatCallback.register(callback);
						chatCallbacks.put(jabberID, chatCallback);
					}
				}
				isBoundTo.add(jabberID);
			}

			public void unregisterChatCallback(IXMPPChatCallback callback,
					String jabberID) throws RemoteException {
				if (callback != null) {
					chatCallbacks.get(jabberID).unregister(callback);
				}
				isBoundTo.remove(jabberID);
			}

			public void sendMessage(String user, String message)
					throws RemoteException {
				xmppAdapter.sendMessage(user, message);
			}

			public List<String> pullMessagesForContact(String jabberID)
					throws RemoteException {
				if (xmppAdapter != null)
					return xmppAdapter.pullMessagesForContact(jabberID);
				return new ArrayList<String>();
			}

			public boolean isAuthenticated() throws RemoteException {
				if (xmppAdapter != null)
					return xmppAdapter.isAuthenticated();
				
				return false;
			}
		};
	}

	private void createServiceRosterStub() {
		service2RosterConnection = new IXMPPRosterService.Stub() {

			public void registerRosterCallback(IXMPPRosterCallback callback)
					throws RemoteException {
				if (callback != null)
					rosterCallbacks.register(callback);
			}

			public void unregisterRosterCallback(IXMPPRosterCallback callback)
					throws RemoteException {
				if (callback != null)
					rosterCallbacks.unregister(callback);
			}

			public int getConnectionState() throws RemoteException {
				if (xmppAdapter != null && xmppAdapter.isAuthenticated())
					return ConnectionState.AUTHENTICATED;
				else if (connectionDemanded)
					return ConnectionState.CONNECTING;
				else return ConnectionState.OFFLINE;
			}

			public void setStatus(String status, String statusMsg)
					throws RemoteException {
				if (status.equals("offline")) {
					doDisconnect();
					return;
				}
				xmppAdapter.setStatus(StatusMode.valueOf(status), statusMsg);
			}

			public void addRosterItem(String user, String alias, String group)
					throws RemoteException {
				try {
					xmppAdapter.addRosterItem(user, alias, group);
				} catch (YaximXMPPException e) {
					shortToastNotify(e.getMessage());
					Log.e(TAG, "exception in addRosterItem(): "
							+ e.getMessage());
				}
			}

			public void addRosterGroup(String group) throws RemoteException {
				xmppAdapter.addRosterGroup(group);
			}

			public void removeRosterItem(String user) throws RemoteException {
				try {
					xmppAdapter.removeRosterItem(user);
				} catch (YaximXMPPException e) {
					shortToastNotify(e.getMessage());
					Log.e(TAG, "exception in removeRosterItem(): "
							+ e.getMessage());
				}
			}

			public void moveRosterItemToGroup(String user, String group)
					throws RemoteException {
				try {
					xmppAdapter.moveRosterItemToGroup(user, group);
				} catch (YaximXMPPException e) {
					shortToastNotify(e.getMessage());
					Log.e(TAG, "exception in moveRosterItemToGroup(): "
							+ e.getMessage());
				}
			}

			public void renameRosterItem(String user, String newName)
					throws RemoteException {
				try {
					xmppAdapter.renameRosterItem(user, newName);
				} catch (YaximXMPPException e) {
					shortToastNotify(e.getMessage());
					Log.e(TAG, "exception in renameRosterItem(): "
							+ e.getMessage());
				}
			}

			public List<String> getRosterGroups() throws RemoteException {
				return xmppAdapter.getRosterGroups();
			}

			public List<RosterItem> getRosterEntriesByGroup(String group)
					throws RemoteException {
				return xmppAdapter.getRosterEntriesByGroup(group);
			}

			public void renameRosterGroup(String group, String newGroup)
					throws RemoteException {
				xmppAdapter.renameRosterGroup(group, newGroup);
			}

			public void disconnect() throws RemoteException {
				doDisconnect();
			}

			public void connect() throws RemoteException {
				doConnect();
			}
		};
	}

	private void splitJabberID(String jid) {
		Pattern p = Pattern.compile("\\@");
		String[] res = p.split(jid);
		this.jabUsername = res[0];
		this.jabServer = res[1];
	}

	private void doConnect() {
		connectionDemanded = true;
		if (connectingThread != null)
			return;
		connectingThread = new Thread() { public void run() {
			try {
				if (xmppAdapter.doConnect()) {
					connectionEstablished();
					jabReconnectCount = 0;
					isConnected = true;
				} else
					connectionFailed();
			} catch (YaximXMPPException e) {
				connectionFailed();
				Log.e(TAG, "YaximXMPPException in doConnect(): " + e);
			}
			connectingThread = null;
		}};
		connectingThread.start();
	}

	private void connectionFailed() {
		final int broadCastItems = rosterCallbacks.beginBroadcast();
		for (int i = 0; i < broadCastItems; i++) {
			try {
				rosterCallbacks.getBroadcastItem(i).connectionFailed(
						jabReconnect && jabReconnectCount <= 5);
			} catch (RemoteException e) {
				Log.e(TAG, "caught RemoteException: " + e.getMessage());
			}
		}
		rosterCallbacks.finishBroadcast();
		isConnected = false;
		if (jabReconnect && jabReconnectCount <= 5) {
			jabReconnectCount++;
			Log.i(TAG, "connectionFailed(" + jabReconnectCount + "/5): " +
					"attempting reconnect in 5s...");
			mainHandler.postDelayed(new Runnable() {
				public void run() {
					doConnect();
				}
			}, 5000);
		}
	}

	private void connectionEstablished() {
		final int broadCastItems = rosterCallbacks.beginBroadcast();
		for (int i = 0; i < broadCastItems; i++) {
			try {
				rosterCallbacks.getBroadcastItem(i).connectionSuccessful();
			} catch (RemoteException e) {
				Log.e(TAG, "caught RemoteException: " + e.getMessage());
			}
		}
		rosterCallbacks.finishBroadcast();
	}

	public void doDisconnect() {
		if (xmppAdapter != null) {
			xmppAdapter.unRegisterCallback();
		}
		xmppAdapter = null;
		connectionDemanded = false;
	}

	protected void getPreferences(SharedPreferences prefs) {
		super.getPreferences(prefs);
		this.jabPassword = prefs.getString(PreferenceConstants.PASSWORD, "");
		this.jabRessource = prefs.getString(PreferenceConstants.RESSOURCE,
				"yaxim");
		this.jabPort = XMPPHelper.tryToParseInt(prefs.getString(
				PreferenceConstants.PORT, PreferenceConstants.DEFAULT_PORT),
				PreferenceConstants.DEFAULT_PORT_INT);
		this.jabPriority = XMPPHelper.tryToParseInt(prefs.getString(
				"account_prio", "0"), 0);
		this.connStartup = prefs.getBoolean(PreferenceConstants.CONN_STARTUP, false);
		this.jabReconnect = prefs.getBoolean(PreferenceConstants.AUTO_RECONNECT, false);

		String jid = prefs.getString(PreferenceConstants.JID, "");

		try {
			XMPPHelper.verifyJabberID(jid);
			splitJabberID(jid);
		} catch (YaximXMPPAdressMalformedException e) {
			shortToastNotify("Malformed JabberID!");
			Log.e(TAG, "Exception in getPreferences(): " + e);
		}
	}

	private void createAdapter() {
		try {
			xmppAdapter = new SmackableImp(jabServer, jabUsername, jabPassword,
					jabRessource, jabPort, jabPriority);
		} catch (NullPointerException e) {
			e.printStackTrace();
		}
	}

	private void registerAdapterCallback() {
		xmppAdapter.registerCallback(new XMPPServiceCallback() {

			public void newMessage(String from, String message) {
				if (!isBoundTo.contains(from)) {
					Log.i(TAG, "notification: " + from);
					notifyClient(from, message);
				} else {
					handleIncomingMessage(from, message);
				}
			}

			public void rosterChanged() {
				if (!xmppAdapter.isAuthenticated()) {
					if (isConnected) {
						/* XXX: hack - it seems we need to reset the connection */
						xmppAdapter.unRegisterCallback();
						registerAdapterCallback();
						connectionFailed();
					}
					return;
				}
				final int broadCastItems = rosterCallbacks.beginBroadcast();
				for (int i = 0; i < broadCastItems; ++i) {
					try {
						rosterCallbacks.getBroadcastItem(i).rosterChanged();
					} catch (RemoteException e) {
						Log.e(TAG, "caught RemoteException: " + e.getMessage());
					}
				}
				rosterCallbacks.finishBroadcast();
			}

			public boolean isBoundTo(String jabberID) {
				return isBoundTo.contains(jabberID);
			}
		});
	}

	private void handleIncomingMessage(String from, String message) {
		RemoteCallbackList<IXMPPChatCallback> chatCallbackList = chatCallbacks
				.get(from);
		final int broadCastItems = chatCallbackList.beginBroadcast();
		for (int i = 0; i < broadCastItems; i++) {
			try {
				chatCallbackList.getBroadcastItem(i).newMessage(from, message);
			} catch (RemoteException e) {
				Log.e(TAG, "caught RemoteException: " + e.getMessage());
			}
		}
		rosterCallbacks.finishBroadcast();
	}
}
