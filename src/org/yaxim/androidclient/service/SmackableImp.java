package org.yaxim.androidclient.service;

import java.io.File;
import java.text.Collator;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

import de.duenndns.ssl.MemorizingTrustManager;

import org.jivesoftware.smack.AccountManager;
import org.jivesoftware.smack.Connection;
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
import org.jivesoftware.smack.packet.RosterPacket;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.util.DNSUtil;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smack.util.dns.DNSJavaResolver;
import org.jivesoftware.smackx.entitycaps.EntityCapsManager;
import org.jivesoftware.smackx.entitycaps.cache.SimpleDirectoryPersistentCache;
import org.jivesoftware.smackx.Form;
import org.jivesoftware.smackx.FormField;
import org.jivesoftware.smackx.ServiceDiscoveryManager;
import org.jivesoftware.smackx.muc.DiscussionHistory;
import org.jivesoftware.smackx.muc.InvitationListener;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.Occupant;
import org.jivesoftware.smackx.muc.RoomInfo;
import org.jivesoftware.smackx.carbons.Carbon;
import org.jivesoftware.smackx.carbons.CarbonManager;
import org.jivesoftware.smackx.entitycaps.provider.CapsExtensionProvider;
import org.jivesoftware.smackx.forward.Forwarded;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.provider.DataFormProvider;
import org.jivesoftware.smackx.provider.DelayInfoProvider;
import org.jivesoftware.smackx.provider.DiscoverInfoProvider;
import org.jivesoftware.smackx.provider.DiscoverItemsProvider;
import org.jivesoftware.smackx.provider.MUCAdminProvider;
import org.jivesoftware.smackx.provider.MUCOwnerProvider;
import org.jivesoftware.smackx.provider.MUCUserProvider;
import org.jivesoftware.smackx.packet.DelayInformation;
import org.jivesoftware.smackx.packet.DelayInfo;
import org.jivesoftware.smackx.packet.DiscoverInfo;
import org.jivesoftware.smackx.packet.MUCUser;
import org.jivesoftware.smackx.packet.Version;
import org.jivesoftware.smackx.ping.PingManager;
import org.jivesoftware.smackx.ping.packet.*;
import org.jivesoftware.smackx.ping.provider.PingProvider;
import org.jivesoftware.smackx.receipts.DeliveryReceipt;
import org.jivesoftware.smackx.receipts.DeliveryReceiptManager;
import org.jivesoftware.smackx.receipts.DeliveryReceiptRequest;
import org.jivesoftware.smackx.receipts.ReceiptReceivedListener;
import org.yaxim.androidclient.YaximApplication;
import org.yaxim.androidclient.data.ChatHelper;
import org.yaxim.androidclient.data.ChatProvider;
import org.yaxim.androidclient.data.RosterProvider;
import org.yaxim.androidclient.data.YaximConfiguration;
import org.yaxim.androidclient.data.ChatProvider.ChatConstants;
import org.yaxim.androidclient.data.RosterProvider.RosterConstants;
import org.yaxim.androidclient.exceptions.YaximXMPPException;
import org.yaxim.androidclient.packet.PreAuth;
import org.yaxim.androidclient.packet.Replace;
import org.yaxim.androidclient.util.ConnectionState;
import org.yaxim.androidclient.util.LogConstants;
import org.yaxim.androidclient.util.PreferenceConstants;
import org.yaxim.androidclient.util.StatusMode;
import org.yaxim.androidclient.R;

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
import android.telephony.gsm.SmsMessage.MessageClass;
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

		// initialize smack defaults before any connections are created
		SmackConfiguration.setPacketReplyTimeout(PACKET_TIMEOUT);
		SmackConfiguration.setDefaultPingInterval(0);
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

		// data forms
		pm.addExtensionProvider("x","jabber:x:data", new DataFormProvider());

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

		// XEP-0308 Last Message Correction
		pm.addExtensionProvider("replace", Replace.NAMESPACE, new Replace.Provider());

		// XEP-XXXX Pre-Authenticated Roster Subscription
		pm.addExtensionProvider("preauth", PreAuth.NAMESPACE, new PreAuth.Provider());

		//  MUC User
		pm.addExtensionProvider("x","http://jabber.org/protocol/muc#user", new MUCUserProvider());
		//  MUC Admin
		pm.addIQProvider("query","http://jabber.org/protocol/muc#admin", new MUCAdminProvider());
		//  MUC Owner
		pm.addIQProvider("query","http://jabber.org/protocol/muc#owner", new MUCOwnerProvider());

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

	private AlarmManager mAlarmManager;
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
	
	private final HashSet<String> mucJIDs = new HashSet<String>();
	private Map<String, MultiUserChat> multiUserChats;
	private Map<String, Presence> subscriptionRequests = new HashMap<String, Presence>();


	public SmackableImp(YaximConfiguration config,
			ContentResolver contentResolver,
			Service service) {
		this.mConfig = config;
		this.mContentResolver = contentResolver;
		this.mService = service;
		this.mAlarmManager = (AlarmManager)mService.getSystemService(Context.ALARM_SERVICE);
	}
		
	// this code runs a DNS resolver, might be blocking
	private synchronized void initXMPPConnection() {
		// allow custom server / custom port to override SRV record
		if (mConfig.customServer.length() > 0)
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
			MemorizingTrustManager mtm = YaximApplication.getApp(mService).mMTM;
			sc.init(null, new X509TrustManager[] { mtm },
					new java.security.SecureRandom());
			this.mXMPPConfig.setCustomSSLContext(sc);
			this.mXMPPConfig.setHostnameVerifier(mtm.wrapHostnameVerifier(
						new org.apache.http.conn.ssl.StrictHostnameVerifier()));
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

		multiUserChats = new HashMap<String, MultiUserChat>();
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
			updateConnectionState(ConnectionState.LOADING);
			registerMessageListener();
			registerPresenceListener();
			registerPongListener();
			syncDbRooms();
			sendOfflineMessages();
			sendUserWatching();
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
			case DISCONNECTED:
			case OFFLINE:
				// update state before starting thread to prevent race conditions
				updateConnectionState(ConnectionState.CONNECTING);

				// register ping (connection) timeout handler: 2*PACKET_TIMEOUT(30s) + 3s
				registerPongTimeout(2*PACKET_TIMEOUT + 3000, "connection");

				new Thread() {
					@Override
					public void run() {
						updateConnectingThread(this);
						try {
							doConnect(create_account);
						} catch (IllegalArgumentException e) {
							// this might happen when DNS resolution in ConnectionConfiguration fails
							onDisconnected(e);
						} catch (IllegalStateException e) {//TODO: work around old smack
							onDisconnected(e);
						} catch (YaximXMPPException e) {
							onDisconnected(e);
						} finally {
							mAlarmManager.cancel(mPongTimeoutAlarmPendIntent);
							finishConnectingThread();
						}
					}
				}.start();
				break;
			case CONNECTING:
			case LOADING:
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

				// register ping (connection) timeout handler: PACKET_TIMEOUT(30s)
				registerPongTimeout(PACKET_TIMEOUT, "forced disconnect");

				new Thread() {
					public void run() {
						updateConnectingThread(this);
						mStreamHandler.quickShutdown();
						onDisconnected("forced disconnect completed");
						finishConnectingThread();
						//updateConnectionState(ConnectionState.OFFLINE);
					}
				}.start();
			}
			break;
		case OFFLINE:
			switch (mState) {
			case CONNECTING:
			case LOADING:
			case ONLINE:
				// update state before starting thread to prevent race conditions
				updateConnectionState(ConnectionState.DISCONNECTING);

				// register ping (connection) timeout handler: PACKET_TIMEOUT(30s)
				registerPongTimeout(PACKET_TIMEOUT, "manual disconnect");

				// spawn thread to do disconnect
				new Thread() {
					public void run() {
						updateConnectingThread(this);
						mXMPPConnection.shutdown();
						mStreamHandler.close();
						mAlarmManager.cancel(mPongTimeoutAlarmPendIntent);
						// we should reset XMPPConnection the next time
						mConfig.reconnect_required = true;
						finishConnectingThread();
						// reconnect if it was requested in the meantime
						if (mRequestedState == ConnectionState.ONLINE)
							requestConnectionState(ConnectionState.ONLINE);
					}
				}.start();
				break;
			case DISCONNECTING:
				break;
			case DISCONNECTED:
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
		if (new_state == ConnectionState.ONLINE || new_state == ConnectionState.LOADING)
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
		PingManager.getInstanceFor(mXMPPConnection).disablePingFloodProtection();

		// set Version for replies
		String app_name = mService.getString(R.string.app_name);
		String build_revision = mService.getString(R.string.build_revision);
		Version.Manager.getInstanceFor(mXMPPConnection).setVersion(
				new Version(app_name, build_revision, "Android"));

		// reference DeliveryReceiptManager, add listener
		DeliveryReceiptManager dm = DeliveryReceiptManager.getInstanceFor(mXMPPConnection);
		dm.addReceiptReceivedListener(new ReceiptReceivedListener() { // DOES NOT WORK IN CARBONS
			public void onReceiptReceived(String fromJid, String toJid, String receiptId) {
				Log.d(TAG, "got delivery receipt for " + receiptId);
				changeMessageDeliveryStatus(receiptId, ChatConstants.DS_ACKED);
			}});
	}

	public void addRosterItem(String user, String alias, String group, String token)
			throws YaximXMPPException {
		subscriptionRequests.remove(user);
		mConfig.whitelistInvitationJID(user);
		tryToAddRosterEntry(user, alias, group, token);
	}

	public void removeRosterItem(String user) throws YaximXMPPException {
		debugLog("removeRosterItem(" + user + ")");
		subscriptionRequests.remove(user);
		tryToRemoveRosterEntry(user);
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
		subscriptionRequests.remove(user);
		if ("unsubscribed".equals(type))
			deleteRosterEntryFromDB(user);
		Presence response = new Presence(Presence.Type.valueOf(type));
		response.setTo(user);
		mXMPPConnection.sendPacket(response);
	}
	
	@Override
	public String changePassword(String newPassword) {
		try {
			new AccountManager(mXMPPConnection).changePassword(newPassword);
			return "OK"; //HACK: hard coded string to differentiate from failure modes
		} catch (XMPPException e) {
			if (e.getXMPPError() != null)
				return e.getXMPPError().toString();
			else
				return e.getLocalizedMessage();
		}
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
		while (reason.getCause() != null && !(reason.getCause().getClass().getSimpleName().equals("GaiException")))
			reason = reason.getCause();
		onDisconnected(reason.getLocalizedMessage());
	}

	private void tryToConnect(boolean create_account) throws YaximXMPPException {
		try {
			if (mXMPPConnection.isConnected()) {
				try {
					mStreamHandler.quickShutdown(); // blocking shutdown prior to re-connection
				} catch (Exception e) {
					debugLog("conn.shutdown() failed: " + e);
				}
			}
			registerRosterListener();
			boolean need_bind = !mStreamHandler.isResumePossible();

			if (mConnectionListener != null)
				mXMPPConnection.removeConnectionListener(mConnectionListener);
			mConnectionListener = new ConnectionListener() {
				public void connectionClosedOnError(Exception e) {
					// XXX: this is the only callback we get from errors, so
					// we need to check for non-resumability and work around
					// here:
					if (!mStreamHandler.isResumePossible()) {
						multiUserChats.clear();
					}
					onDisconnected(e);
				}
				public void connectionClosed() {
					// TODO: fix reconnect when we got kicked by the server or SM failed!
					//onDisconnected(null);
					multiUserChats.clear();
					updateConnectionState(ConnectionState.OFFLINE);
				}
				public void reconnectingIn(int seconds) { }
				public void reconnectionFailed(Exception e) { }
				public void reconnectionSuccessful() { }
			};
			mXMPPConnection.addConnectionListener(mConnectionListener);

			mXMPPConnection.connect(need_bind);
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
				cleanupMUCs();
				setStatusFromConfig();
			}

		} catch (Exception e) {
			// actually we just care for IllegalState or NullPointer or XMPPEx.
			throw new YaximXMPPException("tryToConnect failed", e);
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
				throw new YaximXMPPException("tryToMoveRosterEntryToGroup", e);
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
			throw new YaximXMPPException("tryToRemoveUserFromGroup", e);
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
			throw new YaximXMPPException("tryToRemoveRosterEntry", e);
		}
	}

	private void tryToAddRosterEntry(String user, String alias, String group, String token)
			throws YaximXMPPException {
		try {
			// send a presence subscription request with token (must be before roster action!)
			if (token != null && token.length() > 0) {
				Presence preauth = new Presence(Presence.Type.subscribe);
				preauth.setTo(user);
				preauth.addExtension(new PreAuth(token));
				mXMPPConnection.sendPacket(preauth);
			}
			// add to roster, triggers another sub request by Smack (sigh)
			mRoster.createEntry(user, alias, new String[] { group });
			// send a pre-approval
			Presence pre_approval = new Presence(Presence.Type.subscribed);
			pre_approval.setTo(user);
			mXMPPConnection.sendPacket(pre_approval);
			mConfig.whitelistInvitationJID(user);
		} catch (XMPPException e) {
			throw new YaximXMPPException("tryToAddRosterEntry", e);
		}
	}

	private void removeOldRosterEntries() {
		Log.d(TAG, "removeOldRosterEntries()");
		Collection<RosterEntry> rosterEntries = mRoster.getEntries();
		StringBuilder exclusion = new StringBuilder(RosterConstants.JID + " NOT IN (");
		boolean first = true;
		
		for (RosterEntry rosterEntry : rosterEntries) {
			if (first)
				first = false;
			else
				exclusion.append(",");
			exclusion.append("'").append(rosterEntry.getUser()).append("'");
		}
		
		exclusion.append(") AND "+RosterConstants.GROUP+" NOT IN ('" + RosterProvider.RosterConstants.MUCS + "');");
		int count = mContentResolver.delete(RosterProvider.CONTENT_URI, exclusion.toString(), null);
		Log.d(TAG, "deleted " + count + " old roster entries");
	}

	// HACK: add an incoming subscription request as a fake roster entry
	private void handleIncomingSubscribe(Presence request) {
		// perform Pre-Authenticated Roster Subscription, fallback to manual
		try {
			String jid = request.getFrom();
			PreAuth preauth = (PreAuth)request.getExtension(PreAuth.ELEMENT, PreAuth.NAMESPACE);
			String jid_or_token = jid;
			if (preauth != null) {
				jid_or_token = preauth.getToken();
				Log.d(TAG, "PARS: found token " + jid_or_token);
			}
			if (mConfig.redeemInvitationCode(jid_or_token)) {
				Log.d(TAG, "PARS: approving request from " + jid);
				if (mRoster.getEntry(request.getFrom()) != null) {
					// already in roster, only send approval
					Presence response = new Presence(Presence.Type.subscribed);
					response.setTo(jid);
					mXMPPConnection.sendPacket(response);
				} else {
					tryToAddRosterEntry(jid, null, "", null);
				}
				return;
			}
		} catch (YaximXMPPException e) {
			Log.d(TAG, "PARS: failed to send response: " + e);
		}

		subscriptionRequests.put(request.getFrom(), request);

		final ContentValues values = new ContentValues();

		values.put(RosterConstants.JID, request.getFrom());
		values.put(RosterConstants.STATUS_MODE, getStatusInt(request));
		values.put(RosterConstants.STATUS_MESSAGE, request.getStatus());
		if (!mRoster.contains(request.getFrom())) {
			// reset alias and group for new entries
			values.put(RosterConstants.ALIAS, request.getFrom());
			values.put(RosterConstants.GROUP, "");
		};
		upsertRoster(values, request.getFrom());
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
			if (mucJIDs.contains(toJID))
				newMessage.setType(Message.Type.groupchat);
			else
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

	public void sendReceiptIfRequested(Packet packet) {
		DeliveryReceiptRequest drr = (DeliveryReceiptRequest)packet.getExtension(
				DeliveryReceiptRequest.ELEMENT, DeliveryReceipt.NAMESPACE);
		if (drr != null) {
			Message ack = new Message(packet.getFrom(), Message.Type.normal);
			ack.addExtension(new DeliveryReceipt(packet.getPacketID()));
			mXMPPConnection.sendPacket(ack);
		}
	}

	public void sendMessage(String toJID, String message) {
		final Message newMessage = new Message(toJID, Message.Type.chat);
		newMessage.setBody(message);
		newMessage.addExtension(new DeliveryReceiptRequest());
		if (isAuthenticated()) {

			if(mucJIDs.contains(toJID)) {
				sendMucMessage(toJID, message);
			} else {
				addChatMessageToDB(ChatConstants.OUTGOING, toJID, message, ChatConstants.DS_SENT_OR_READ,
						System.currentTimeMillis(), newMessage.getPacketID());
				mXMPPConnection.sendPacket(newMessage);
			}
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
//		multiUserChats.clear(); // TODO: right place
		this.mServiceCallBack = null;
	}
	
	public String getNameForJID(String jid) {
		RosterEntry re = mRoster.getEntry(jid);
		if (null != re && null != re.getName() && re.getName().length() > 0) {
			return re.getName();
		} else if (mucJIDs.contains(jid)) {
			// query the DB as we do not have the room name in memory
			Cursor c = mContentResolver.query(RosterProvider.CONTENT_URI, new String[] { RosterConstants.ALIAS },
					RosterConstants.JID + " = ?", new String[] { jid }, null);
			String result = jid;
			if (c.moveToFirst())
				result = c.getString(0);
			c.close();
			return result;
		} else {
			return jid;
		}			
	}

	public long getRowIdForMessage(String jid, String resource, int direction, String packet_id) {
		// query the DB for the RowID, return -1 if packet_id does not match
		Cursor c = mContentResolver.query(ChatProvider.CONTENT_URI, new String[] { ChatConstants._ID, ChatConstants.PACKET_ID },
				"jid = ? AND resource = ? AND from_me = ?",
				new String[] { jid, resource, "" + direction }, "_id DESC");
		long result = -1;
		if (c.moveToFirst() && c.getString(1).equals(packet_id))
			result = c.getLong(0);
		c.close();
		return result;
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
				}
				debugLog("entriesAdded() done");
			}

			public void entriesDeleted(Collection<String> entries) {
				debugLog("entriesDeleted(" + entries + ")");

				for (String entry : entries) {
					deleteRosterEntryFromDB(entry);
				}
			}

			public void entriesUpdated(Collection<String> entries) {
				debugLog("entriesUpdated(" + entries + ")");

				for (String entry : entries) {
					RosterEntry rosterEntry = mRoster.getEntry(entry);
					updateRosterEntryInDB(rosterEntry);
				}
			}

			public void presenceChanged(Presence presence) {
				debugLog("presenceChanged(" + presence.getFrom() + "): " + presence);

				String jabberID = getBareJID(presence.getFrom());
				RosterEntry rosterEntry = mRoster.getEntry(jabberID);
				if (rosterEntry != null)
					upsertRoster(getContentValuesForRosterEntry(rosterEntry, presence),
							rosterEntry.getUser());
			}
		};
		mRoster.addRosterListener(mRosterListener);
	}

	private String getBareJID(String from) {
		String[] res = from.split("/");
		return res[0].toLowerCase();
	}

	private String[] getJabberID(String from) {
		if(from.contains("/")) {
			String[] res = from.split("/");
			return new String[] { res[0], res[1] };
		} else {
			return new String[] {from, ""};
		}
	}

	public boolean changeMessageDeliveryStatus(String packetID, int new_status) {
		ContentValues cv = new ContentValues();
		cv.put(ChatConstants.DELIVERY_STATUS, new_status);
		Uri rowuri = Uri.parse("content://" + ChatProvider.AUTHORITY + "/"
				+ ChatProvider.TABLE_NAME);
		return mContentResolver.update(rowuri, cv,
				ChatConstants.PACKET_ID + " = ? AND " +
				ChatConstants.DELIVERY_STATUS + " != " + ChatConstants.DS_ACKED + " AND " +
				ChatConstants.DIRECTION + " = " + ChatConstants.OUTGOING,
				new String[] { packetID }) > 0;
	}

	protected boolean is_user_watching = false;
	public void setUserWatching(boolean user_watching) {
		if (is_user_watching == user_watching)
			return;
		is_user_watching = user_watching;
		if (mXMPPConnection != null && mXMPPConnection.isAuthenticated())
			sendUserWatching();
	}

	protected void sendUserWatching() {
		IQ toggle_google_queue = new IQ() {
			public String getChildElementXML() {
				// enable g:q = start queueing packets = do it when the user is gone
				return "<query xmlns='google:queue'><" + (is_user_watching ? "disable" : "enable") + "/></query>";
			}
		};
		toggle_google_queue.setType(IQ.Type.SET);
		mXMPPConnection.sendPacket(toggle_google_queue);
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
		registerPongTimeout(PACKET_TIMEOUT + 3000, mPingID);
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
		mAlarmManager.cancel(mPongTimeoutAlarmPendIntent);
	}

	/** Register a "pong" timeout on the connection. */
	private void registerPongTimeout(long wait_time, String id) {
		mPingID = id;
		mPingTimestamp = System.currentTimeMillis();
		debugLog(String.format("Ping: registering timeout for %s: %1.3fs", id, wait_time/1000.));
		mAlarmManager.set(AlarmManager.RTC_WAKEUP,
				System.currentTimeMillis() + wait_time,
				mPongTimeoutAlarmPendIntent);
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
				// ping all MUCs. TODO: We ignore the result for now and hope we'll get kicked
				for (MultiUserChat muc : multiUserChats.values()) {
					Ping ping = new Ping();
					ping.setType(Type.GET);
					String jid = muc.getRoom() + "/" + muc.getNickname();
					ping.setTo(jid);
					mPingID = ping.getPacketID();
					debugLog("Ping: sending ping to " + jid);
					mXMPPConnection.sendPacket(ping);
				}

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

				if (mPingID != null && mPingID.equals(packet.getPacketID()))
					gotServerPong(packet.getPacketID());
			}

		};

		mXMPPConnection.addPacketListener(mPongListener, new PacketTypeFilter(IQ.class));
		mPingAlarmPendIntent = PendingIntent.getBroadcast(mService.getApplicationContext(), 0, mPingAlarmIntent,
					PendingIntent.FLAG_UPDATE_CURRENT);
		mPongTimeoutAlarmPendIntent = PendingIntent.getBroadcast(mService.getApplicationContext(), 0, mPongTimeoutAlarmIntent,
					PendingIntent.FLAG_UPDATE_CURRENT);
		mAlarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, 
				System.currentTimeMillis() + AlarmManager.INTERVAL_FIFTEEN_MINUTES, AlarmManager.INTERVAL_FIFTEEN_MINUTES, mPingAlarmPendIntent);
	}
	private void unregisterPongListener() {
		mAlarmManager.cancel(mPingAlarmPendIntent);
		mAlarmManager.cancel(mPongTimeoutAlarmPendIntent);
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

					String[] fromJID = getJabberID(msg.getFrom());
					
					int direction = ChatConstants.INCOMING;
					Carbon cc = CarbonManager.getCarbon(msg);

					// check for jabber MUC invitation
					if(msg.getExtension("jabber:x:conference") != null) {
						Log.d(TAG, "handling MUC invitation and aborting futher packet processing...");
						handleMucInvitation(msg);
						sendReceiptIfRequested(packet);
						return;
					}

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
							fromJID = getJabberID(msg.getTo());
							direction = ChatConstants.OUTGOING;
						} else {
							fromJID = getJabberID(msg.getFrom());

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
						if (changeMessageDeliveryStatus(msg.getPacketID(), ChatConstants.DS_FAILED))
							mServiceCallBack.notifyMessage(fromJID, msg.getError().toString(), (cc != null), Message.Type.error);
						else if (mucJIDs.contains(msg.getFrom())) {
							handleKickedFromMUC(msg.getFrom(), false, null,
									msg.getError().toString());
						}
						return; // we do not want to add errors as "incoming messages"
					}

					// ignore empty messages
					if (chatMessage == null) {
						if (msg.getSubject() != null && msg.getType() == Message.Type.groupchat
								&& multiUserChats.containsKey(fromJID[0])) {
							// this is a MUC subject, update our DB
							ContentValues cvR = new ContentValues();
							cvR.put(RosterProvider.RosterConstants.STATUS_MESSAGE, msg.getSubject());
							cvR.put(RosterProvider.RosterConstants.STATUS_MODE, StatusMode.available.ordinal());
							upsertRoster(cvR, fromJID[0]);
							return;
						}
						Log.d(TAG, "empty message.");
						return;
					}

					// obtain Last Message Correction, if present
					Replace replace = (Replace)msg.getExtension(Replace.NAMESPACE);
					String replace_id = (replace != null) ? replace.getId() : null;

					// carbons are old. all others are new
					int is_new = (cc == null) ? ChatConstants.DS_NEW : ChatConstants.DS_SENT_OR_READ;
					if (msg.getType() == Message.Type.error)
						is_new = ChatConstants.DS_FAILED;

					boolean is_muc = (msg.getType() == Message.Type.groupchat);
					boolean is_from_me = (direction == ChatConstants.OUTGOING) ||
						(is_muc && multiUserChats.get(fromJID[0]).getNickname().equals(fromJID[1]));

					if (!is_muc || checkAddMucMessage(msg, msg.getPacketID(), fromJID, timestamp)) {
						addChatMessageToDB(direction, fromJID, chatMessage, is_new, ts, msg.getPacketID(), replace_id);
						// only notify on private messages or when MUC notification requested
						boolean need_notify = !is_muc || mConfig.needMucNotification(multiUserChats.get(fromJID[0]).getNickname(), chatMessage);
						// outgoing carbon -> clear notification by signalling 'null' message
						if (is_from_me) {
							mServiceCallBack.notifyMessage(fromJID, null, true, msg.getType());
							// TODO: MUC PMs
							ChatHelper.markAsRead(mService, fromJID[0]);
						} else if (direction == ChatConstants.INCOMING && need_notify)
							mServiceCallBack.notifyMessage(fromJID, chatMessage, (cc != null), msg.getType());
					}
					sendReceiptIfRequested(packet);
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


	private boolean checkAddMucMessage(Message msg, String packet_id, String[] fromJid, DelayInfo timestamp) {
		String muc = fromJid[0];
		String nick = fromJid[1];
		if (timestamp == null) {
			// HACK: remove last outgoing message instead of upserting
			if (multiUserChats.get(muc).getNickname().equals(nick))
				mContentResolver.delete(ChatProvider.CONTENT_URI,
					"jid = ? AND from_me = 1 AND (pid = ? OR message = ?) AND " +
					"_id >= (SELECT MIN(_id) FROM chats WHERE jid = ? ORDER BY _id DESC LIMIT 50)",
					new String[] { muc, packet_id, msg.getBody(), muc });
			return true;
		}

		long ts = timestamp.getStamp().getTime();

		final String[] projection = new String[] {
				ChatConstants._ID, ChatConstants.MESSAGE,
				ChatConstants.JID, ChatConstants.RESOURCE,
				ChatConstants.PACKET_ID
		};

		if (packet_id == null) packet_id = "";
		final String selection = "resource = ? AND (pid = ? OR date = ? OR message = ?) AND _id >= (SELECT MIN(_id) FROM chats WHERE jid = ? ORDER BY _id DESC LIMIT 50)";
		final String[] selectionArgs = new String[] { nick, packet_id, ""+ts, msg.getBody(), muc };
		try {
			Cursor cursor = mContentResolver.query(ChatProvider.CONTENT_URI, projection, selection, selectionArgs, null);
			Log.d(TAG, "message from " + nick + " matched " + cursor.getCount() + " items.");
			boolean result = (cursor.getCount() == 0);
			cursor.close();
			return result;
		} catch (Exception e) { e.printStackTrace(); } // just return true...
		return true;	
	}

	private void handleKickedFromMUC(String room, boolean banned, String actor, String reason) {
		multiUserChats.remove(room);
		ContentValues cvR = new ContentValues();
		String message;
		if (actor != null && actor.length() > 0)
			message = mService.getString(banned ? R.string.muc_banned_by : R.string.muc_kicked_by,
					actor, reason);
		else
			message = mService.getString(banned ? R.string.muc_banned : R.string.muc_kicked,
					reason);
		cvR.put(RosterProvider.RosterConstants.STATUS_MESSAGE, message);
		cvR.put(RosterProvider.RosterConstants.STATUS_MODE, StatusMode.offline.ordinal());
		upsertRoster(cvR, room);
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
					case subscribed:
					case unsubscribe:
					case unsubscribed:
						subscriptionRequests.remove(p.getFrom());
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

	private void addChatMessageToDB(int direction, String[] tJID,
			String message, int delivery_status, long ts, String packetID, String replaceID) {
		ContentValues values = new ContentValues();

		values.put(ChatConstants.DIRECTION, direction);
		values.put(ChatConstants.JID, tJID[0]);
		values.put(ChatConstants.RESOURCE, tJID[1]);
		values.put(ChatConstants.MESSAGE, message);
		values.put(ChatConstants.DELIVERY_STATUS, delivery_status);
		values.put(ChatConstants.DATE, ts);
		values.put(ChatConstants.PACKET_ID, packetID);

		if (replaceID != null) {
			// obtain row id for last message with that full JID, or -1
			long _id = getRowIdForMessage(tJID[0], tJID[1], direction, replaceID);
			Log.d(TAG, "Replacing last message from " + tJID[0] + "/" + tJID[1] + ": " + replaceID + " -> " + packetID);
			Uri row = Uri.withAppendedPath(ChatProvider.CONTENT_URI, "" + _id);
			if (_id >= 0 && mContentResolver.update(row, values, null, null) == 1)
				return;
		}
		mContentResolver.insert(ChatProvider.CONTENT_URI, values);
	}

	private void addChatMessageToDB(int direction, String JID,
			String message, int delivery_status, long ts, String packetID) {
		String[] tJID = {JID, ""};
		addChatMessageToDB(direction, tJID, message, delivery_status, ts, packetID, null);
	}

	private ContentValues getContentValuesForRosterEntry(final RosterEntry entry) {
		Presence presence = mRoster.getPresence(entry.getUser());
		return getContentValuesForRosterEntry(entry, presence);
	}

	private ContentValues getContentValuesForRosterEntry(final RosterEntry entry, Presence presence) {
		final ContentValues values = new ContentValues();

		values.put(RosterConstants.JID, entry.getUser());
		values.put(RosterConstants.ALIAS, getName(entry));

		// handle subscription requests and errors with higher priority
		Presence sub = subscriptionRequests.get(entry.getUser());
		if (presence.getType() == Presence.Type.error) {
			String error = presence.getError().getMessage();
			if (error == null || error.length() == 0)
				error = presence.getError().toString();
			values.put(RosterConstants.STATUS_MESSAGE, error);
		} else if (sub != null) {
			presence = sub;
			values.put(RosterConstants.STATUS_MESSAGE, presence.getStatus());
		} else switch (entry.getType()) {
		case to:
		case both:
			// override received presence from roster, using highest-prio entry
			presence = mRoster.getPresence(entry.getUser());
			values.put(RosterConstants.STATUS_MESSAGE, presence.getStatus());
			break;
		case from:
			values.put(RosterConstants.STATUS_MESSAGE, mService.getString(R.string.subscription_status_from));
			presence = null;
			break;
		case none:
			values.put(RosterConstants.STATUS_MESSAGE, "");
			presence = null;
		}
		values.put(RosterConstants.STATUS_MODE, getStatusInt(presence));
		values.put(RosterConstants.GROUP, getGroup(entry.getGroups()));

		return values;
	}

	private void deleteRosterEntryFromDB(final String jabberID) {
		int count = mContentResolver.delete(RosterProvider.CONTENT_URI,
				RosterConstants.JID + " = ?", new String[] { jabberID });
		debugLog("deleteRosterEntryFromDB: Deleted " + count + " entries");
	}

	private void updateRosterEntryInDB(final RosterEntry entry) {
		upsertRoster(getContentValuesForRosterEntry(entry), entry.getUser());
	}

	private void upsertRoster(final ContentValues values, String jid) {
		if (mContentResolver.update(RosterProvider.CONTENT_URI, values,
				RosterConstants.JID + " = ?", new String[] { jid }) == 0) {
			mContentResolver.insert(RosterProvider.CONTENT_URI, values);
		}
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
		return rosterEntry.getUser();
	}

	private StatusMode getStatus(Presence presence) {
		if (presence == null)
			return StatusMode.unknown;
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

	private void cleanupMUCs() {
		mContentResolver.delete(RosterProvider.CONTENT_URI,
				RosterProvider.RosterConstants.GROUP + " = ?",
				new String[] { RosterProvider.RosterConstants.MUCS });
	}

	public synchronized void syncDbRooms() {
		if (!isAuthenticated()) {
			debugLog("syncDbRooms: aborting, not yet authenticated");
		}

		java.util.Set<String> joinedRooms = multiUserChats.keySet();
		Cursor cursor = mContentResolver.query(RosterProvider.MUCS_URI, 
				new String[] {RosterProvider.RosterConstants._ID,
					RosterProvider.RosterConstants.JID, 
					RosterProvider.RosterConstants.PASSWORD, 
					RosterProvider.RosterConstants.NICKNAME}, 
				null, null, null);
		final int ID = cursor.getColumnIndexOrThrow(RosterProvider.RosterConstants._ID);
		final int JID_ID = cursor.getColumnIndexOrThrow(RosterProvider.RosterConstants.JID);
		final int PASSWORD_ID = cursor.getColumnIndexOrThrow(RosterProvider.RosterConstants.PASSWORD);
		final int NICKNAME_ID = cursor.getColumnIndexOrThrow(RosterProvider.RosterConstants.NICKNAME);
		
		mucJIDs.clear();
		while(cursor.moveToNext()) {
			int id = cursor.getInt(ID);
			String jid = cursor.getString(JID_ID);
			String password = cursor.getString(PASSWORD_ID);
			String nickname = cursor.getString(NICKNAME_ID);
			mucJIDs.add(jid);
			//debugLog("Found MUC Room: "+jid+" with nick "+nickname+" and pw "+password);
			if(!joinedRooms.contains(jid)) {
				debugLog("room " + jid + " isn't joined yet, i wanna join...");
				joinRoomAsync(jid, nickname, password); // TODO: make historyLen configurable
			} else {
				MultiUserChat muc = multiUserChats.get(jid);
				if (!muc.getNickname().equals(nickname)) {
					debugLog("room " + jid + ": changing nickname to " + nickname);
					try {
						muc.changeNickname(nickname);
					} catch (XMPPException e) {
						Log.e(TAG, "Changing nickname failed.");
						e.printStackTrace();
					}
				}
			}
			//debugLog("found data in contentprovider: "+jid+" "+password+" "+nickname);
		}
		cursor.close();
		
		for(String room : new HashSet<String>(joinedRooms)) {
			if(!mucJIDs.contains(room)) {
				quitRoom(room);
			}
		}
	}
	
	protected void handleMucInvitation(Message msg) {
		MUCUser mucuser = (MUCUser)msg.getExtension("x", "http://jabber.org/protocol/muc#user");
		mServiceCallBack.mucInvitationReceived(
				msg.getFrom(),
				mucuser.getPassword(),
				msg.getBody()
				);
	}
	
	private Map<String,Runnable> ongoingMucJoins = new java.util.concurrent.ConcurrentHashMap<String, Runnable>();
	private void joinRoomAsync(final String room, final String nickname, final String password) {
		if (ongoingMucJoins.containsKey(room))
			return;
		Thread joiner = new Thread() {
			@Override
			public void run() {
				Log.d(TAG, "async joining " + room);
				boolean result = joinRoom(room, nickname, password);
				Log.d(TAG, "async joining " + room + " done: " + result);
				ongoingMucJoins.remove(room);
			}
		};
		ongoingMucJoins.put(room, joiner);
		joiner.start();
	}

	private boolean joinRoom(final String room, String nickname, String password) {
		MultiUserChat muc = new MultiUserChat(mXMPPConnection, room);
		muc.addUserStatusListener(new org.jivesoftware.smackx.muc.DefaultUserStatusListener() {
			@Override
			public void kicked(String actor, String reason) {
				debugLog("Kicked from " + room + " by " + actor + ": " + reason);
				handleKickedFromMUC(room, false, actor, reason);
			}
			@Override
			public void banned(String actor, String reason) {
				debugLog("Banned from " + room + " by " + actor + ": " + reason);
				handleKickedFromMUC(room, true, actor, reason);
			}
		});
		
		DiscussionHistory history = new DiscussionHistory();
		final String[] projection = new String[] {
				ChatConstants._ID, ChatConstants.DATE,
				ChatConstants.JID, ChatConstants.MESSAGE,
				ChatConstants.PACKET_ID
		};
		final String selection = String.format("%s = '%s'", projection[2], room);
		Cursor cursor = mContentResolver.query(ChatProvider.CONTENT_URI, projection, 
				selection, null, "date DESC LIMIT 1");
		if(cursor.getCount()>0) {
			cursor.moveToFirst();
			long lastDate = cursor.getLong( cursor.getColumnIndexOrThrow(projection[1]) );
			String msg =  cursor.getString( cursor.getColumnIndexOrThrow(projection[3]) );
			Log.d(TAG, String.format("joining room %s i found %d rows of last date %d with msg %s, setting since to %s", room, cursor.getCount(), lastDate, msg, (new Date(lastDate)).toString()) );
			history.setSince( new Date(lastDate) );
		} else Log.d(TAG, "found no old DB messages");
		cursor.close();
		
		ContentValues cvR = new ContentValues();
		cvR.put(RosterProvider.RosterConstants.JID, room);
		cvR.put(RosterProvider.RosterConstants.ALIAS, room);
		cvR.put(RosterProvider.RosterConstants.STATUS_MESSAGE, mService.getString(R.string.muc_synchronizing));
		cvR.put(RosterProvider.RosterConstants.STATUS_MODE, StatusMode.dnd.ordinal());
		cvR.put(RosterProvider.RosterConstants.GROUP, RosterProvider.RosterConstants.MUCS);
		upsertRoster(cvR, room);
		try {
			Presence force_resync = new Presence(Presence.Type.unavailable);
			force_resync.setTo(room + "/" + nickname);
			mXMPPConnection.sendPacket(force_resync);
			muc.join(nickname, password, history, 10*PACKET_TIMEOUT);
		} catch (Exception e) {
			Log.e(TAG, "Could not join MUC-room "+room);
			e.printStackTrace();
			cvR.put(RosterProvider.RosterConstants.STATUS_MESSAGE, "Error: " + e.getLocalizedMessage());
			cvR.put(RosterProvider.RosterConstants.STATUS_MODE, StatusMode.offline.ordinal());
			upsertRoster(cvR, room);
			return false;
		}

		if(muc.isJoined()) {
			multiUserChats.put(room, muc);
			String roomname = room.split("@")[0];
			try {
				RoomInfo ri = MultiUserChat.getRoomInfo(mXMPPConnection, room);
				String rn = ri.getRoomName();
				if (rn != null && rn.length() > 0)
					roomname = rn;
			} catch (XMPPException e) {
				// ignore a failed room info request
				Log.d(TAG, "MUC room IQ failed: " + room);
				e.printStackTrace();
			}
			// delay requesting subject until room info IQ returned/failed
			String subject = muc.getSubject();
			cvR.put(RosterProvider.RosterConstants.ALIAS, roomname);
			cvR.put(RosterProvider.RosterConstants.STATUS_MESSAGE, subject);
			cvR.put(RosterProvider.RosterConstants.STATUS_MODE, StatusMode.available.ordinal());
			upsertRoster(cvR, room);
			return true;
		}
		
		return false;
	}

	@Override
	public void sendMucMessage(String room, String message) {
		Message newMessage = new Message(room, Message.Type.groupchat);
		newMessage.setBody(message);
		addChatMessageToDB(ChatConstants.OUTGOING, room, message, ChatConstants.DS_NEW,
				System.currentTimeMillis(), newMessage.getPacketID());
		mXMPPConnection.sendPacket(newMessage);
	}

	private void quitRoom(String room) {
		MultiUserChat muc = multiUserChats.get(room); 
		muc.leave();
		multiUserChats.remove(room);
		mContentResolver.delete(RosterProvider.CONTENT_URI, "jid LIKE ?", new String[] {room});
	}

	@Override
	public boolean inviteToRoom(String contactJid, String roomJid) {
		MultiUserChat muc = multiUserChats.get(roomJid);
		if(contactJid.contains("/")) {
			contactJid = contactJid.split("/")[0];
		}
		Log.d(TAG, "invitng contact: "+contactJid+" to room: "+muc);
		muc.invite(contactJid, "User "+contactJid+" has invited you to a chat!");
		return false;
	}

	@Override
	public List<ParcelablePresence> getUserList(String jid) {
		MultiUserChat muc = multiUserChats.get(jid);
		if (muc == null) {
			return null;
		}
		Iterator<String> occIter = muc.getOccupants();
		ArrayList<ParcelablePresence> tmpList = new ArrayList<ParcelablePresence>();
		while(occIter.hasNext())
			tmpList.add(new ParcelablePresence(muc.getOccupantPresence(occIter.next())));
		Collections.sort(tmpList, new Comparator<ParcelablePresence>() {
			@Override
			public int compare(ParcelablePresence lhs, ParcelablePresence rhs) {
				return java.text.Collator.getInstance().compare(lhs.resource, rhs.resource);
			}
		});
		return tmpList;
	}
}
