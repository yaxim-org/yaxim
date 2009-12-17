package de.hdmstuttgart.yaxim.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import de.hdmstuttgart.yaxim.IXMPPRosterCallback;
import de.hdmstuttgart.yaxim.chat.IXMPPChatCallback;
import de.hdmstuttgart.yaxim.data.RosterItem;
import de.hdmstuttgart.yaxim.data.YaximConfiguration;
import de.hdmstuttgart.yaxim.exceptions.YaximXMPPException;
import de.hdmstuttgart.yaxim.util.ConnectionState;
import de.hdmstuttgart.yaxim.util.StatusMode;

public class XMPPService extends GenericService {

	protected static final String TAG = "XMPPService";

	private boolean mConnectionDemanded;
	private boolean mIsConnected;
	private int mReconnectCount;
	private Thread mConnectingThread;

	private Smackable mSmackable;
	private IXMPPRosterService.Stub mService2RosterConnection;
	private IXMPPChatService.Stub mService2ChatConnection;

	private RemoteCallbackList<IXMPPRosterCallback> mRosterCallbacks = new RemoteCallbackList<IXMPPRosterCallback>();
	private HashMap<String, RemoteCallbackList<IXMPPChatCallback>> mChatCallbacks = new HashMap<String, RemoteCallbackList<IXMPPChatCallback>>();
	private HashSet<String> mIsBoundTo = new HashSet<String>();

	private Handler mMainHandler = new Handler();

	@Override
	public IBinder onBind(Intent intent) {
		super.onBind(intent);
		String caller = intent.getDataString();
		if ((caller != null) && caller.equals("chatwindow"))
			return mService2ChatConnection;

		return mService2RosterConnection;
	}

	@Override
	public boolean onUnbind(Intent intent) {
		return super.onUnbind(intent);
	}

	@Override
	public void onCreate() {
		super.onCreate();

		createServiceRosterStub();
		createServiceChatStub();

		mConfig = new YaximConfiguration(PreferenceManager
				.getDefaultSharedPreferences(this));

		mConnectionDemanded = mConfig.connStartup;

		if (mConfig.connStartup) {
			/*
			 * start our own service so it remains in background even when
			 * unbound
			 */
			Intent xmppServiceIntent = new Intent(this, XMPPService.class);
			xmppServiceIntent.setAction("de.hdmstuttgart.yaxim.XMPPSERVICE");
			startService(xmppServiceIntent);
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		mRosterCallbacks.kill();
		for (String key : mChatCallbacks.keySet()) {
			mChatCallbacks.get(key).kill();
		}
		doDisconnect();
	}

	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
		initiateConnection();
	}

	public void initiateConnection() {
		if (mSmackable == null) {
			createAdapter();
			registerAdapterCallback();
		}
		doConnect();
	}

	private void createServiceChatStub() {
		mService2ChatConnection = new IXMPPChatService.Stub() {

			public void registerChatCallback(IXMPPChatCallback callback,
					String jabberID) throws RemoteException {

				if (callback != null) {
					resetNotificationCounter();
					if (mChatCallbacks.containsKey(jabberID))
						mChatCallbacks.get(jabberID).register(callback);
					else {
						RemoteCallbackList<IXMPPChatCallback> chatCallback = new RemoteCallbackList<IXMPPChatCallback>();
						chatCallback.register(callback);
						mChatCallbacks.put(jabberID, chatCallback);
					}
				}
				mIsBoundTo.add(jabberID);
			}

			public void unregisterChatCallback(IXMPPChatCallback callback,
					String jabberID) throws RemoteException {
				if (callback != null) {
					mChatCallbacks.get(jabberID).unregister(callback);
				}
				mIsBoundTo.remove(jabberID);
			}

			public void sendMessage(String user, String message)
					throws RemoteException {
				mSmackable.sendMessage(user, message);
			}

			public List<String> pullMessagesForContact(String jabberID)
					throws RemoteException {
				if (mSmackable != null)
					return mSmackable.pullMessagesForContact(jabberID);
				return new ArrayList<String>();
			}

			public boolean isAuthenticated() throws RemoteException {
				if (mSmackable != null)
					return mSmackable.isAuthenticated();

				return false;
			}
		};
	}

	private void createServiceRosterStub() {
		mService2RosterConnection = new IXMPPRosterService.Stub() {

			public void registerRosterCallback(IXMPPRosterCallback callback)
					throws RemoteException {
				if (callback != null)
					mRosterCallbacks.register(callback);
			}

			public void unregisterRosterCallback(IXMPPRosterCallback callback)
					throws RemoteException {
				if (callback != null)
					mRosterCallbacks.unregister(callback);
			}

			public int getConnectionState() throws RemoteException {
				if (mSmackable != null && mSmackable.isAuthenticated())
					return ConnectionState.AUTHENTICATED;
				else if (mConnectionDemanded)
					return ConnectionState.CONNECTING;
				else
					return ConnectionState.OFFLINE;
			}

			public void setStatus(String status, String statusMsg)
					throws RemoteException {
				if (status.equals("offline")) {
					doDisconnect();
					return;
				}
				mSmackable.setStatus(StatusMode.valueOf(status), statusMsg);
			}

			public void addRosterItem(String user, String alias, String group)
					throws RemoteException {
				try {
					mSmackable.addRosterItem(user, alias, group);
				} catch (YaximXMPPException e) {
					shortToastNotify(e.getMessage());
					Log.e(TAG, "exception in addRosterItem(): "
							+ e.getMessage());
				}
			}

			public void addRosterGroup(String group) throws RemoteException {
				mSmackable.addRosterGroup(group);
			}

			public void removeRosterItem(String user) throws RemoteException {
				try {
					mSmackable.removeRosterItem(user);
				} catch (YaximXMPPException e) {
					shortToastNotify(e.getMessage());
					Log.e(TAG, "exception in removeRosterItem(): "
							+ e.getMessage());
				}
			}

			public void moveRosterItemToGroup(String user, String group)
					throws RemoteException {
				try {
					mSmackable.moveRosterItemToGroup(user, group);
				} catch (YaximXMPPException e) {
					shortToastNotify(e.getMessage());
					Log.e(TAG, "exception in moveRosterItemToGroup(): "
							+ e.getMessage());
				}
			}

			public void renameRosterItem(String user, String newName)
					throws RemoteException {
				try {
					mSmackable.renameRosterItem(user, newName);
				} catch (YaximXMPPException e) {
					shortToastNotify(e.getMessage());
					Log.e(TAG, "exception in renameRosterItem(): "
							+ e.getMessage());
				}
			}

			public List<String> getRosterGroups() throws RemoteException {
				return mSmackable.getRosterGroups();
			}

			public List<RosterItem> getRosterEntriesByGroup(String group)
					throws RemoteException {
				return mSmackable.getRosterEntriesByGroup(group);
			}

			public void renameRosterGroup(String group, String newGroup)
					throws RemoteException {
				mSmackable.renameRosterGroup(group, newGroup);
			}

			public void disconnect() throws RemoteException {
				doDisconnect();
			}

			public void connect() throws RemoteException {
				doConnect();
			}
		};
	}

	private void doConnect() {
		mConnectionDemanded = true;

		if (mConnectingThread != null)
			return;

		mConnectingThread = new Thread() {

			public void run() {
				try {
					if (mSmackable.doConnect()) {
						connectionEstablished();
						mReconnectCount = 0;
						mIsConnected = true;
					} else
						connectionFailed();
				} catch (YaximXMPPException e) {
					connectionFailed();
					Log.e(TAG, "YaximXMPPException in doConnect(): " + e);
				}
				mConnectingThread = null;
			}
		};
		mConnectingThread.start();
	}

	private void connectionFailed() {
		final int broadCastItems = mRosterCallbacks.beginBroadcast();
		for (int i = 0; i < broadCastItems; i++) {
			try {
				mRosterCallbacks.getBroadcastItem(i).connectionFailed(
						mConfig.reconnect && mReconnectCount <= 5);
			} catch (RemoteException e) {
				Log.e(TAG, "caught RemoteException: " + e.getMessage());
			}
		}
		mRosterCallbacks.finishBroadcast();
		mIsConnected = false;
		if (mConfig.reconnect && mReconnectCount <= 5) {
			mReconnectCount++;
			Log.i(TAG, "connectionFailed(" + mReconnectCount + "/5): "
					+ "attempting reconnect in 5s...");
			mMainHandler.postDelayed(new Runnable() {
				public void run() {
					doConnect();
				}
			}, 5000);
		}
	}

	private void connectionEstablished() {
		final int broadCastItems = mRosterCallbacks.beginBroadcast();
		for (int i = 0; i < broadCastItems; i++) {
			try {
				mRosterCallbacks.getBroadcastItem(i).connectionSuccessful();
			} catch (RemoteException e) {
				Log.e(TAG, "caught RemoteException: " + e.getMessage());
			}
		}
		mRosterCallbacks.finishBroadcast();
	}

	public void doDisconnect() {
		mIsConnected = false; /* hack to prevent recursion in rosterChanged() */
		if (mSmackable != null) {
			mSmackable.unRegisterCallback();
		}
		mSmackable = null;
		mConnectionDemanded = false;
	}

	private void createAdapter() {
		try {
			mSmackable = new SmackableImp(mConfig);
		} catch (NullPointerException e) {
			e.printStackTrace();
		}
	}

	private void registerAdapterCallback() {
		mSmackable.registerCallback(new XMPPServiceCallback() {

			public void newMessage(String from, String message) {
				if (!mIsBoundTo.contains(from)) {
					Log.i(TAG, "notification: " + from);
					notifyClient(from, message);
				} else {
					handleIncomingMessage(from, message);
				}
			}

			public void rosterChanged() {
				if (!mSmackable.isAuthenticated()) {
					if (mIsConnected) {
						/* XXX: hack - it seems we need to reset the connection */
						mSmackable.unRegisterCallback();
						registerAdapterCallback();
						connectionFailed();
					}
					return;
				}
				final int broadCastItems = mRosterCallbacks.beginBroadcast();
				for (int i = 0; i < broadCastItems; ++i) {
					try {
						mRosterCallbacks.getBroadcastItem(i).rosterChanged();
					} catch (RemoteException e) {
						Log.e(TAG, "caught RemoteException: " + e.getMessage());
					}
				}
				mRosterCallbacks.finishBroadcast();
			}

			public boolean isBoundTo(String jabberID) {
				return mIsBoundTo.contains(jabberID);
			}
		});
	}

	private void handleIncomingMessage(String from, String message) {
		RemoteCallbackList<IXMPPChatCallback> chatCallbackList = mChatCallbacks
				.get(from);
		final int broadCastItems = chatCallbackList.beginBroadcast();
		for (int i = 0; i < broadCastItems; i++) {
			try {
				chatCallbackList.getBroadcastItem(i).newMessage(from, message);
			} catch (RemoteException e) {
				Log.e(TAG, "caught RemoteException: " + e.getMessage());
			}
		}
		mRosterCallbacks.finishBroadcast();
	}
}
