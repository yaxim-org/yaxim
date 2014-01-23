package org.yaxim.androidclient.service;

import java.io.File;
import java.util.Collection;
import java.util.Date;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

import org.jivesoftware.smack.AccountManager;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.RosterEntry;
import org.jivesoftware.smack.RosterGroup;
import org.jivesoftware.smack.RosterListener;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.IQ.Type;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Presence.Mode;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.util.DNSUtil;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smack.util.dns.DNSJavaResolver;
import org.jivesoftware.smackx.entitycaps.EntityCapsManager;
import org.jivesoftware.smackx.entitycaps.cache.SimpleDirectoryPersistentCache;
import org.jivesoftware.smackx.ServiceDiscoveryManager;
import org.jivesoftware.smackx.carbons.Carbon;
import org.jivesoftware.smackx.carbons.CarbonManager;
import org.jivesoftware.smackx.entitycaps.provider.CapsExtensionProvider;
import org.jivesoftware.smackx.forward.Forwarded;
import org.jivesoftware.smackx.provider.DelayInfoProvider;
import org.jivesoftware.smackx.provider.DiscoverInfoProvider;
import org.jivesoftware.smackx.provider.DiscoverItemsProvider;
import org.jivesoftware.smackx.packet.DelayInformation;
import org.jivesoftware.smackx.packet.DelayInfo;
import org.jivesoftware.smackx.packet.DiscoverInfo;
import org.jivesoftware.smackx.packet.Version;
import org.jivesoftware.smackx.ping.PingManager;
import org.jivesoftware.smackx.ping.packet.*;
import org.jivesoftware.smackx.ping.provider.PingProvider;
import org.jivesoftware.smackx.receipts.DeliveryReceipt;
import org.jivesoftware.smackx.receipts.DeliveryReceiptManager;
import org.jivesoftware.smackx.receipts.DeliveryReceiptRequest;
import org.jivesoftware.smackx.receipts.ReceiptReceivedListener;
import org.yaxim.androidclient.YaximApplication;
import org.yaxim.androidclient.data.ChatProvider;
import org.yaxim.androidclient.data.RosterProvider;
import org.yaxim.androidclient.data.YaximConfiguration;
import org.yaxim.androidclient.data.ChatProvider.ChatConstants;
import org.yaxim.androidclient.data.RosterProvider.RosterConstants;
import org.yaxim.androidclient.exceptions.YaximXMPPException;
import org.yaxim.androidclient.util.ConnectionState;
import org.yaxim.androidclient.util.LogConstants;
import org.yaxim.androidclient.util.PreferenceConstants;
import org.yaxim.androidclient.util.StatusMode;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;

import android.net.Uri;
import android.util.Log;

public class SmackableImp implements Smackable {
	final static private String TAG = "yaxim.SmackableImp";

	final static private int PACKET_TIMEOUT = 30000;

	final static private String[] SEND_OFFLINE_PROJECTION = new String[] {
			ChatConstants._ID, ChatConstants.JID,
			ChatConstants.MESSAGE, ChatConstants.DATE, ChatConstants.PACKET_ID };
	final static private String SEND_OFFLINE_SELECTION =
			ChatConstants.DIRECTION + " = " + ChatConstants.OUTGOING + " AND " +
			ChatConstants.DELIVERY_STATUS + " = " + ChatConstants.DS_NEW;

	static final DiscoverInfo.Identity YAXIM_IDENTITY = new DiscoverInfo.Identity("client",
					YaximApplication.XMPP_IDENTITY_NAME,
					YaximApplication.XMPP_IDENTITY_TYPE);

	static File capsCacheDir = null; ///< this is used to cache if we already initialized EntityCapsCache

	static {
		registerSmackProviders();
		DNSUtil.setDNSResolver(DNSJavaResolver.getInstance());
	}

	static void registerSmackProviders() {
		ProviderManager pm = ProviderManager.getInstance();
		// add IQ handling
		pm.addIQProvider("query","http://jabber.org/protocol/disco#info", new DiscoverInfoProvider());
		pm.addIQProvider("query","http://jabber.org/protocol/disco#items", new DiscoverItemsProvider());
		// add delayed delivery notifications
		pm.addExtensionProvider("delay","urn:xmpp:delay", new DelayInfoProvider());
		pm.addExtensionProvider("x","jabber:x:delay", new DelayInfoProvider());
		// add XEP-0092 Software Version
		pm.addIQProvider("query", Version.NAMESPACE, new Version.Provider());

		// add carbons and forwarding
		pm.addExtensionProvider("forwarded", Forwarded.NAMESPACE, new Forwarded.Provider());
		pm.addExtensionProvider("sent", Carbon.NAMESPACE, new Carbon.Provider());
		pm.addExtensionProvider("received", Carbon.NAMESPACE, new Carbon.Provider());
		// add delivery receipts
		pm.addExtensionProvider(DeliveryReceipt.ELEMENT, DeliveryReceipt.NAMESPACE, new DeliveryReceipt.Provider());
		pm.addExtensionProvider(DeliveryReceiptRequest.ELEMENT, DeliveryReceipt.NAMESPACE, new DeliveryReceiptRequest.Provider());
		// add XMPP Ping (XEP-0199)
		pm.addIQProvider("ping","urn:xmpp:ping", new PingProvider());

		ServiceDiscoveryManager.setDefaultIdentity(YAXIM_IDENTITY);
		
		// XEP-0115 Entity Capabilities
		pm.addExtensionProvider("c", "http://jabber.org/protocol/caps", new CapsExtensionProvider());

		XmppStreamHandler.addExtensionProviders();
	}

	private final YaximConfiguration mConfig;
	private ConnectionConfiguration mXMPPConfig;
	private XmppStreamHandler.ExtXMPPConnection mXMPPConnection;
	private XmppStreamHandler mStreamHandler;
	private Thread mConnectingThread;
	private Object mConnectingThreadMutex = new Object();


	private ConnectionState mRequestedState = ConnectionState.OFFLINE;
	private ConnectionState mState = ConnectionState.OFFLINE;
	private String mLastError;
	
	private XMPPServiceCallback mServiceCallBack;
	private Roster mRoster;
	private RosterListener mRosterListener;
	private PacketListener mPacketListener;
	private PacketListener mPresenceListener;
	private ConnectionListener mConnectionListener;

	private final ContentResolver mContentResolver;

	private PacketListener mPongListener;
	private String mPingID;
	private long mPingTimestamp;

	private PendingIntent mPingAlarmPendIntent;
	private PendingIntent mPongTimeoutAlarmPendIntent;
	private static final String PING_ALARM = "org.yaxim.androidclient.PING_ALARM";
	private static final String PONG_TIMEOUT_ALARM = "org.yaxim.androidclient.PONG_TIMEOUT_ALARM";
	private Intent mPingAlarmIntent = new Intent(PING_ALARM);
	private Intent mPongTimeoutAlarmIntent = new Intent(PONG_TIMEOUT_ALARM);
	private Service mService;

	private PongTimeoutAlarmReceiver mPongTimeoutAlarmReceiver = new PongTimeoutAlarmReceiver();
	private BroadcastReceiver mPingAlarmReceiver = new PingAlarmReceiver();


	public SmackableImp(YaximConfiguration config,
			ContentResolver contentResolver,
			Service service) {
		this.mConfig = config;
		this.mContentResolver = contentResolver;
		this.mService = service;
	}
		
	// this code runs a DNS resolver, might be blocking
	private synchronized void initXMPPConnection() {
		// allow custom server / custom port to override SRV record
		if (mConfig.customServer.length() > 0 || mConfig.port != PreferenceConstants.DEFAULT_PORT_INT)
			mXMPPConfig = new ConnectionConfiguration(mConfig.customServer,
					mConfig.port, mConfig.server);
		else
			mXMPPConfig = new ConnectionConfiguration(mConfig.server); // use SRV
		mXMPPConfig.setReconnectionAllowed(false);
		mXMPPConfig.setSendPresence(false);
		mXMPPConfig.setCompressionEnabled(false); // disable for now
		mXMPPConfig.setDebuggerEnabled(mConfig.smackdebug);
		if (mConfig.require_ssl)
			this.mXMPPConfig.setSecurityMode(ConnectionConfiguration.SecurityMode.required);

		// register MemorizingTrustManager for HTTPS
		try {
			SSLContext sc = SSLContext.getInstance("TLS");
			sc.init(null, new X509TrustManager[] { YaximApplication.getApp(mService).mMTM },
					new java.security.SecureRandom());
			this.mXMPPConfig.setCustomSSLContext(sc);
		} catch (java.security.GeneralSecurityException e) {
			debugLog("initialize MemorizingTrustManager: " + e);
		}

		this.mXMPPConnection = new XmppStreamHandler.ExtXMPPConnection(mXMPPConfig);
		this.mStreamHandler = new XmppStreamHandler(mXMPPConnection, mConfig.smackdebug);
		mStreamHandler.addAckReceivedListener(new XmppStreamHandler.AckReceivedListener() {
			public void ackReceived(long handled, long total) {
				gotServerPong("" + handled);
			}
		});
		mConfig.reconnect_required = false;

		initServiceDiscovery();
	}

	// blocking, run from a thread!
	public boolean doConnect(boolean create_account) throws YaximXMPPException {
		mRequestedState = ConnectionState.ONLINE;
		updateConnectionState(ConnectionState.CONNECTING);
		if (mXMPPConnection == null || mConfig.reconnect_required)
			initXMPPConnection();
		tryToConnect(create_account);
		// actually, authenticated must be true now, or an exception must have
		// been thrown.
		if (isAuthenticated()) {
			registerMessageListener();
			registerPresenceListener();
			registerPongListener();
			sendOfflineMessages();
			// we need to "ping" the service to let it know we are actually
			// connected, even when no roster entries will come in
			updateConnectionState(ConnectionState.ONLINE);
		} else throw new YaximXMPPException("SMACK connected, but authentication failed");
		return true;
	}

	// BLOCKING, call on a new Thread!
	private void updateConnectingThread(Thread new_thread) {
		synchronized(mConnectingThreadMutex) {
			if (mConnectingThread == null) {
				mConnectingThread = new_thread;
			} else try {
				Log.d(TAG, "updateConnectingThread: old thread is still running, killing it.");
				mConnectingThread.interrupt();
				mConnectingThread.join(50);
			} catch (InterruptedException e) {
				Log.d(TAG, "updateConnectingThread: failed to join(): " + e);
			} finally {
				mConnectingThread = new_thread;
			}
		}
	}
	private void finishConnectingThread() {
		synchronized(mConnectingThreadMutex) {
			mConnectingThread = null;
		}
	}

	/** Non-blocking, synchronized function to connect/disconnect XMPP.
	 * This code is called from outside and returns immediately. The actual work
	 * is done on a background thread, and notified via callback.
	 * @param new_state The state to transition into. Possible values:
	 * 	OFFLINE to properly close the connection
	 * 	ONLINE to connect
	 * 	DISCONNECTED when network goes down
	 * @param create_account When going online, try to register an account.
	 */
	@Override
	public synchronized void requestConnectionState(ConnectionState new_state, final boolean create_account) {
		Log.d(TAG, "requestConnState: " + mState + " -> " + new_state + (create_account ? " create_account!" : ""));
		mRequestedState = new_state;
		if (new_state == mState)
			return;
		switch (new_state) {
		case ONLINE:
			switch (mState) {
			case RECONNECT_DELAYED:
				// TODO: cancel timer
			case RECONNECT_NETWORK:
			case OFFLINE:
				// update state before starting thread to prevent race conditions
				updateConnectionState(ConnectionState.CONNECTING);
				new Thread() {
					@Override
					public void run() {
						updateConnectingThread(this);
						try {
							doConnect(create_account);
						} catch (IllegalArgumentException e) {
							// this might happen when DNS resolution in ConnectionConfiguration fails
							onDisconnected(e);
						} catch (YaximXMPPException e) {
							onDisconnected(e);
						} finally {
							finishConnectingThread();
						}
					}
				}.start();
				break;
			case CONNECTING:
			case DISCONNECTING:
				// ignore all other cases
				break;
			}
			break;
		case DISCONNECTED:
			// spawn thread to do disconnect
			if (mState == ConnectionState.ONLINE) {
				// update state before starting thread to prevent race conditions
				updateConnectionState(ConnectionState.DISCONNECTING);
				new Thread() {
					public void run() {
						updateConnectingThread(this);
						mXMPPConnection.quickShutdown();
						finishConnectingThread();
						//updateConnectionState(ConnectionState.OFFLINE);
					}
				}.start();
			}
			break;
		case OFFLINE:
			switch (mState) {
			case CONNECTING:
			case ONLINE:
				// update state before starting thread to prevent race conditions
				updateConnectionState(ConnectionState.DISCONNECTING);
				// spawn thread to do disconnect
				new Thread() {
					public void run() {
						updateConnectingThread(this);
						mXMPPConnection.shutdown();
						mStreamHandler.close();
						finishConnectingThread();
						// reconnect if it was requested in the meantime
						if (mRequestedState == ConnectionState.ONLINE)
							requestConnectionState(ConnectionState.ONLINE);
					}
				}.start();
				break;
			case DISCONNECTING:
				break;
			case RECONNECT_DELAYED:
				// TODO: clear timer
			case RECONNECT_NETWORK:
				updateConnectionState(ConnectionState.OFFLINE);
			}
			break;
		case RECONNECT_NETWORK:
		case RECONNECT_DELAYED:
			switch (mState) {
			case DISCONNECTED:
			case RECONNECT_NETWORK:
			case RECONNECT_DELAYED:
				updateConnectionState(new_state);
				break;
			default:
				throw new IllegalArgumentException("Can not go from " + mState + " to " + new_state);
			}
		}
	}
	@Override
	public void requestConnectionState(ConnectionState new_state) {
		requestConnectionState(new_state, false);
	}

	@Override
	public ConnectionState getConnectionState() {
		return mState;
	}

	// called at the end of a state transition
	private synchronized void updateConnectionState(ConnectionState new_state) {
		if (new_state == ConnectionState.ONLINE || new_state == ConnectionState.CONNECTING)
			mLastError = null;
		Log.d(TAG, "updateConnectionState: " + mState + " -> " + new_state + " (" + mLastError + ")");
		if (new_state == mState)
			return;
		mState = new_state;
		if (mServiceCallBack != null)
			mServiceCallBack.connectionStateChanged();
	}
	private void initServiceDiscovery() {
		// register connection features
		ServiceDiscoveryManager sdm = ServiceDiscoveryManager.getInstanceFor(mXMPPConnection);

		// init Entity Caps manager with storage in app's cache dir
		try {
			if (capsCacheDir == null) {
				capsCacheDir = new File(mService.getCacheDir(), "entity-caps-cache");
				capsCacheDir.mkdirs();
				EntityCapsManager.setPersistentCache(new SimpleDirectoryPersistentCache(capsCacheDir));
			}
		} catch (java.io.IOException e) {
			Log.e(TAG, "Could not init Entity Caps cache: " + e.getLocalizedMessage());
		}

		// reference PingManager, set ping flood protection to 10s
		PingManager.getInstanceFor(mXMPPConnection).setPingMinimumInterval(10*1000);

		// set Version for replies
		String app_name = mService.getString(org.yaxim.androidclient.R.string.app_name);
		String build_revision = mService.getString(org.yaxim.androidclient.R.string.build_revision);
		Version.Manager.getInstanceFor(mXMPPConnection).setVersion(
				new Version(app_name, build_revision, "Android"));

		// reference DeliveryReceiptManager, add listener
		DeliveryReceiptManager dm = DeliveryReceiptManager.getInstanceFor(mXMPPConnection);
		dm.enableAutoReceipts();
		dm.addReceiptReceivedListener(new ReceiptReceivedListener() { // DOES NOT WORK IN CARBONS
			public void onReceiptReceived(String fromJid, String toJid, String receiptId) {
				Log.d(TAG, "got delivery receipt for " + receiptId);
				changeMessageDeliveryStatus(receiptId, ChatConstants.DS_ACKED);
			}});
	}

	public void addRosterItem(String user, String alias, String group)
			throws YaximXMPPException {
		tryToAddRosterEntry(user, alias, group);
	}

	public void removeRosterItem(String user) throws YaximXMPPException {
		debugLog("removeRosterItem(" + user + ")");

		tryToRemoveRosterEntry(user);
		mServiceCallBack.rosterChanged();
	}

	public void renameRosterItem(String user, String newName)
			throws YaximXMPPException {
		RosterEntry rosterEntry = mRoster.getEntry(user);

		if (!(newName.length() > 0) || (rosterEntry == null)) {
			throw new YaximXMPPException("JabberID to rename is invalid!");
		}
		rosterEntry.setName(newName);
	}

	public void addRosterGroup(String group) {
		mRoster.createGroup(group);
	}

	public void renameRosterGroup(String group, String newGroup) {
		RosterGroup groupToRename = mRoster.getGroup(group);
		groupToRename.setName(newGroup);
	}

	public void moveRosterItemToGroup(String user, String group)
			throws YaximXMPPException {
		tryToMoveRosterEntryToGroup(user, group);
	}

	public void sendPresenceRequest(String user, String type) {
		// HACK: remove the fake roster entry added by handleIncomingSubscribe()
		if ("unsubscribed".equals(type))
			deleteRosterEntryFromDB(user);
		Presence response = new Presence(Presence.Type.valueOf(type));
		response.setTo(user);
		mXMPPConnection.sendPacket(response);
	}
	
	private void onDisconnected(String reason) {
		unregisterPongListener();
		mLastError = reason;
		updateConnectionState(ConnectionState.DISCONNECTED);
	}
	private void onDisconnected(Throwable reason) {
		Log.e(TAG, "onDisconnected: " + reason);
		reason.printStackTrace();
		// iterate through to the deepest exception
		while (reason.getCause() != null)
			reason = reason.getCause();
		onDisconnected(reason.getLocalizedMessage());
	}

	private void tryToConnect(boolean create_account) throws YaximXMPPException {
		try {
			if (mXMPPConnection.isConnected()) {
				try {
					mXMPPConnection.quickShutdown(); // blocking shutdown prior to re-connection
				} catch (Exception e) {
					debugLog("conn.shutdown() failed: " + e);
				}
			}
			SmackConfiguration.setPacketReplyTimeout(PACKET_TIMEOUT);
			SmackConfiguration.setDefaultPingInterval(0);
			registerRosterListener();
			boolean need_bind = !mStreamHandler.isResumePossible();

			mXMPPConnection.connect(need_bind);
			// the following should not happen as of smack 3.3.1
			if (!mXMPPConnection.isConnected()) {
				throw new YaximXMPPException("SMACK connect failed without exception!");
			}
			if (mConnectionListener != null)
				mXMPPConnection.removeConnectionListener(mConnectionListener);
			mConnectionListener = new ConnectionListener() {
				public void connectionClosedOnError(Exception e) {
					onDisconnected(e);
				}
				public void connectionClosed() {
					// TODO: fix reconnect when we got kicked by the server or SM failed!
					//onDisconnected(null);
					updateConnectionState(ConnectionState.OFFLINE);
				}
				public void reconnectingIn(int seconds) { }
				public void reconnectionFailed(Exception e) { }
				public void reconnectionSuccessful() { }
			};
			mXMPPConnection.addConnectionListener(mConnectionListener);

			// SMACK auto-logins if we were authenticated before
			if (!mXMPPConnection.isAuthenticated()) {
				if (create_account) {
					Log.d(TAG, "creating new server account...");
					AccountManager am = new AccountManager(mXMPPConnection);
					am.createAccount(mConfig.userName, mConfig.password);
				}
				mXMPPConnection.login(mConfig.userName, mConfig.password,
						mConfig.ressource);
			}
			Log.d(TAG, "SM: can resume = " + mStreamHandler.isResumePossible() + " needbind=" + need_bind);
			if (need_bind) {
				mStreamHandler.notifyInitialLogin();
				setStatusFromConfig();
			}

		} catch (XMPPException e) {
			throw new YaximXMPPException(e.getLocalizedMessage(), e.getWrappedThrowable());
		} catch (Exception e) {
			// actually we just care for IllegalState or NullPointer or XMPPEx.
			Log.e(TAG, "tryToConnect(): " + Log.getStackTraceString(e));
			throw new YaximXMPPException(e.getLocalizedMessage(), e.getCause());
		}
	}

	private void tryToMoveRosterEntryToGroup(String userName, String groupName)
			throws YaximXMPPException {

		RosterGroup rosterGroup = getRosterGroup(groupName);
		RosterEntry rosterEntry = mRoster.getEntry(userName);

		removeRosterEntryFromGroups(rosterEntry);

		if (groupName.length() == 0)
			return;
		else {
			try {
				rosterGroup.addEntry(rosterEntry);
			} catch (XMPPException e) {
				throw new YaximXMPPException(e.getLocalizedMessage());
			}
		}
	}

	private RosterGroup getRosterGroup(String groupName) {
		RosterGroup rosterGroup = mRoster.getGroup(groupName);

		// create group if unknown
		if ((groupName.length() > 0) && rosterGroup == null) {
			rosterGroup = mRoster.createGroup(groupName);
		}
		return rosterGroup;

	}

	private void removeRosterEntryFromGroups(RosterEntry rosterEntry)
			throws YaximXMPPException {
		Collection<RosterGroup> oldGroups = rosterEntry.getGroups();

		for (RosterGroup group : oldGroups) {
			tryToRemoveUserFromGroup(group, rosterEntry);
		}
	}

	private void tryToRemoveUserFromGroup(RosterGroup group,
			RosterEntry rosterEntry) throws YaximXMPPException {
		try {
			group.removeEntry(rosterEntry);
		} catch (XMPPException e) {
			throw new YaximXMPPException(e.getLocalizedMessage());
		}
	}

	private void tryToRemoveRosterEntry(String user) throws YaximXMPPException {
		try {
			RosterEntry rosterEntry = mRoster.getEntry(user);

			if (rosterEntry != null) {
				// first, unsubscribe the user
				Presence unsub = new Presence(Presence.Type.unsubscribed);
				unsub.setTo(rosterEntry.getUser());
				mXMPPConnection.sendPacket(unsub);
				// then, remove from roster
				mRoster.removeEntry(rosterEntry);
			}
		} catch (XMPPException e) {
			throw new YaximXMPPException(e.getLocalizedMessage());
		}
	}

	private void tryToAddRosterEntry(String user, String alias, String group)
			throws YaximXMPPException {
		try {
			mRoster.createEntry(user, alias, new String[] { group });
		} catch (XMPPException e) {
			throw new YaximXMPPException(e.getLocalizedMessage());
		}
	}

	private void removeOldRosterEntries() {
		Log.d(TAG, "removeOldRosterEntries()");
		Collection<RosterEntry> rosterEntries = mRoster.getEntries();
		StringBuilder exclusion = new StringBuilder(RosterConstants.JID + " NOT IN (");
		boolean first = true;
		for (RosterEntry rosterEntry : rosterEntries) {
			updateRosterEntryInDB(rosterEntry);
			if (first)
				first = false;
			else
				exclusion.append(",");
			exclusion.append("'").append(rosterEntry.getUser()).append("'");
		}
		exclusion.append(")");
		int count = mContentResolver.delete(RosterProvider.CONTENT_URI, exclusion.toString(), null);
		Log.d(TAG, "deleted " + count + " old roster entries");
	}

	// HACK: add an incoming subscription request as a fake roster entry
	private void handleIncomingSubscribe(Presence request) {
		final ContentValues values = new ContentValues();

		values.put(RosterConstants.JID, request.getFrom());
		values.put(RosterConstants.ALIAS, request.getFrom());
		values.put(RosterConstants.GROUP, "");

		values.put(RosterConstants.STATUS_MODE, getStatusInt(request));
		values.put(RosterConstants.STATUS_MESSAGE, request.getStatus());
		
		Uri uri = mContentResolver.insert(RosterProvider.CONTENT_URI, values);
		debugLog("handleIncomingSubscribe: faked " + uri);
	}

	public void setStatusFromConfig() {
		// TODO: only call this when carbons changed, not on every presence change
		CarbonManager.getInstanceFor(mXMPPConnection).sendCarbonsEnabled(mConfig.messageCarbons);

		Presence presence = new Presence(Presence.Type.available);
		Mode mode = Mode.valueOf(mConfig.statusMode);
		presence.setMode(mode);
		presence.setStatus(mConfig.statusMessage);
		presence.setPriority(mConfig.priority);
		mXMPPConnection.sendPacket(presence);
		mConfig.presence_required = false;
	}

	public void sendOfflineMessages() {
		Cursor cursor = mContentResolver.query(ChatProvider.CONTENT_URI,
				SEND_OFFLINE_PROJECTION, SEND_OFFLINE_SELECTION,
				null, null);
		final int      _ID_COL = cursor.getColumnIndexOrThrow(ChatConstants._ID);
		final int      JID_COL = cursor.getColumnIndexOrThrow(ChatConstants.JID);
		final int      MSG_COL = cursor.getColumnIndexOrThrow(ChatConstants.MESSAGE);
		final int       TS_COL = cursor.getColumnIndexOrThrow(ChatConstants.DATE);
		final int PACKETID_COL = cursor.getColumnIndexOrThrow(ChatConstants.PACKET_ID);
		ContentValues mark_sent = new ContentValues();
		mark_sent.put(ChatConstants.DELIVERY_STATUS, ChatConstants.DS_SENT_OR_READ);
		while (cursor.moveToNext()) {
			int _id = cursor.getInt(_ID_COL);
			String toJID = cursor.getString(JID_COL);
			String message = cursor.getString(MSG_COL);
			String packetID = cursor.getString(PACKETID_COL);
			long ts = cursor.getLong(TS_COL);
			Log.d(TAG, "sendOfflineMessages: " + toJID + " > " + message);
			final Message newMessage = new Message(toJID, Message.Type.chat);
			newMessage.setBody(message);
			DelayInformation delay = new DelayInformation(new Date(ts));
			newMessage.addExtension(delay);
			newMessage.addExtension(new DelayInfo(delay));
			newMessage.addExtension(new DeliveryReceiptRequest());
			if ((packetID != null) && (packetID.length() > 0)) {
				newMessage.setPacketID(packetID);
			} else {
				packetID = newMessage.getPacketID();
				mark_sent.put(ChatConstants.PACKET_ID, packetID);
			}
			Uri rowuri = Uri.parse("content://" + ChatProvider.AUTHORITY
				+ "/" + ChatProvider.TABLE_NAME + "/" + _id);
			mContentResolver.update(rowuri, mark_sent,
						null, null);
			mXMPPConnection.sendPacket(newMessage);		// must be after marking delivered, otherwise it may override the SendFailListener
		}
		cursor.close();
	}

	public static void sendOfflineMessage(ContentResolver cr, String toJID, String message) {
		ContentValues values = new ContentValues();
		values.put(ChatConstants.DIRECTION, ChatConstants.OUTGOING);
		values.put(ChatConstants.JID, toJID);
		values.put(ChatConstants.MESSAGE, message);
		values.put(ChatConstants.DELIVERY_STATUS, ChatConstants.DS_NEW);
		values.put(ChatConstants.DATE, System.currentTimeMillis());

		cr.insert(ChatProvider.CONTENT_URI, values);
	}

	public void sendReceipt(String toJID, String id) {
		Log.d(TAG, "sending XEP-0184 ack to " + toJID + " id=" + id);
		final Message ack = new Message(toJID, Message.Type.normal);
		ack.addExtension(new DeliveryReceipt(id));
		mXMPPConnection.sendPacket(ack);
	}

	public void sendMessage(String toJID, String message) {
		final Message newMessage = new Message(toJID, Message.Type.chat);
		newMessage.setBody(message);
		newMessage.addExtension(new DeliveryReceiptRequest());
		if (isAuthenticated()) {
			addChatMessageToDB(ChatConstants.OUTGOING, toJID, message, ChatConstants.DS_SENT_OR_READ,
					System.currentTimeMillis(), newMessage.getPacketID());
			mXMPPConnection.sendPacket(newMessage);
		} else {
			// send offline -> store to DB
			addChatMessageToDB(ChatConstants.OUTGOING, toJID, message, ChatConstants.DS_NEW,
					System.currentTimeMillis(), newMessage.getPacketID());
		}
	}

	public boolean isAuthenticated() {
		if (mXMPPConnection != null) {
			return (mXMPPConnection.isConnected() && mXMPPConnection
					.isAuthenticated());
		}
		return false;
	}

	public void registerCallback(XMPPServiceCallback callBack) {
		this.mServiceCallBack = callBack;
		mService.registerReceiver(mPingAlarmReceiver, new IntentFilter(PING_ALARM));
		mService.registerReceiver(mPongTimeoutAlarmReceiver, new IntentFilter(PONG_TIMEOUT_ALARM));
	}

	public void unRegisterCallback() {
		debugLog("unRegisterCallback()");
		// remove callbacks _before_ tossing old connection
		try {
			mXMPPConnection.getRoster().removeRosterListener(mRosterListener);
			mXMPPConnection.removePacketListener(mPacketListener);
			mXMPPConnection.removePacketListener(mPresenceListener);

			mXMPPConnection.removePacketListener(mPongListener);
			unregisterPongListener();
		} catch (Exception e) {
			// ignore it!
			e.printStackTrace();
		}
		requestConnectionState(ConnectionState.OFFLINE);
		setStatusOffline();
		mService.unregisterReceiver(mPingAlarmReceiver);
		mService.unregisterReceiver(mPongTimeoutAlarmReceiver);
		this.mServiceCallBack = null;
	}
	
	public String getNameForJID(String jid) {
		if (null != this.mRoster.getEntry(jid) && null != this.mRoster.getEntry(jid).getName() && this.mRoster.getEntry(jid).getName().length() > 0) {
			return this.mRoster.getEntry(jid).getName();
		} else {
			return jid;
		}			
	}

	private void setStatusOffline() {
		ContentValues values = new ContentValues();
		values.put(RosterConstants.STATUS_MODE, StatusMode.offline.ordinal());
		mContentResolver.update(RosterProvider.CONTENT_URI, values, null, null);
	}

	private void registerRosterListener() {
		// flush roster on connecting.
		mRoster = mXMPPConnection.getRoster();
		mRoster.setSubscriptionMode(Roster.SubscriptionMode.manual);

		if (mRosterListener != null)
			mRoster.removeRosterListener(mRosterListener);

		mRosterListener = new RosterListener() {
			private boolean first_roster = true;

			public void entriesAdded(Collection<String> entries) {
				debugLog("entriesAdded(" + entries + ")");

				ContentValues[] cvs = new ContentValues[entries.size()];
				int i = 0;
				for (String entry : entries) {
					RosterEntry rosterEntry = mRoster.getEntry(entry);
					cvs[i++] = getContentValuesForRosterEntry(rosterEntry);
				}
				mContentResolver.bulkInsert(RosterProvider.CONTENT_URI, cvs);
				// when getting the roster in the beginning, remove remains of old one
				if (first_roster) {
					removeOldRosterEntries();
					first_roster = false;
					mServiceCallBack.rosterChanged();
				}
				debugLog("entriesAdded() done");
			}

			public void entriesDeleted(Collection<String> entries) {
				debugLog("entriesDeleted(" + entries + ")");

				for (String entry : entries) {
					deleteRosterEntryFromDB(entry);
				}
				mServiceCallBack.rosterChanged();
			}

			public void entriesUpdated(Collection<String> entries) {
				debugLog("entriesUpdated(" + entries + ")");

				for (String entry : entries) {
					RosterEntry rosterEntry = mRoster.getEntry(entry);
					updateRosterEntryInDB(rosterEntry);
				}
				mServiceCallBack.rosterChanged();
			}

			public void presenceChanged(Presence presence) {
				debugLog("presenceChanged(" + presence.getFrom() + "): " + presence);

				String jabberID = getBareJID(presence.getFrom());
				RosterEntry rosterEntry = mRoster.getEntry(jabberID);
				updateRosterEntryInDB(rosterEntry);
				mServiceCallBack.rosterChanged();
			}
		};
		mRoster.addRosterListener(mRosterListener);
	}

	private String getBareJID(String from) {
		String[] res = from.split("/");
		return res[0].toLowerCase();
	}

	public void changeMessageDeliveryStatus(String packetID, int new_status) {
		ContentValues cv = new ContentValues();
		cv.put(ChatConstants.DELIVERY_STATUS, new_status);
		Uri rowuri = Uri.parse("content://" + ChatProvider.AUTHORITY + "/"
				+ ChatProvider.TABLE_NAME);
		mContentResolver.update(rowuri, cv,
				ChatConstants.PACKET_ID + " = ? AND " +
				ChatConstants.DIRECTION + " = " + ChatConstants.OUTGOING,
				new String[] { packetID });
	}

	/** Check the server connection, reconnect if needed.
	 *
	 * This function will try to ping the server if we are connected, and try
	 * to reestablish a connection otherwise.
	 */
	public void sendServerPing() {
		if (mXMPPConnection == null || !mXMPPConnection.isAuthenticated()) {
			debugLog("Ping: requested, but not connected to server.");
			requestConnectionState(ConnectionState.ONLINE, false);
			return;
		}
		if (mPingID != null) {
			debugLog("Ping: requested, but still waiting for " + mPingID);
			return; // a ping is still on its way
		}

		mPingTimestamp = System.currentTimeMillis();
		if (mStreamHandler.isSmEnabled()) {
			debugLog("Ping: sending SM request");
			mPingID = "" + mStreamHandler.requestAck();
		} else {
			Ping ping = new Ping();
			ping.setType(Type.GET);
			ping.setTo(mConfig.server);
			mPingID = ping.getPacketID();
			debugLog("Ping: sending ping " + mPingID);
			mXMPPConnection.sendPacket(ping);
		}

		// register ping timeout handler: PACKET_TIMEOUT(30s) + 3s
		((AlarmManager)mService.getSystemService(Context.ALARM_SERVICE)).set(AlarmManager.RTC_WAKEUP,
			System.currentTimeMillis() + PACKET_TIMEOUT + 3000, mPongTimeoutAlarmPendIntent);
	}

	private void gotServerPong(String pongID) {
		long latency = System.currentTimeMillis() - mPingTimestamp;
		if (pongID != null && pongID.equals(mPingID))
			Log.i(TAG, String.format("Ping: server latency %1.3fs",
						latency/1000.));
		else
			Log.i(TAG, String.format("Ping: server latency %1.3fs (estimated)",
						latency/1000.));
		mPingID = null;
		((AlarmManager)mService.getSystemService(Context.ALARM_SERVICE)).cancel(mPongTimeoutAlarmPendIntent);
	}

	/**
	 * BroadcastReceiver to trigger reconnect on pong timeout.
	 */
	private class PongTimeoutAlarmReceiver extends BroadcastReceiver {
		public void onReceive(Context ctx, Intent i) {
			debugLog("Ping: timeout for " + mPingID);
			onDisconnected("Ping timeout");
		}
	}

	/**
	 * BroadcastReceiver to trigger sending pings to the server
	 */
	private class PingAlarmReceiver extends BroadcastReceiver {
		public void onReceive(Context ctx, Intent i) {
				sendServerPing();
		}
	}

	/**
	 * Registers a smack packet listener for IQ packets, intended to recognize "pongs" with
	 * a packet id matching the last "ping" sent to the server.
	 *
	 * Also sets up the AlarmManager Timer plus necessary intents.
	 */
	private void registerPongListener() {
		// reset ping expectation on new connection
		mPingID = null;

		if (mPongListener != null)
			mXMPPConnection.removePacketListener(mPongListener);

		mPongListener = new PacketListener() {

			@Override
			public void processPacket(Packet packet) {
				if (packet == null) return;

				gotServerPong(packet.getPacketID());
			}

		};

		mXMPPConnection.addPacketListener(mPongListener, new PacketTypeFilter(IQ.class));
		mPingAlarmPendIntent = PendingIntent.getBroadcast(mService.getApplicationContext(), 0, mPingAlarmIntent,
					PendingIntent.FLAG_UPDATE_CURRENT);
		mPongTimeoutAlarmPendIntent = PendingIntent.getBroadcast(mService.getApplicationContext(), 0, mPongTimeoutAlarmIntent,
					PendingIntent.FLAG_UPDATE_CURRENT);
		((AlarmManager)mService.getSystemService(Context.ALARM_SERVICE)).setInexactRepeating(AlarmManager.RTC_WAKEUP, 
				System.currentTimeMillis() + AlarmManager.INTERVAL_FIFTEEN_MINUTES, AlarmManager.INTERVAL_FIFTEEN_MINUTES, mPingAlarmPendIntent);
	}
	private void unregisterPongListener() {
		((AlarmManager)mService.getSystemService(Context.ALARM_SERVICE)).cancel(mPingAlarmPendIntent);
		((AlarmManager)mService.getSystemService(Context.ALARM_SERVICE)).cancel(mPongTimeoutAlarmPendIntent);
	}

	private void registerMessageListener() {
		// do not register multiple packet listeners
		if (mPacketListener != null)
			mXMPPConnection.removePacketListener(mPacketListener);

		PacketTypeFilter filter = new PacketTypeFilter(Message.class);

		mPacketListener = new PacketListener() {
			public void processPacket(Packet packet) {
				try {
				if (packet instanceof Message) {
					Message msg = (Message) packet;

					String fromJID = getBareJID(msg.getFrom());
					int direction = ChatConstants.INCOMING;
					Carbon cc = CarbonManager.getCarbon(msg);

					// extract timestamp
					long ts;
					DelayInfo timestamp = (DelayInfo)msg.getExtension("delay", "urn:xmpp:delay");
					if (timestamp == null)
						timestamp = (DelayInfo)msg.getExtension("x", "jabber:x:delay");
					if (cc != null) // Carbon timestamp overrides packet timestamp
						timestamp = cc.getForwarded().getDelayInfo();
					if (timestamp != null)
						ts = timestamp.getStamp().getTime();
					else
						ts = System.currentTimeMillis();

					// try to extract a carbon
					if (cc != null) {
						Log.d(TAG, "carbon: " + cc.toXML());
						msg = (Message)cc.getForwarded().getForwardedPacket();

						// outgoing carbon: fromJID is actually chat peer's JID
						if (cc.getDirection() == Carbon.Direction.sent) {
							fromJID = getBareJID(msg.getTo());
							direction = ChatConstants.OUTGOING;
						} else {
							fromJID = getBareJID(msg.getFrom());

							// hook off carbonated delivery receipts
							DeliveryReceipt dr = (DeliveryReceipt)msg.getExtension(
									DeliveryReceipt.ELEMENT, DeliveryReceipt.NAMESPACE);
							if (dr != null) {
								Log.d(TAG, "got CC'ed delivery receipt for " + dr.getId());
								changeMessageDeliveryStatus(dr.getId(), ChatConstants.DS_ACKED);
							}
						}
					}

					String chatMessage = msg.getBody();

					// display error inline
					if (msg.getType() == Message.Type.error) {
						changeMessageDeliveryStatus(msg.getPacketID(), ChatConstants.DS_FAILED);
						chatMessage = msg.getError().toString();
					}

					// ignore empty messages
					if (chatMessage == null) {
						Log.d(TAG, "empty message.");
						return;
					}

					// carbons are old. all others are new
					int is_new = (cc == null) ? ChatConstants.DS_NEW : ChatConstants.DS_SENT_OR_READ;
					if (msg.getType() == Message.Type.error)
						is_new = ChatConstants.DS_FAILED;

					addChatMessageToDB(direction, fromJID, chatMessage, is_new, ts, msg.getPacketID());
					if (direction == ChatConstants.INCOMING)
						mServiceCallBack.newMessage(fromJID, chatMessage, (cc != null));
				}
				} catch (Exception e) {
					// SMACK silently discards exceptions dropped from processPacket :(
					Log.e(TAG, "failed to process packet:");
					e.printStackTrace();
				}
			}
		};

		mXMPPConnection.addPacketListener(mPacketListener, filter);
	}

	private void registerPresenceListener() {
		// do not register multiple packet listeners
		if (mPresenceListener != null)
			mXMPPConnection.removePacketListener(mPresenceListener);

		mPresenceListener = new PacketListener() {
			public void processPacket(Packet packet) {
				try {
					Presence p = (Presence) packet;
					switch (p.getType()) {
					case subscribe:
						handleIncomingSubscribe(p);
						break;
					case unsubscribe:
						break;
					}
				} catch (Exception e) {
					// SMACK silently discards exceptions dropped from processPacket :(
					Log.e(TAG, "failed to process presence:");
					e.printStackTrace();
				}
			}
		};

		mXMPPConnection.addPacketListener(mPresenceListener, new PacketTypeFilter(Presence.class));
	}

	private void addChatMessageToDB(int direction, String JID,
			String message, int delivery_status, long ts, String packetID) {
		ContentValues values = new ContentValues();

		values.put(ChatConstants.DIRECTION, direction);
		values.put(ChatConstants.JID, JID);
		values.put(ChatConstants.MESSAGE, message);
		values.put(ChatConstants.DELIVERY_STATUS, delivery_status);
		values.put(ChatConstants.DATE, ts);
		values.put(ChatConstants.PACKET_ID, packetID);

		mContentResolver.insert(ChatProvider.CONTENT_URI, values);
	}

	private ContentValues getContentValuesForRosterEntry(final RosterEntry entry) {
		final ContentValues values = new ContentValues();

		values.put(RosterConstants.JID, entry.getUser());
		values.put(RosterConstants.ALIAS, getName(entry));

		Presence presence = mRoster.getPresence(entry.getUser());
		values.put(RosterConstants.STATUS_MODE, getStatusInt(presence));
		values.put(RosterConstants.STATUS_MESSAGE, presence.getStatus());
		values.put(RosterConstants.GROUP, getGroup(entry.getGroups()));

		return values;
	}

	private void addRosterEntryToDB(final RosterEntry entry) {
		ContentValues values = getContentValuesForRosterEntry(entry);
		Uri uri = mContentResolver.insert(RosterProvider.CONTENT_URI, values);
		debugLog("addRosterEntryToDB: Inserted " + uri);
	}

	private void deleteRosterEntryFromDB(final String jabberID) {
		int count = mContentResolver.delete(RosterProvider.CONTENT_URI,
				RosterConstants.JID + " = ?", new String[] { jabberID });
		debugLog("deleteRosterEntryFromDB: Deleted " + count + " entries");
	}

	private void updateRosterEntryInDB(final RosterEntry entry) {
		final ContentValues values = getContentValuesForRosterEntry(entry);

		if (mContentResolver.update(RosterProvider.CONTENT_URI, values,
				RosterConstants.JID + " = ?", new String[] { entry.getUser() }) == 0)
			addRosterEntryToDB(entry);
	}

	private String getGroup(Collection<RosterGroup> groups) {
		for (RosterGroup group : groups) {
			return group.getName();
		}
		return "";
	}

	private String getName(RosterEntry rosterEntry) {
		String name = rosterEntry.getName();
		if (name != null && name.length() > 0) {
			return name;
		}
		name = StringUtils.parseName(rosterEntry.getUser());
		if (name.length() > 0) {
			return name;
		}
		return rosterEntry.getUser();
	}

	private StatusMode getStatus(Presence presence) {
		if (presence.getType() == Presence.Type.subscribe)
			return StatusMode.subscribe;
		if (presence.getType() == Presence.Type.available) {
			if (presence.getMode() != null) {
				return StatusMode.valueOf(presence.getMode().name());
			}
			return StatusMode.available;
		}
		return StatusMode.offline;
	}

	private int getStatusInt(final Presence presence) {
		return getStatus(presence).ordinal();
	}

	private void debugLog(String data) {
		if (LogConstants.LOG_DEBUG) {
			Log.d(TAG, data);
		}
	}

	@Override
	public String getLastError() {
		return mLastError;
	}
}
