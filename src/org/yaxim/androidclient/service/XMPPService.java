package org.yaxim.androidclient.service;

import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.yaxim.androidclient.IXMPPRosterCallback;
import org.yaxim.androidclient.MainWindow;
import org.yaxim.androidclient.R;
import org.yaxim.androidclient.exceptions.YaximXMPPException;
import org.yaxim.androidclient.util.ConnectionState;
import org.yaxim.androidclient.util.StatusMode;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;

public class XMPPService extends GenericService {

	private AtomicBoolean mIsConnected = new AtomicBoolean(false);
	private AtomicBoolean mConnectionDemanded = new AtomicBoolean(false); // should we try to reconnect?
	private static final int RECONNECT_AFTER = 5;
	private static final int RECONNECT_MAXIMUM = 10*60;
	private static final String RECONNECT_ALARM = "org.yaxim.androidclient.RECONNECT_ALARM";
	private int mReconnectTimeout = RECONNECT_AFTER;
	private String mLastConnectionError = null;
	private String mReconnectInfo = "";
	private Intent mAlarmIntent = new Intent(RECONNECT_ALARM);
	private PendingIntent mPAlarmIntent;
	private BroadcastReceiver mAlarmReceiver = new ReconnectAlarmReceiver();

	private ServiceNotification mServiceNotification = null;

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
		String chatPartner = intent.getDataString();
		if ((chatPartner != null)) {
			resetNotificationCounter(chatPartner);
			mIsBoundTo.add(chatPartner);
			return mServiceChatConnection;
		}

		return mService2RosterConnection;
	}

	@Override
	public void onRebind(Intent intent) {
		super.onRebind(intent);
		String chatPartner = intent.getDataString();
		if ((chatPartner != null)) {
			mIsBoundTo.add(chatPartner);
			resetNotificationCounter(chatPartner);
		}
	}

	@Override
	public boolean onUnbind(Intent intent) {
		String chatPartner = intent.getDataString();
		if ((chatPartner != null)) {
			mIsBoundTo.remove(chatPartner);
		}
		return true;
	}

	@Override
	public void onCreate() {
		super.onCreate();

		createServiceRosterStub();
		createServiceChatStub();

		mPAlarmIntent = PendingIntent.getBroadcast(this, 0, mAlarmIntent,
					PendingIntent.FLAG_UPDATE_CURRENT);
		registerReceiver(mAlarmReceiver, new IntentFilter(RECONNECT_ALARM));

		// for the initial connection, check if autoConnect is set
		mConnectionDemanded.set(mConfig.autoConnect);
		YaximBroadcastReceiver.initNetworkStatus(getApplicationContext());

		if (mConfig.autoConnect) {
			/*
			 * start our own service so it remains in background even when
			 * unbound
			 */
			Intent xmppServiceIntent = new Intent(this, XMPPService.class);
			xmppServiceIntent.setAction("org.yaxim.androidclient.XMPPSERVICE");
			startService(xmppServiceIntent);
		}

		mServiceNotification = ServiceNotification.getInstance();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		((AlarmManager)getSystemService(Context.ALARM_SERVICE)).cancel(mPAlarmIntent);
		mRosterCallbacks.kill();
		doDisconnect();
		unregisterReceiver(mAlarmReceiver);
	}

	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
		if (intent != null) {
			boolean disconnect = intent.getBooleanExtra("disconnect", false);
			boolean reconnect = intent.getBooleanExtra("reconnect", false);
			
			logInfo("disconnect/reconnect: "+disconnect+ " " + reconnect);
			if (disconnect) {
				if (mConnectingThread != null || mIsConnected.get())
					connectionFailed(getString(R.string.conn_networkchg));
				return;
			}
			if (reconnect) {
				// reset reconnection timeout
				mReconnectTimeout = RECONNECT_AFTER;
				if (mConnectionDemanded.get())
					doConnect();
				else
					stopSelf(); // started by YaximBroadcastReceiver, no connection initiation
				return;
			}
		}
		
		mConnectionDemanded.set(mConfig.autoConnect);
		doConnect();
	}

	private void createServiceChatStub() {
		mServiceChatConnection = new IXMPPChatService.Stub() {

			public void sendMessage(String user, String message)
					throws RemoteException {
				if (mSmackable != null)
					mSmackable.sendMessage(user, message);
				else
					SmackableImp.sendOfflineMessage(getContentResolver(),
							user, message);
			}

			public boolean isAuthenticated() throws RemoteException {
				if (mSmackable != null) {
					return mSmackable.isAuthenticated();
				}

				return false;
			}
			
			public void clearNotifications(String Jid) throws RemoteException {
				clearNotification(Jid);
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
				if (mSmackable != null && mSmackable.isAuthenticated()) {
					return ConnectionState.AUTHENTICATED;
				} else if (mConnectionDemanded.get()) {
					return ConnectionState.CONNECTING;
				} else {
					return ConnectionState.OFFLINE;
				}
			}

			public String getConnectionStateString() throws RemoteException {
				if (mLastConnectionError != null)
					return mLastConnectionError + mReconnectInfo;
				else
					return null;
			}


			public void setStatusFromConfig()
					throws RemoteException {
				mSmackable.setStatusFromConfig();
				updateServiceNotification();
			}

			public void addRosterItem(String user, String alias, String group)
					throws RemoteException {
				try {
					mSmackable.addRosterItem(user, alias, group);
				} catch (YaximXMPPException e) {
					shortToastNotify(e.getMessage());
					logError("exception in addRosterItem(): " + e.getMessage());
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
					logError("exception in removeRosterItem(): "
							+ e.getMessage());
				}
			}

			public void moveRosterItemToGroup(String user, String group)
					throws RemoteException {
				try {
					mSmackable.moveRosterItemToGroup(user, group);
				} catch (YaximXMPPException e) {
					shortToastNotify(e.getMessage());
					logError("exception in moveRosterItemToGroup(): "
							+ e.getMessage());
				}
			}

			public void renameRosterItem(String user, String newName)
					throws RemoteException {
				try {
					mSmackable.renameRosterItem(user, newName);
				} catch (YaximXMPPException e) {
					shortToastNotify(e.getMessage());
					logError("exception in renameRosterItem(): "
							+ e.getMessage());
				}
			}

			public void renameRosterGroup(String group, String newGroup)
					throws RemoteException {
				mSmackable.renameRosterGroup(group, newGroup);
			}

			public void disconnect() throws RemoteException {
				doDisconnect();
			}

			public void connect() throws RemoteException {
				mConnectionDemanded.set(true);
				doConnect();
			}

			public void requestAuthorizationForRosterItem(String user)
					throws RemoteException {
				mSmackable.requestAuthorizationForRosterItem(user);
			}
		};
	}

	private void updateServiceNotification() {
		if (!mConfig.foregroundService)
			return;
		String title = getString(R.string.conn_title, mConfig.jabberID);
		Notification n = new Notification(R.drawable.ic_offline, title,
				System.currentTimeMillis());
		n.flags = Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;

		Intent notificationIntent = new Intent(this, MainWindow.class);
		notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		n.contentIntent = PendingIntent.getActivity(this, 0, notificationIntent,
				PendingIntent.FLAG_UPDATE_CURRENT);

		String message = mLastConnectionError;
		if (message != null)
			message += mReconnectInfo;
		if (mIsConnected.get()) {
			message = MainWindow.getStatusTitle(this, mConfig.statusMode, mConfig.statusMessage);
			n.icon = R.drawable.ic_online;
		}
		n.setLatestEventInfo(this, title, message, n.contentIntent);

		mServiceNotification.showNotification(this, SERVICE_NOTIFICATION,
				n);
	}

	private void doConnect() {
		if (mConnectingThread != null) {
			// a connection is still goign on!
			return;
		}

		mLastConnectionError = getString(R.string.conn_connecting);
		updateServiceNotification();
		if (mSmackable != null) {
			mSmackable.unRegisterCallback();
		}

		mConnectingThread = new Thread() {

			public void run() {
				try {
					createAdapter();
					if (!mSmackable.doConnect()) {
						postConnectionFailed("Inconsistency in Smackable.doConnect()");
					}
					postConnectionEstablished();
				} catch (YaximXMPPException e) {
					String message = e.getLocalizedMessage();
					if (e.getCause() != null)
						message += "\n" + e.getCause().getLocalizedMessage();
					postConnectionFailed(message);
					logError("YaximXMPPException in doConnect():");
					e.printStackTrace();
				} finally {
					if (mConnectingThread != null) synchronized(mConnectingThread) {
						mConnectingThread = null;
					}
				}
			}

		};
		mConnectingThread.start();
	}

	private void postConnectionFailed(final String reason) {
		mMainHandler.post(new Runnable() {
			public void run() {
				connectionFailed(reason);
			}
		});
	}

	private void postConnectionEstablished() {
		mMainHandler.post(new Runnable() {
			public void run() {
				connectionEstablished();
			}
		});
	}

	private void postRosterChanged() {
		mMainHandler.post(new Runnable() {
			public void run() {
				rosterChanged();
			}
		});
	}

	private void broadcastConnectionStatus(boolean isConnected, boolean willReconnect) {
		final int broadCastItems = mRosterCallbacks.beginBroadcast();
		for (int i = 0; i < broadCastItems; i++) {
			try {
				mRosterCallbacks.getBroadcastItem(i).connectionStatusChanged(isConnected, willReconnect);
			} catch (RemoteException e) {
				logError("caught RemoteException: " + e.getMessage());
			}
		}
		mRosterCallbacks.finishBroadcast();
	}

	private void connectionFailed(String reason) {
		logInfo("connectionFailed: " + reason);
		mLastConnectionError = reason;
		mIsConnected.set(false);
		broadcastConnectionStatus(false, mConnectionDemanded.get());
		if (mConnectionDemanded.get()) {
			mReconnectInfo = getString(R.string.conn_reconnect, mReconnectTimeout);
			updateServiceNotification();
			logInfo("connectionFailed(): registering reconnect in " + mReconnectTimeout + "s");
			((AlarmManager)getSystemService(Context.ALARM_SERVICE)).set(AlarmManager.RTC_WAKEUP,
					System.currentTimeMillis() + mReconnectTimeout * 1000, mPAlarmIntent);
			mReconnectTimeout = mReconnectTimeout * 2;
			if (mReconnectTimeout > RECONNECT_MAXIMUM)
				mReconnectTimeout = RECONNECT_MAXIMUM;
		} else {
			mReconnectInfo = "";
			mServiceNotification.hideNotification(this, SERVICE_NOTIFICATION);
		}

	}

	private void connectionEstablished() {
		// once we are connected, use autoReconnect to determine reconnections
		mConnectionDemanded.set(mConfig.autoReconnect);
		mLastConnectionError = null;
		mIsConnected.set(true);
		mReconnectTimeout = RECONNECT_AFTER;
		updateServiceNotification();
		broadcastConnectionStatus(true, false);
	}

	private void rosterChanged() {
		// gracefully handle^W ignore events after a disconnect
		if (mSmackable == null)
			return;
		if (!mIsConnected.get() && mSmackable.isAuthenticated()) {
			// We get a roster changed update, but we are not connected,
			// that means we just got connected and need to notify the Activity.
			logInfo("rosterChanged(): we just got connected");
			//connectionEstablished();
		}
		if (mIsConnected.get() && mSmackable != null && !mSmackable.isAuthenticated()) {
			logInfo("rosterChanged(): disconnected without warning");
			connectionFailed(getString(R.string.conn_disconnected));
		}
	}

	public void doDisconnect() {
		mConnectionDemanded.set(false);
		if (mConnectingThread != null) {
			synchronized(mConnectingThread) {
				try {
					mConnectingThread.interrupt();
					mConnectingThread.join(50);
				} catch (InterruptedException e) {
					logError("doDisconnect: failed catching connecting thread");
				} finally {
					mConnectingThread = null;
				}
			}
		}
		if (mSmackable != null) {
			mSmackable.unRegisterCallback();
			mSmackable = null;
		}
		mSmackable = null;
		connectionFailed(getString(R.string.conn_offline));
		mServiceNotification.hideNotification(this, SERVICE_NOTIFICATION);
	}

	private void createAdapter() {
		System.setProperty("smack.debugEnabled", "" + mConfig.smackdebug);
		try {
			mSmackable = new SmackableImp(mConfig, getContentResolver(), this);
		} catch (NullPointerException e) {
			e.printStackTrace();
		}

		mSmackable.registerCallback(new XMPPServiceCallback() {

			public void newMessage(String from, String message) {
				if (!mIsBoundTo.contains(from)) {
					logInfo("notification: " + from);
					notifyClient(from, mSmackable.getNameForJID(from), message);
				}
			}

			public void rosterChanged() {
				postRosterChanged();
			}

			public boolean isBoundTo(String jabberID) {
				return mIsBoundTo.contains(jabberID);
			}
		});
	}

	private class ReconnectAlarmReceiver extends BroadcastReceiver {
		public void onReceive(Context ctx, Intent i) {
			logInfo("Alarm received.");
			if (!mConnectionDemanded.get()) {
				return;
			}
			if (mIsConnected.get()) {
				logError("Reconnect attempt aborted: we are connected again!");
				return;
			}
			doConnect();
		}
	}
}
