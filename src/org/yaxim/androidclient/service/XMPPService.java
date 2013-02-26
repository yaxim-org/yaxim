package org.yaxim.androidclient.service;

import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;

import org.yaxim.androidclient.IXMPPRosterCallback;
import org.yaxim.androidclient.MainWindow;
import org.yaxim.androidclient.R;
import org.yaxim.androidclient.exceptions.YaximXMPPException;
import org.yaxim.androidclient.util.ConnectionState;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
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
	private boolean create_account = false;
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
			startService(xmppServiceIntent);
		}

		mServiceNotification = ServiceNotification.getInstance();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		((AlarmManager)getSystemService(Context.ALARM_SERVICE)).cancel(mPAlarmIntent);
		mRosterCallbacks.kill();
		performDisconnect();
		unregisterReceiver(mAlarmReceiver);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		logInfo("onStartCommand(), mConnectionDemanded=" + mConnectionDemanded.get());
		if (intent != null) {
			create_account = intent.getBooleanExtra("create_account", false);
			
			if ("disconnect".equals(intent.getAction())) {
				if (mConnectingThread != null || mIsConnected.get())
					connectionFailed(getString(R.string.conn_networkchg));
				return START_STICKY;
			} else
			if ("reconnect".equals(intent.getAction())) {
				// reset reconnection timeout
				mReconnectTimeout = RECONNECT_AFTER;
				doConnect();
				return START_STICKY;
			} else
			if ("ping".equals(intent.getAction())) {
				if (mSmackable != null && mSmackable.isAuthenticated())
					mSmackable.sendServerPing();
				return START_STICKY;
			}
		}
		
		mConnectionDemanded.set(mConfig.autoConnect);
		doConnect();
		return START_STICKY;
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
				} else if (mConnectionDemanded.get() &&
						networkConnectedOrConnecting()) {
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
				manualDisconnect();
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
					if (!mSmackable.doConnect(create_account)) {
						postConnectionFailed("Inconsistency in Smackable.doConnect()");
					} else
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
					create_account = false;
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

	private NetworkInfo getNetworkInfo() {
		Context ctx = getApplicationContext();
		ConnectivityManager connMgr =
				(ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
		return connMgr.getActiveNetworkInfo();
	}
	private boolean networkConnected() {
		NetworkInfo info = getNetworkInfo();

		return info != null && info.isConnected();
	}
	private boolean networkConnectedOrConnecting() {
		NetworkInfo info = getNetworkInfo();

		return info != null && info.isConnectedOrConnecting();
	}

	private void connectionFailed(String reason) {
		logInfo("connectionFailed: " + reason);
		mLastConnectionError = reason;
		mIsConnected.set(false);
		broadcastConnectionStatus(false, mConnectionDemanded.get());
		if (!networkConnected()) {
			mLastConnectionError = null;
			mReconnectInfo = "";
			updateServiceNotification();
			if (mSmackable != null) {
				mSmackable.unRegisterCallback();
				mSmackable = null;
			}
			YaximBroadcastReceiver.initNetworkStatus(getApplicationContext());
		} else if (mConnectionDemanded.get()) {
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
		if (mIsConnected.get() && mSmackable != null && !mSmackable.isAuthenticated()) {
			logInfo("rosterChanged(): disconnected without warning");
			connectionFailed(getString(R.string.conn_disconnected));
		}
	}

	public void manualDisconnect() {
		mConnectionDemanded.set(false);
		performDisconnect();
	}

	public void performDisconnect() {
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
				logInfo("notification: " + from);
				notifyClient(from, mSmackable.getNameForJID(from), message, !mIsBoundTo.contains(from));
			}

			public void rosterChanged() {
				postRosterChanged();
			}

			public void disconnectOnError() {
				logInfo("Smackable disconnected on error");
				postConnectionFailed(getString(R.string.conn_disconnected));
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
