package org.yaxim.androidclient.service;

import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.yaxim.androidclient.FileHttpUploadTask;
import org.yaxim.androidclient.IXMPPRosterCallback;
import org.yaxim.androidclient.MainWindow;
import org.yaxim.androidclient.R;
import org.yaxim.androidclient.YaximApplication;
import org.yaxim.androidclient.data.RosterProvider;
import org.yaxim.androidclient.exceptions.YaximXMPPException;
import org.yaxim.androidclient.util.ConnectionState;
import org.yaxim.androidclient.util.StatusMode;

import org.jivesoftware.smack.packet.Message.Type;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.Uri.Builder;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

public class XMPPService extends GenericService {

	private static final String TAG="yaxim.XMPPService";
	
	private AtomicBoolean mConnectionDemanded = new AtomicBoolean(false); // should we try to reconnect?
	private static final int RECONNECT_AFTER = 5;
	private static final int RECONNECT_MAXIMUM = 10*60;
	private static final String RECONNECT_ALARM = "org.yaxim.androidclient.RECONNECT_ALARM";
	private int mReconnectTimeout = RECONNECT_AFTER;
	private String mReconnectInfo = "";
	private Intent mAlarmIntent = new Intent(RECONNECT_ALARM);
	private PendingIntent mPAlarmIntent;
	private BroadcastReceiver mAlarmReceiver = new ReconnectAlarmReceiver();
	private BroadcastReceiver mRingerModeReceiver = new RingerModeReceiver();

	private Smackable mSmackable;
	private boolean create_account = false;
	private IXMPPRosterService.Stub mService2RosterConnection;
	private IXMPPChatService.Stub mServiceChatConnection;
	private IXMPPMucService.Stub mServiceMucConnection;

	private RemoteCallbackList<IXMPPRosterCallback> mRosterCallbacks = new RemoteCallbackList<IXMPPRosterCallback>();
	private HashSet<String> mIsBoundTo = new HashSet<String>();
	private Handler mMainHandler = new Handler();

	@Override
	public IBinder onBind(Intent intent) {
		userStartedWatching();

		String chatPartner = intent.getDataString();
		if(chatPartner != null && chatPartner.endsWith("?chat")) {
			return mServiceMucConnection;
		} else if (chatPartner != null) {
			resetNotificationCounter(chatPartner);
			mIsBoundTo.add(chatPartner);
			return mServiceChatConnection;
		}
		return mService2RosterConnection;
	}

	@Override
	public void onRebind(Intent intent) {
		userStartedWatching();
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
		userStoppedWatching();

		return true;
	}

	@Override
	public void onCreate() {
		super.onCreate();

		createServiceRosterStub();
		createServiceChatStub();
		createServiceMucStub();

		mPAlarmIntent = PendingIntent.getBroadcast(this, 0, mAlarmIntent,
					PendingIntent.FLAG_UPDATE_CURRENT);
		registerReceiver(mAlarmReceiver, new IntentFilter(RECONNECT_ALARM));
		registerReceiver(mRingerModeReceiver, new IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION));
		configureSmartAwayMode();

		createAdapter();
		YaximBroadcastReceiver.initNetworkStatus(getApplicationContext());

		if (mConfig.autoConnect && mConfig.jid_configured) {
			/*
			 * start our own service so it remains in background even when
			 * unbound
			 */
			Intent xmppServiceIntent = new Intent(this, XMPPService.class);
			startService(xmppServiceIntent);
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		((AlarmManager)getSystemService(Context.ALARM_SERVICE)).cancel(mPAlarmIntent);
		mRosterCallbacks.kill();
		if (mSmackable != null) {
		    manualDisconnect();
		    mSmackable.unRegisterCallback();
		}
		unregisterReceiver(mAlarmReceiver);
		unregisterReceiver(mRingerModeReceiver);
		YaximBroadcastReceiver.getInstance().untrackConnectivity(this);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		logInfo("onStartCommand(), mConnectionDemanded=" + mConnectionDemanded.get());
		logInfo("    intent=" + intent);
		if (intent != null) {
			create_account = intent.getBooleanExtra("create_account", false);
			
			if ("disconnect".equals(intent.getAction())) {
				failConnection(getString(R.string.conn_no_network));
				return START_STICKY;
			} else
			if ("reconnect".equals(intent.getAction())) {
				// TODO: integrate the following steps into one "RECONNECT"
				failConnection(getString(R.string.conn_no_network));
				// reset reconnection timeout
				mReconnectTimeout = RECONNECT_AFTER;
				doConnect();
				return START_STICKY;
			} else
			if ("ping".equals(intent.getAction())) {
				if (mSmackable != null && mSmackable.isAuthenticated()) {
					mSmackable.sendServerPing();
					return START_STICKY;
				}
				// if not authenticated, fall through to doConnect()
			} else
			if ("respond".equals(intent.getAction())) {
				// clear notifications and send a message from Android Auto/Wear event
				String jid = intent.getDataString();
				String replystring = intent.getStringExtra("message");
				if (replystring != null) {
					Log.d(TAG, "got reply: " + replystring);
					mSmackable.sendMessage(jid, replystring, null, null, -1);
				}
				org.yaxim.androidclient.data.ChatHelper.markAsRead(this, jid);
				clearNotification(jid);
				return START_STICKY;
			}
		}
		
		mConnectionDemanded.set(mConfig.autoConnect);
		YaximBroadcastReceiver.getInstance().trackConnectivity(this);
		doConnect();
		return START_STICKY;
	}

	private void createServiceChatStub() {
		mServiceChatConnection = new IXMPPChatService.Stub() {

			public void sendMessage(String user, String message, String lmc, long upsert_id)
					throws RemoteException {
				if (mSmackable != null)
					mSmackable.sendMessage(user, message, lmc, null, upsert_id);
				else
					SmackableImp.addOfflineMessage(getContentResolver(),
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

			@Override
			public boolean hasFileUpload() throws RemoteException {
				if (mSmackable != null) {
					return mSmackable.hasFileUpload();
				}
				return false;
			}

			public void sendFile(Uri path, String user, int flags) throws RemoteException {
				if (mSmackable != null)
					new FileHttpUploadTask(XMPPService.this, mConfig, mSmackable, path, user, flags).execute();
			}
		};
	}
	
	private void createServiceMucStub() {
		mServiceMucConnection = new IXMPPMucService.Stub() {
			private void fail(String error) {
				Toast toast = Toast.makeText(getApplicationContext(), 
						error, Toast.LENGTH_LONG);
				toast.show();
			}
			@Override
			public void syncDbRooms() throws RemoteException {
				if(mSmackable!=null)
					new Thread() {
						@Override
						public void run() {
							mSmackable.syncDbRooms();
						}
					}.start();
			}
			@Override
			public boolean inviteToRoom(String contactJid, String roomJid) {
				if(mSmackable!=null)
					return mSmackable.inviteToRoom(contactJid, roomJid);
				else {
					shortToastNotify(getString(R.string.Global_authenticate_first));
					return false;
				}
			}
			@Override
			public String getMyMucNick(String jid) throws RemoteException {
				if(mSmackable!=null)
					return mSmackable.getMyMucNick(jid);
				return null;
			}
			@Override
			public List<ParcelablePresence> getUserList(String jid) throws RemoteException {
				if(mSmackable!=null)
					return mSmackable.getUserList(jid);
				else {
					shortToastNotify(getString(R.string.Global_authenticate_first));
					return null;
				}
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
				if (mSmackable != null) {
					return mSmackable.getConnectionState().ordinal();
				} else {
					return ConnectionState.OFFLINE.ordinal();
				}
			}

			public String getConnectionStateString() throws RemoteException {
				return XMPPService.this.getConnectionStateString();
			}


			public void setStatusFromConfig()
					throws RemoteException {
				if (mSmackable != null) { // this should always be true, but stil...
					mSmackable.setStatusFromConfig();
					updateServiceNotification();
				}
			}

			public void addRosterItem(String user, String alias, String group, String token)
					throws RemoteException {
				try {
					mSmackable.addRosterItem(user, alias, group, token);
				} catch (YaximXMPPException e) {
					shortToastNotify(e);
				}
			}

			public void addRosterGroup(String group) throws RemoteException {
				mSmackable.addRosterGroup(group);
			}

			public void removeRosterItem(String user) throws RemoteException {
				try {
					mSmackable.removeRosterItem(user);
				} catch (YaximXMPPException e) {
					shortToastNotify(e);
				}
			}

			public void moveRosterItemToGroup(String user, String group)
					throws RemoteException {
				try {
					mSmackable.moveRosterItemToGroup(user, group);
				} catch (YaximXMPPException e) {
					shortToastNotify(e);
				}
			}

			public void renameRosterItem(String user, String newName)
					throws RemoteException {
				try {
					mSmackable.renameRosterItem(user, newName);
				} catch (YaximXMPPException e) {
					shortToastNotify(e);
				}
			}

			public void renameRosterGroup(String group, String newGroup)
					throws RemoteException {
				try {
					mSmackable.renameRosterGroup(group, newGroup);
				} catch (YaximXMPPException e) {
					shortToastNotify(e);
				}
			}

			@Override
			public String changePassword(String newPassword)
					throws RemoteException {
				return mSmackable.changePassword(newPassword);
			}

			public void disconnect() throws RemoteException {
				manualDisconnect();
			}

			public void connect() throws RemoteException {
				mConnectionDemanded.set(true);
				mReconnectTimeout = RECONNECT_AFTER;
				doConnect();
			}

			public void sendPresenceRequest(String jid, String type)
					throws RemoteException {
				try {
					mSmackable.sendPresenceRequest(jid, type);
				} catch (YaximXMPPException e) {
					shortToastNotify(e);
				}
			}

		};
	}

	private String getConnectionStateString() {
		StringBuilder sb = new StringBuilder();
		sb.append(mReconnectInfo);
		if (mSmackable != null && mSmackable.getLastError() != null) {
			sb.append("\n");
			sb.append(mSmackable.getLastError());
		}
		return sb.toString();
	}

	public String getStatusTitle(ConnectionState cs) {
		if (cs != ConnectionState.ONLINE)
			return mReconnectInfo;
		String status = getString(mConfig.getPresenceMode().getTextId());

		if (mConfig.statusMessage.length() > 0) {
			status = status + " (" + mConfig.statusMessage + ")";
		}

		return status;
	}

	private void updateServiceNotification(ConnectionState cs) {
		// HACK to trigger show-offline when XEP-0198 reconnect is going on
		getContentResolver().notifyChange(RosterProvider.CONTENT_URI, null);
		getContentResolver().notifyChange(RosterProvider.GROUPS_URI, null);
		// end-of-HACK

		Log.d(TAG, "updateServiceNotification: " + cs);
		broadcastConnectionState(cs);

		// do not show notification if not a foreground service
		if (!mConfig.foregroundService)
			return;

		if (cs == ConnectionState.OFFLINE) {
			stopForeground(true);
			return;
		}

		Intent notificationIntent = new Intent(this, MainWindow.class);
		notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

		Notification n = new NotificationCompat.Builder(this)
			.setSmallIcon((cs == ConnectionState.ONLINE) ? R.drawable.ic_online : R.drawable.ic_offline)
			.setLargeIcon(android.graphics.BitmapFactory.decodeResource(getResources(), R.drawable.icon))
			.setWhen(mSmackable.getConnectionStateTimestamp())
			.setOngoing(true)
			.setOnlyAlertOnce(true)
			.setContentIntent(PendingIntent.getActivity(this, 0, notificationIntent,
				PendingIntent.FLAG_UPDATE_CURRENT))
			.setContentTitle(getString(R.string.conn_title, mConfig.jabberID))
			.setContentText(getStatusTitle(cs))
			.build();


		startForeground(SERVICE_NOTIFICATION, n);
	}
	private void updateServiceNotification() {
		ConnectionState cs = ConnectionState.OFFLINE;
		if (mSmackable != null) {
			cs = mSmackable.getConnectionState();
		}
	}

	private void doConnect() {
		mReconnectInfo = getString(R.string.conn_connecting);
		updateServiceNotification();
		if (mSmackable == null) {
			createAdapter();
		}

		mSmackable.requestConnectionState(ConnectionState.ONLINE, create_account);
		create_account = false;
	}

	private synchronized void broadcastConnectionState(ConnectionState cs) {
		final int broadCastItems = mRosterCallbacks.beginBroadcast();

		for (int i = 0; i < broadCastItems; i++) {
			try {
				mRosterCallbacks.getBroadcastItem(i).connectionStateChanged(cs.ordinal());
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

	// call this when Android tells us to shut down
	private void failConnection(String reason) {
		logInfo("failConnection: " + reason);
		mReconnectInfo = reason;
		updateServiceNotification();
		if (mSmackable != null)
			mSmackable.requestConnectionState(ConnectionState.DISCONNECTED);
	}

	// called from Smackable when connection broke down
	private void connectionFailed(String reason) {
		logInfo("connectionFailed: " + reason);
		//TODO: error message from downstream?
		//mLastConnectionError = reason;
		if (!networkConnected()) {
			mReconnectInfo = getString(R.string.conn_no_network);
			mSmackable.requestConnectionState(ConnectionState.RECONNECT_NETWORK);

		} else if (mConnectionDemanded.get()) {
			if (reason.contains("SASLError using")) {
				mConnectionDemanded.set(false);
				mConfig.jid_configured = false;
				mSmackable.requestConnectionState(ConnectionState.OFFLINE);
				return;
			}
			mReconnectInfo = getString(R.string.conn_reconnect, mReconnectTimeout);
			mSmackable.requestConnectionState(ConnectionState.RECONNECT_DELAYED);
			logInfo("connectionFailed(): registering reconnect in " + mReconnectTimeout + "s");
			((AlarmManager)getSystemService(Context.ALARM_SERVICE)).set(AlarmManager.RTC_WAKEUP,
					System.currentTimeMillis() + mReconnectTimeout * 1000, mPAlarmIntent);
			mReconnectTimeout = mReconnectTimeout * 2;
			if (mReconnectTimeout > RECONNECT_MAXIMUM)
				mReconnectTimeout = RECONNECT_MAXIMUM;
		} else {
			connectionClosed();
		}

	}

	private void connectionClosed() {
		logInfo("connectionClosed.");
		mReconnectInfo = "";
		stopForeground(true);
		mSmackable.requestConnectionState(ConnectionState.OFFLINE);
	}

	public void manualDisconnect() {
		mConnectionDemanded.set(false);
		mReconnectInfo = getString(R.string.conn_disconnecting);
		performDisconnect();
	}

	public void performDisconnect() {
		if (mSmackable != null) {
			// this is non-blocking
			mSmackable.requestConnectionState(ConnectionState.OFFLINE);
		}
	}

	private void createAdapter() {
		System.setProperty("smack.debugEnabled", "" + mConfig.smackdebug);
		try {
			mSmackable = new SmackableImp(mConfig, getContentResolver(), this);
			YaximApplication.getApp().setSmackable(mSmackable);
		} catch (NullPointerException e) {
			e.printStackTrace();
		}

		mSmackable.registerCallback(new XMPPServiceCallback() {
			public void notifyMessage(final String[] from, final String message,
					final boolean silent_notification, final Type msgType) {
				final String name = mSmackable.getNameForJID(from[0]);
				logInfo("notification: " + from[0] + "=" + name +" with type: "+msgType.name());
				mMainHandler.post(new Runnable() {
					public void run() {
						// work around Toast fallback for errors
						notifyClient(from, name, message,
							!mIsBoundTo.contains(from[0]), silent_notification, msgType);
					}});
				}

			@Override
			public void setGracePeriod(boolean silence) {
				XMPPService.this.setGracePeriod(silence);
			}

			public void connectionStateChanged(ConnectionState connection_state) {
				// TODO: OFFLINE is sometimes caused by XMPPConnection calling
				// connectionClosed() callback on an error, need to catch that?
				switch (connection_state) {
				case LOADING:
					mReconnectInfo = getString(R.string.muc_synchronizing);
					break;
				//case OFFLINE:
				case DISCONNECTED:
					connectionFailed(getString(R.string.conn_disconnected));
					break;
				case ONLINE:
					mReconnectTimeout = RECONNECT_AFTER;
				default:
				}
				updateServiceNotification(connection_state);
			}

			@Override
			public void mucInvitationReceived(String roomname, String room, String password, String invite_from, String roomdescription) {
				String body = invite_from + ": " + roomname + "\n" + roomdescription;
				Log.d(TAG, "Notifying MUC invitation for " + room + ". " + body);
				Intent intent = new Intent(getApplicationContext(), MainWindow.class);
				intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				intent.setAction("android.intent.action.VIEW");
				String uri = "xmpp:" + java.net.URLEncoder.encode(room);
				Builder b = new Builder();
				b.appendQueryParameter("join", null);
				if (password != null)
					b.appendQueryParameter("password", password);
				b.appendQueryParameter("body", body);
				intent.setData(Uri.parse(uri + b.toString()));
				PendingIntent pi = PendingIntent.getActivity(getApplicationContext(), 0, 
						intent, 0);
				Notification invNotify = new NotificationCompat.Builder(getApplicationContext())
						 .setContentTitle(roomname)
						 .setContentText(body)
						 .setSmallIcon(R.drawable.ic_action_contacts_online)
						 .setTicker(invite_from + ": " + roomname)
						 .setStyle(new NotificationCompat.BigTextStyle()
								 .bigText(roomdescription)
								 .setSummaryText(invite_from)
								 .setBigContentTitle(roomname))
						 .setContentIntent(pi)
						 .setAutoCancel(true)
						 .build();
				int notifyId;
				if (notificationId.containsKey(room)) {
					notifyId = notificationId.get(room);
				} else {
					lastNotificationId++;
					notifyId = lastNotificationId;
					notificationId.put(room, Integer.valueOf(notifyId));
				}
				mNotificationMGR.notify(notifyId, invNotify);
			}
		});
	}

	private class ReconnectAlarmReceiver extends BroadcastReceiver {
		public void onReceive(Context ctx, Intent i) {
			logInfo("Alarm received.");
			if (!mConnectionDemanded.get()) {
				return;
			}
			if (mSmackable != null && mSmackable.getConnectionState() == ConnectionState.ONLINE) {
				logError("Reconnect attempt aborted: we are connected again!");
				return;
			}
			doConnect();
		}
	}

	private int number_of_eyes = 0;
	private void userStartedWatching() {
		number_of_eyes += 1;
		logInfo("userStartedWatching: " + number_of_eyes);
		if (mSmackable != null)
			mSmackable.setUserWatching(true);
		setGracePeriod(false);
	}

	private void userStoppedWatching() {
		number_of_eyes -= 1;
		logInfo("userStoppedWatching: " + number_of_eyes);
		// delay deactivation by 3s, in case we happen to be immediately re-bound
		mMainHandler.postDelayed(new Runnable() {
			public void run() {
				if (mSmackable != null && number_of_eyes == 0)
					mSmackable.setUserWatching(false);
			}}, 3000);
	}

	private void configureSmartAwayMode() {
		AudioManager am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		boolean is_silent = (am.getRingerMode() == AudioManager.RINGER_MODE_SILENT);
		mConfig.smartAwayMode = is_silent ? StatusMode.dnd : null;
		logInfo("configureSmartAwayMode: " + mConfig.smartAwayMode);
	}

	private class RingerModeReceiver extends BroadcastReceiver {
		public void onReceive(Context ctx, Intent i) {
			logInfo("Ringer mode changed: " + i);
			configureSmartAwayMode();
			if (mSmackable != null && mSmackable.isAuthenticated()) {
				mSmackable.setStatusFromConfig();
				updateServiceNotification();
			}
		}
	}
}
