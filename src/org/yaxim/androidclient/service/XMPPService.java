package org.yaxim.androidclient.service;

import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.yaxim.androidclient.IXMPPRosterCallback;
import org.yaxim.androidclient.data.RosterItem;
import org.yaxim.androidclient.data.YaximConfiguration;
import org.yaxim.androidclient.exceptions.YaximXMPPException;
import org.yaxim.androidclient.util.ConnectionState;
import org.yaxim.androidclient.util.StatusMode;

import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;

public class XMPPService extends GenericService {

	protected static final String TAG = "XMPPService";

	private AtomicBoolean mIsConnected = new AtomicBoolean(false);
	private Thread mConnectingThread;

	private Smackable mSmackable;
	private IXMPPRosterService.Stub mService2RosterConnection;
	private IXMPPChatService.Stub mServiceChatConnection;

	private RemoteCallbackList<IXMPPRosterCallback> mRosterCallbacks = new RemoteCallbackList<IXMPPRosterCallback>();
	private HashSet<String> mIsBoundTo = new HashSet<String>();
	private Handler mMainHandler = new Handler();

	@Override
	public IBinder onBind(Intent intent) {
		super.onBind(intent);
		String caller = intent.getDataString();
		if ((caller != null) && caller.equals("chatwindow")) {
			resetNotificationCounter();
			return mServiceChatConnection;
		}

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

		if (mConfig.autoConnect) {
			/*
			 * start our own service so it remains in background even when
			 * unbound
			 */
			Intent xmppServiceIntent = new Intent(this, XMPPService.class);
			xmppServiceIntent.setAction("org.yaxim.androidclient.XMPPSERVICE");
			startService(xmppServiceIntent);
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		mRosterCallbacks.kill();
		doDisconnect();
	}

	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
		initiateConnection();
	}

	public void initiateConnection() {
		setForeground(true);
		if (mSmackable == null) {
			createAdapter();
			registerAdapterCallback();
		}
		doConnect();
	}

	private void createServiceChatStub() {
		mServiceChatConnection = new IXMPPChatService.Stub() {

			public void sendMessage(String user, String message)
					throws RemoteException {
				mSmackable.sendMessage(user, message);
			}

			public boolean isAuthenticated() throws RemoteException {
				if (mSmackable != null) {
					return mSmackable.isAuthenticated();
				}

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
		if (mConnectingThread != null) {
			return;
		}

		mConnectingThread = new Thread() {

			public void run() {
				try {
					if (!mSmackable.doConnect())
						postConnectionFailed();
				} catch (YaximXMPPException e) {
					postConnectionFailed();
					Log.e(TAG, "YaximXMPPException in doConnect(): " + e);
				}
				mConnectingThread = null;
			}
		};
		mConnectingThread.start();
	}
	
	public void postConnectionFailed() {
		mMainHandler.post(new Runnable() {
			public void run() {
				connectionFailed();
			}
		});
	}	

	private void connectionFailed() {
		final int broadCastItems = mRosterCallbacks.beginBroadcast();
		for (int i = 0; i < broadCastItems; i++) {
			try {
				mRosterCallbacks.getBroadcastItem(i).connectionFailed();
			} catch (RemoteException e) {
				Log.e(TAG, "caught RemoteException: " + e.getMessage());
			}
		}
		mRosterCallbacks.finishBroadcast();
		mIsConnected.set(false);
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
		mIsConnected.set(false); /* hack to prevent recursion in rosterChanged() */
		if (mSmackable != null) {
			mSmackable.unRegisterCallback();
		}
		mSmackable = null;
		setForeground(false);
	}

	private void createAdapter() {
		try {
			mSmackable = new SmackableImp(mConfig, getContentResolver());
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
				}
			}

			public void rosterChanged() {
				// when connected, we get a rosterChanged CB too
				if (!mIsConnected.get()) {
					connectionEstablished();
					mIsConnected.set(true);
				} else if (!mSmackable.isAuthenticated()) {
					if (mIsConnected.get()) {
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

}
