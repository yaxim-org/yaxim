package org.yaxim.androidclient.service;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

import de.duenndns.ssl.MemorizingTrustManager;

import org.jivesoftware.smack.*;
import org.jivesoftware.smack.filter.StanzaTypeFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.IQ.Type;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Presence.Mode;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.packet.StreamError;
import org.jivesoftware.smack.packet.StanzaError;
import org.jivesoftware.smack.packet.id.StanzaIdUtil;
import org.jivesoftware.smack.parsing.ParsingExceptionCallback;
import org.jivesoftware.smack.UnparseableStanza;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smack.roster.RosterGroup;
import org.jivesoftware.smack.roster.RosterListener;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smack.util.TLSUtils;
import org.jivesoftware.smack.util.dns.HostAddress;
import org.jivesoftware.smackx.carbons.packet.CarbonExtension;
import org.jivesoftware.smackx.csi.ClientStateIndicationManager;
import org.jivesoftware.smackx.delay.DelayInformationManager;
import org.jivesoftware.smackx.httpfileupload.HttpFileUploadManager;
import org.jivesoftware.smackx.iqregister.AccountManager;
import org.jivesoftware.smackx.iqversion.VersionManager;
import org.jivesoftware.smackx.message_correct.element.MessageCorrectExtension;
import org.jivesoftware.smackx.muc.MucEnterConfiguration;
import org.jivesoftware.smackx.muc.MultiUserChatManager;
import org.jivesoftware.smackx.nick.packet.Nick;
import org.jivesoftware.smackx.ping.PingFailedListener;
import org.jivesoftware.smackx.ping.PingManager;
import org.jivesoftware.smackx.bookmarks.BookmarkManager;
import org.jivesoftware.smackx.bookmarks.BookmarkedConference;
import org.jivesoftware.smackx.caps.EntityCapsManager;
import org.jivesoftware.smackx.caps.cache.SimpleDirectoryPersistentCache;
import org.jivesoftware.smackx.muc.packet.GroupChatInvitation;
import org.jivesoftware.smackx.disco.ServiceDiscoveryManager;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.RoomInfo;
import org.jivesoftware.smackx.carbons.CarbonManager;
import org.jivesoftware.smackx.disco.packet.DiscoverItems;
import org.jivesoftware.smackx.pubsub.LeafNode;
import org.jivesoftware.smackx.pubsub.PayloadItem;
import org.jivesoftware.smackx.pubsub.PubSubManager;
import org.jivesoftware.smackx.vcardtemp.VCardManager;
import org.jivesoftware.smackx.vcardtemp.packet.VCard;
import org.jivesoftware.smackx.delay.packet.DelayInformation;
import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
import org.jivesoftware.smackx.muc.packet.MUCUser;
import org.jivesoftware.smackx.ping.packet.Ping;
import org.jivesoftware.smackx.receipts.DeliveryReceipt;
import org.jivesoftware.smackx.receipts.DeliveryReceiptRequest;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.EntityFullJid;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Localpart;
import org.jxmpp.jid.parts.Resourcepart;
import org.jxmpp.stringprep.XmppStringprepException;
import org.yaxim.androidclient.YaximApplication;
import org.yaxim.androidclient.data.ChatHelper;
import org.yaxim.androidclient.data.ChatProvider;
import org.yaxim.androidclient.data.ChatRoomHelper;
import org.yaxim.androidclient.data.EntityInfo;
import org.yaxim.androidclient.data.RosterProvider;
import org.yaxim.androidclient.data.YaximConfiguration;
import org.yaxim.androidclient.data.ChatProvider.ChatConstants;
import org.yaxim.androidclient.data.RosterProvider.RosterConstants;
import org.yaxim.androidclient.exceptions.YaximXMPPException;
import org.yaxim.androidclient.packet.MuclumbusIQ;
import org.yaxim.androidclient.packet.MuclumbusResult;
import org.yaxim.androidclient.packet.Oob;
import org.yaxim.androidclient.packet.PreAuth;
import org.yaxim.androidclient.util.ConnectionState;
import org.yaxim.androidclient.util.ErrorReportManager;
import org.yaxim.androidclient.util.LogConstants;
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
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;

public class SmackableImp implements Smackable {
	final static private String TAG = "yaxim.SmackableImp";

	final static private int PACKET_TIMEOUT = 30000;

	final static private String[] SEND_OFFLINE_PROJECTION = new String[] {
			ChatConstants._ID, ChatConstants.JID,
			ChatConstants.MESSAGE, ChatConstants.MSGFLAGS,
			ChatConstants.CORRECTION, ChatConstants.EXTRA,
			ChatConstants.DATE, ChatConstants.PACKET_ID };
	final static private String SEND_OFFLINE_SELECTION =
			ChatConstants.DIRECTION + " = " + ChatConstants.OUTGOING + " AND " +
			ChatConstants.DELIVERY_STATUS + " = " + ChatConstants.DS_NEW;

	static File capsCacheDir = null; ///< this is used to cache if we already initialized EntityCapsCache

	static {
		// initialize smack defaults before any connections are created
		SmackConfiguration.setDefaultReplyTimeout(PACKET_TIMEOUT);

		ProviderManager.addExtensionProvider(Oob.ELEMENT, Oob.NAMESPACE, new Oob.Provider());
		ProviderManager.addExtensionProvider(PreAuth.ELEMENT, PreAuth.NAMESPACE, new PreAuth.Provider());
		ProviderManager.addIQProvider(MuclumbusIQ.ELEMENT, MuclumbusIQ.NAMESPACE, new MuclumbusIQ.Provider());
		ProviderManager.addIQProvider(MuclumbusResult.ELEMENT, MuclumbusIQ.NAMESPACE, new MuclumbusResult.Provider());
		PingManager.setDefaultPingInterval(3*60);
	}

	private final YaximConfiguration mConfig;
	private XMPPTCPConnectionConfiguration mXMPPConfig;
	private XMPPTCPConnection mXMPPConnection;
	private AtomicReference<Thread> mConnectingThread = new AtomicReference<Thread>();


	private ConnectionState mRequestedState = ConnectionState.OFFLINE;
	private ConnectionState mState = ConnectionState.OFFLINE;
	private String mLastError;
	private long mLastOnline = 0;	//< timestamp of last successful full login (XEP-0198 does not count)
	private long mLastOffline = 0;	//< timestamp of the end of last successful login

	private XMPPServiceCallback mServiceCallBack;
	private Roster mRoster;
	private RosterListener mRosterListener;
	private StanzaListener mStanzaListener;
	private StanzaListener mPresenceListener;
	private ConnectionListener mConnectionListener;

	private final ContentResolver mContentResolver;

	private AlarmManager mAlarmManager;
	private StanzaListener mPongListener;
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
	
	private final HashSet<String> mucJIDs = new HashSet<String>();	//< all configured MUCs, joined or not
	private Map<String, MUCController> multiUserChats = new HashMap<String, MUCController>();
	private long mucLastPing = 0;
	private long mucPreviousPing = 0;
	private Map<String, Presence> subscriptionRequests = new HashMap<String, Presence>();


	public SmackableImp(YaximConfiguration config,
			ContentResolver contentResolver,
			Service service) {
		this.mConfig = config;
		this.mContentResolver = contentResolver;
		this.mService = service;
		this.mAlarmManager = (AlarmManager)mService.getSystemService(Context.ALARM_SERVICE);

		mLastOnline = mLastOffline = System.currentTimeMillis();
		mPingAlarmPendIntent = PendingIntent.getBroadcast(mService.getApplicationContext(), 0, mPingAlarmIntent,
					PendingIntent.FLAG_UPDATE_CURRENT);
		mPongTimeoutAlarmPendIntent = PendingIntent.getBroadcast(mService.getApplicationContext(), 0, mPongTimeoutAlarmIntent,
					PendingIntent.FLAG_UPDATE_CURRENT);

		DiscoverInfo.Identity yaxim_identity = new DiscoverInfo.Identity("client",
				service.getString(R.string.app_name),
				YaximApplication.XMPP_IDENTITY_TYPE);
		ServiceDiscoveryManager.setDefaultIdentity(yaxim_identity);
		EntityCapsManager.setDefaultEntityNode("https://yaxim.org/");
	}
		
	// this code runs a DNS resolver, might be blocking
	private synchronized void initXMPPConnection() throws YaximXMPPException {
		XMPPTCPConnectionConfiguration.Builder cb;
		// allow custom server / custom port to override SRV record
		try {
			if (mConfig.customServer.length() > 0)
				cb = XMPPTCPConnectionConfiguration.builder()
						.setXmppDomain(mConfig.server)
						.setHostAddressByNameOrIp(mConfig.customServer)
						.setPort(mConfig.port);
			else
				cb = XMPPTCPConnectionConfiguration.builder()
						.setXmppDomain(mConfig.server);
		} catch (XmppStringprepException e) {
			throw new YaximXMPPException("Invalid server name", e);
		}
		cb.setLanguage(Locale.getDefault());
		cb.setSendPresence(false);
		cb.setCompressionEnabled(false); // disable for now
		SmackConfiguration.DEBUG = mConfig.smackdebug;
		if (mConfig.require_ssl)
			cb.setSecurityMode(ConnectionConfiguration.SecurityMode.required);

		// register MemorizingTrustManager for XMPP and HTTPS
		try {
			TLSUtils.setTLSOnly(cb);
			SSLContext sc = SSLContext.getInstance("TLS");
			MemorizingTrustManager mtm = YaximApplication.getApp().mMTM;
			sc.init(null, new X509TrustManager[] { mtm },
					new java.security.SecureRandom());
			// XMPP
			cb.setCustomSSLContext(sc);
			cb.setHostnameVerifier(mtm.wrapHostnameVerifier(
						new org.apache.http.conn.ssl.StrictHostnameVerifier()));
			// HTTPS (for HTTP Upload)
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
			HttpsURLConnection.setDefaultHostnameVerifier(mtm.wrapHostnameVerifier(
						HttpsURLConnection.getDefaultHostnameVerifier()));
		} catch (java.security.GeneralSecurityException e) {
			debugLog("initialize MemorizingTrustManager: " + e);
		}

		mXMPPConfig = cb.build();
		this.mXMPPConnection = new XMPPTCPConnection(mXMPPConfig);
		mXMPPConnection.setParsingExceptionCallback(new ParsingExceptionCallback() {
			@Override
			public void handleUnparsableStanza(UnparseableStanza up) throws Exception {
				Exception e = up.getParsingException();
				// work around Smack throwing up on mod_mam_archive bug
				if (e.getMessage().equals("variable cannot be null")) {
					debugLog("Ignoring invalid disco#info caused by https://prosody.im/issues/issue/870");
					e.printStackTrace();
					return;
				}
				Log.e(TAG, "parsing exception", e);
				ErrorReportManager.get(mService).report(e);
			}
		});

		mXMPPConnection.addStanzaAcknowledgedListener(new StanzaListener() {
			@Override
			public void processStanza(Stanza packet) throws SmackException.NotConnectedException, InterruptedException, SmackException.NotLoggedInException {
				//SMAXX per stanza acks
			}
		});
		mXMPPConnection.addStanzaDroppedListener(new StanzaListener() {
			@Override
			public void processStanza(Stanza packet) throws SmackException.NotConnectedException, InterruptedException, SmackException.NotLoggedInException {
				if (packet instanceof Message && packet.getTo() != null) {
					changeMessageDeliveryStatus(packet.getTo().toString(), packet.getStanzaId(), ChatConstants.DS_NEW);
				}
			}
		});

		registerConnectionListener();
		registerRosterListener();
		registerMessageListener();
		registerPresenceListener();
		registerPongListener();
		PingManager.getInstanceFor(mXMPPConnection).registerPingFailedListener(new PingFailedListener() {
			@Override
			public void pingFailed() {
				Log.w(TAG, "PingFailedListener invoked!");
				onDisconnected(mService.getString(R.string.conn_ping_timeout));
			}
		});

		mConfig.reconnect_required = false;

		multiUserChats = new HashMap<String, MUCController>();
		initServiceDiscovery();
	}

	// blocking, run from a thread!
	public void doConnect(boolean create_account) throws YaximXMPPException {
		mRequestedState = ConnectionState.ONLINE;
		updateConnectionState(ConnectionState.CONNECTING);
		if (mXMPPConnection == null || mConfig.reconnect_required)
			initXMPPConnection();
		connectAndLogin(create_account);
		if (!mXMPPConnection.isAuthenticated())
			throw new YaximXMPPException("SMACK connected, but authentication failed");
		updateConnectionState(ConnectionState.LOADING);
		Log.d(TAG, "connect has returned!");
		syncDbRooms();
		sendOfflineMessages(null);
		try {
			sendUserWatching();
		} catch (Exception e) {
			throw new YaximXMPPException("sendUserWatching", e);
		}
		registerPingAlarm();
		// we need to "ping" the service to let it know we are actually
		// connected, even when no roster entries will come in
		if (mState == ConnectionState.LOADING)
			updateConnectionState(ConnectionState.ONLINE);
		else throw new YaximXMPPException("Connection state after connect is " + mState);
	}

	// BLOCKING, call on a new Thread!
	private void updateConnectingThread(Thread new_thread) {
		if (new_thread == null) {
			// unset current thread if it's still set
			mConnectingThread.compareAndSet(Thread.currentThread(), null);
			return;
		}
		synchronized (mConnectingThread) {
			if (mConnectingThread.get() == null) {
				// no contention, just update
				mConnectingThread.set(new_thread);
			} else try {
				Thread old = mConnectingThread.get();
				Log.d(TAG, "updateConnectingThread: old thread (" + old + ") is still running, killing it.");
				old.interrupt();
				old.join(0);
				Log.d(TAG, "updateConnectingThread: killed old thread.");
			} catch (InterruptedException e) {
				Log.d(TAG, "updateConnectingThread: failed to join(): " + e);
			} finally {
				mConnectingThread.set(new_thread);
			}
		}
	}
	private void finishConnectingThread() {
		updateConnectingThread(null);
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

				new Thread("connect") {
					@Override
					public void run() {
						if (mConnectingThread.get() != null && mXMPPConnection != null) {
							// hack: we are not connected yet, so try to abort connect
							Log.e(TAG, "Ongoing connection attempt!");
							//mXMPPConnection.abortConnect();
						}
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

				new Thread("instantShutdown") {
					public void run() {
						if (mConnectingThread.get() != null && mXMPPConnection != null) {
							// hack: we are not connected yet, so try to abort connect
							Log.e(TAG, "Ongoing connection attempt!");
							//mXMPPConnection.abortConnect();
						}
						updateConnectingThread(this);
						mXMPPConnection.instantShutdown();
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
				new Thread("disconnect") {
					public void run() {
						if (mConnectingThread.get() != null && mXMPPConnection != null) {
							// hack: we are not connected yet, so try to abort connect
							Log.e(TAG, "Ongoing connection attempt!");
							//mXMPPConnection.abortConnect();
						}
						updateConnectingThread(this);
						mXMPPConnection.disconnect();
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

	@Override
	public long getConnectionStateTimestamp() {
		return (mState == ConnectionState.ONLINE) ? mLastOnline : mLastOffline;
	}

	// called at the end of a state transition
	private synchronized void updateConnectionState(ConnectionState new_state) {
		if (new_state == ConnectionState.ONLINE || new_state == ConnectionState.LOADING || new_state == ConnectionState.RECONNECT_NETWORK)
			mLastError = null;
		Log.d(TAG, "updateConnectionState: " + mState + " -> " + new_state + " (" + mLastError + ")");
		if (new_state == mState)
			return;
		if (mState == ConnectionState.ONLINE)
			mLastOffline = System.currentTimeMillis();
		else if (mState == ConnectionState.OFFLINE || mState == ConnectionState.DISCONNECTED) {
			// clean up roster and MUCs
			removeOldRosterIfNeeded();
		}
		mState = new_state;
		if (mServiceCallBack != null)
			mServiceCallBack.connectionStateChanged(new_state);
	}
	private void initServiceDiscovery() {
		// register connection features
		ServiceDiscoveryManager sdm = ServiceDiscoveryManager.getInstanceFor(mXMPPConnection);

		// init Entity Caps manager with storage in app's cache dir
		if (capsCacheDir == null) {
			capsCacheDir = new File(mService.getCacheDir(), "entity-caps-cache");
			capsCacheDir.mkdirs();
			EntityCapsManager.setPersistentCache(new SimpleDirectoryPersistentCache(capsCacheDir));
		}

		// manually add LMC feature (XEP-0308) because it lacks a Manager

		sdm.addFeature(MessageCorrectExtension.NAMESPACE);
		// same for OOB (XEP-0066)
		sdm.addFeature(Oob.NAMESPACE);

		// set Version for replies
		String app_name = mService.getString(R.string.app_name);
		String build_revision = mService.getString(R.string.build_revision);
		VersionManager.setAutoAppendSmackVersion(false); // WTF flow? Why are you doing this to me?
		VersionManager.getInstanceFor(mXMPPConnection).setVersion(
				app_name, build_revision, "Android");
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
		RosterEntry rosterEntry;
		try {
			rosterEntry = mRoster.getEntry(JidCreate.bareFrom(user));
		} catch (XmppStringprepException e) {
			e.printStackTrace();
			throw new YaximXMPPException("Invalid JID", e);
		}

		if (!(newName.length() > 0) || (rosterEntry == null)) {
			throw new YaximXMPPException("JabberID to rename is invalid!");
		}
		try {
			rosterEntry.setName(newName);
		} catch (Exception e) {
			e.printStackTrace();
			throw new YaximXMPPException("Roster change failed", e);
		}
	}

	public void addRosterGroup(String group) {
		mRoster.createGroup(group);
	}

	public void renameRosterGroup(String group, String newGroup) throws YaximXMPPException {
		RosterGroup groupToRename = mRoster.getGroup(group);
		try {
			groupToRename.setName(newGroup);
		} catch (Exception e) {
			throw new YaximXMPPException("renameRosterGroup", e);
		}
	}

	public void moveRosterItemToGroup(String user, String group)
			throws YaximXMPPException {
		tryToMoveRosterEntryToGroup(user, group);
	}

	public void sendPresenceRequest(String user, String type) throws YaximXMPPException {
		// HACK: remove the fake roster entry added by handleIncomingSubscribe()
		if (user == null) {
			for (String[] jid_name : ChatHelper.getRosterContacts(mService, ChatHelper.ROSTER_FILTER_SUBSCRIPTIONS)) {
				sendPresenceRequest(jid_name[0], type);
			}
			return;
		}
		subscriptionRequests.remove(user);
		if ("unsubscribed".equals(type))
			deleteRosterEntryFromDB(user);
		Presence response = new Presence(Presence.Type.valueOf(type));
		response.setTo(user);
		try {
			mXMPPConnection.sendStanza(response);
		} catch (Exception e) {
			throw new YaximXMPPException("sendPresenceRequest", e);
		}
	}
	
	@Override
	public String changePassword(String newPassword) {
		try {
			AccountManager.getInstance(mXMPPConnection).changePassword(newPassword);
			return "OK"; //HACK: hard coded string to differentiate from failure modes
		} catch (XMPPException.XMPPErrorException e) {
			if (e.getStanzaError() != null)
				return e.getStanzaError().toString();
			else
				return e.getLocalizedMessage();
		} catch (Exception e) {
			e.printStackTrace();
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
		if (reason instanceof YaximXMPPException && reason.getCause() != null)
			reason = reason.getCause();
		if (reason instanceof XMPPException.StreamErrorException) {
			StreamError se = ((XMPPException.StreamErrorException)reason).getStreamError();
			if (se != null && se.getCondition().equals(StreamError.Condition.conflict))
				mConfig.generateNewResource();
			// XXX: getCondition() isn't human-readable, but we lack i18n for it yet
			onDisconnected(se.getCondition() + " " + se.getDescriptiveText());
			return;
		}
		// iterate through to the deepest exception
		while (reason.getCause() != null && !(reason.getCause().getClass().getSimpleName().equals("GaiException")))
			reason = reason.getCause();
		if (reason instanceof SmackException.ConnectionException) {
			List<HostAddress> fail = ((SmackException.ConnectionException)reason).getFailedAddresses();
			if (fail.size() > 0) {
				reason = fail.get(0).getExceptions().values().iterator().next();
				// reiterate on the inner reason
				while (reason.getCause() != null && !(reason.getCause().getClass().getSimpleName().equals("GaiException")))
					reason = reason.getCause();
			}
		}
		onDisconnected(reason.getLocalizedMessage());
	}

	private void registerConnectionListener() {
		if (mConnectionListener != null)
			mXMPPConnection.removeConnectionListener(mConnectionListener);
		mConnectionListener = new ConnectionListener() {
			public void connected(XMPPConnection c) {
				Log.d(TAG, "connected");
			}
			public void authenticated(XMPPConnection c, boolean resumed) {
				Log.d(TAG, "authenticated, resumed=" + resumed);
				gotServerPong("connection");
				if (!resumed) {
					mLastOnline = System.currentTimeMillis();
					cleanupMUCsRoster(true);
					cleanupMUCsList(); /* TODO: this is a workaround for smack4 not updating the list */
					setStatusFromConfig();
					discoverServicesAsync();
				}
			}
			public void connectionClosedOnError(Exception e) {
				// XXX: this is the only callback we get from errors, so
				// we need to check for non-resumability and work around
				// here:
				if (!mXMPPConnection.isSmResumptionPossible()) {
					cleanupMUCsList();
				}
				onDisconnected(e);
			}
			public void connectionClosed() {
				// TODO: fix reconnect when we got kicked by the server or SM failed!
				//onDisconnected(null);
				cleanupMUCsList();
				updateConnectionState(ConnectionState.OFFLINE);
			}
			public void reconnectingIn(int seconds) { }
			public void reconnectionFailed(Exception e) { }
			public void reconnectionSuccessful() { }
		};
		mXMPPConnection.addConnectionListener(mConnectionListener);
	}
	/** establishes an XMPP connection and performs login / account creation.
	 *
	 * @param create_account
	 * @return true if this is a new session, as opposed to a resumed one
	 * @throws YaximXMPPException
	 */
	private void connectAndLogin(boolean create_account) throws YaximXMPPException {
		try {
			try {
				debugLog("connectAndLogin: force-instant-shutdown!");
				mXMPPConnection.instantShutdown(); // blocking shutdown prior to re-connection
			} catch (Exception e) {
				debugLog("conn.shutdown() failed, ignoring: " + e);
			}
			debugLog("connectAndLogin: entering synchronized mXMPPConnection");
			synchronized (mXMPPConnection) {
				debugLog("connectAndLogin: within synchronized mXMPPConnection");
				mXMPPConnection.connect();
				if (create_account) {
					Log.d(TAG, "creating new server account...");
					AccountManager am = AccountManager.getInstance(mXMPPConnection);
					am.createAccount(Localpart.from(mConfig.userName), mConfig.password);
				}
				mXMPPConnection.login(mConfig.userName, mConfig.password,
						Resourcepart.from(mConfig.ressource));
			}
			debugLog("connectAndLogin: left synchronized mXMPPConnection");
			Log.d(TAG, "SM: can resume = " + mXMPPConnection.isSmResumptionPossible());
		} catch (Exception e) {
			// actually we just care for IllegalState or NullPointer or XMPPEx.
			throw new YaximXMPPException("connectAndLogin failed", e);
		}
	}

	private void tryToMoveRosterEntryToGroup(String userName, String groupName)
			throws YaximXMPPException {
		try {
			RosterGroup rosterGroup = getRosterGroup(groupName);
			RosterEntry rosterEntry = mRoster.getEntry(JidCreate.bareFrom(userName));
			removeRosterEntryFromGroups(rosterEntry);

			if (groupName.length() == 0)
				return;
			else {
				rosterGroup.addEntry(rosterEntry);
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new YaximXMPPException("tryToMoveRosterEntryToGroup", e);
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
		} catch (Exception e) {
			throw new YaximXMPPException("tryToRemoveUserFromGroup", e);
		}
	}

	private void tryToRemoveRosterEntry(String user) throws YaximXMPPException {
		try {
			RosterEntry rosterEntry = mRoster.getEntry(JidCreate.bareFrom(user));

			if (rosterEntry != null) {
				// first, unsubscribe the user
				Presence unsub = new Presence(Presence.Type.unsubscribed);
				unsub.setTo(rosterEntry.getUser());
				mXMPPConnection.sendStanza(unsub);
				// then, remove from roster
				mRoster.removeEntry(rosterEntry);
			}
		} catch (Exception e) {
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
				mXMPPConnection.sendStanza(preauth);
			}
			// add to roster, triggers another sub request by Smack (sigh)
			mRoster.createEntry(JidCreate.bareFrom(user), alias, new String[] { group });
			// send a pre-approval
			Presence pre_approval = new Presence(Presence.Type.subscribed);
			pre_approval.setTo(user);
			mXMPPConnection.sendStanza(pre_approval);
			mConfig.whitelistInvitationJID(user);
		} catch (Exception e) {
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

	private void removeOldRosterIfNeeded() {
		if (!mConfig.rosterreset_required)
			return;
		Log.d(TAG, "removeOldRoster()");
		int count = mContentResolver.delete(RosterProvider.CONTENT_URI, null, null);
		int muc_count = mContentResolver.delete(RosterProvider.MUCS_URI, null, null);
		Log.d(TAG, "deleted " + count + " old roster entries and " + muc_count + " MUCs.");
		mConfig.rosterreset_required = false;
	}

	// HACK: add an incoming subscription request as a fake roster entry
	private void handleIncomingSubscribe(Presence request) {
		// perform Pre-Authenticated Roster Subscription, fallback to manual
		try {
			Jid jid = request.getFrom();
			PreAuth preauth = (PreAuth)request.getExtension(PreAuth.ELEMENT, PreAuth.NAMESPACE);
			String jid_or_token = jid.toString();
			if (preauth != null) {
				jid_or_token = preauth.getToken();
				Log.d(TAG, "PARS: found token " + jid_or_token);
			}
			if (mConfig.redeemInvitationCode(jid_or_token)) {
				Log.d(TAG, "PARS: approving request from " + jid);
				if (mRoster.getEntry(request.getFrom().asBareJid()) != null) {
					// already in roster, only send approval
					Presence response = new Presence(Presence.Type.subscribed);
					response.setTo(jid);
					mXMPPConnection.sendStanza(response);
				} else {
					tryToAddRosterEntry(jid.toString(), null, "", null);
				}
				return;
			}
		} catch (Exception e) {
			Log.d(TAG, "PARS: failed to send response: " + e);
		}

		subscriptionRequests.put(request.getFrom().toString(), request);

		final ContentValues values = new ContentValues();

		values.put(RosterConstants.JID, request.getFrom().toString());
		values.put(RosterConstants.STATUS_MODE, getStatusInt(request));
		values.put(RosterConstants.STATUS_MESSAGE, request.getStatus());
		if (!mRoster.contains(request.getFrom().asBareJid())) {
			// reset alias and group for new entries
			values.put(RosterConstants.ALIAS, request.getFrom().toString());
			values.put(RosterConstants.GROUP, "");
		};
		upsertRoster(values, request.getFrom().toString());
	}

	public void setStatusFromConfig(Presence presence) {
		Mode mode = Mode.valueOf(mConfig.getPresenceMode().toString());
		presence.setMode(mode);
		presence.setStatus(mConfig.statusMessage);
		presence.setPriority(mConfig.priority);
	}
	public void setStatusFromConfig() {
		// TODO: only call this when carbons changed, not on every presence change
		try {
			CarbonManager.getInstanceFor(mXMPPConnection).sendCarbonsEnabled(mConfig.messageCarbons);

			Presence presence = new Presence(Presence.Type.available);
			setStatusFromConfig(presence);
			mXMPPConnection.sendStanza(presence);
			mConfig.presence_required = false;

			Iterator<MUCController> muc_it = multiUserChats.values().iterator();
			while (muc_it.hasNext()) {
				MUCController mucc = muc_it.next();
				MultiUserChat muc = mucc.muc;
				if (!muc.isJoined())
					continue;
				Presence muc_presence = new Presence(presence.getType(),
						presence.getStatus(), presence.getPriority(), presence.getMode());
				muc_presence.setTo(muc.getRoom() + "/" + muc.getNickname());
				mXMPPConnection.sendStanza(muc_presence);
			}
		} catch (SmackException.NotConnectedException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}

	public Message formatMessage(boolean is_muc, String to, String body, String lmc, String oob) {
		final Message newMessage;
		try {
			newMessage = new Message(JidCreate.from(to), is_muc ? Message.Type.groupchat : Message.Type.chat);
		} catch (XmppStringprepException e) {
			e.printStackTrace();
			return null;
		}
		newMessage.setBody(body);
		if (!is_muc)
			newMessage.addExtension(new DeliveryReceiptRequest());
		if (!TextUtils.isEmpty(lmc))
			newMessage.addExtension(new MessageCorrectExtension(lmc));
		if (!TextUtils.isEmpty(oob))
			newMessage.addExtension(new Oob(oob));
		return newMessage;
	}

	public void sendOfflineMessages(String toMUCjid) throws YaximXMPPException {
		boolean is_muc = (toMUCjid != null);
		String selection = SEND_OFFLINE_SELECTION;
		String[] selection_args = null;
		if (is_muc) {
			selection = selection + " AND jid = ?";
			selection_args = new String[] { toMUCjid };
		}
		Cursor cursor = mContentResolver.query(ChatProvider.CONTENT_URI,
				SEND_OFFLINE_PROJECTION, selection,
				selection_args, null);
		final int      _ID_COL = cursor.getColumnIndexOrThrow(ChatConstants._ID);
		final int      JID_COL = cursor.getColumnIndexOrThrow(ChatConstants.JID);
		final int      MSG_COL = cursor.getColumnIndexOrThrow(ChatConstants.MESSAGE);
		final int MSGFLAGS_COL = cursor.getColumnIndexOrThrow(ChatConstants.MSGFLAGS);
		final int      LMC_COL = cursor.getColumnIndexOrThrow(ChatConstants.CORRECTION);
		final int    EXTRA_COL = cursor.getColumnIndexOrThrow(ChatConstants.EXTRA);
		final int       TS_COL = cursor.getColumnIndexOrThrow(ChatConstants.DATE);
		final int PACKETID_COL = cursor.getColumnIndexOrThrow(ChatConstants.PACKET_ID);
		ContentValues mark_sent = new ContentValues();
		mark_sent.put(ChatConstants.DELIVERY_STATUS, ChatConstants.DS_SENT_OR_READ);
		while (cursor.moveToNext()) {
			long _id = cursor.getLong(_ID_COL);
			String toJID = cursor.getString(JID_COL);
			if (!is_muc && mucJIDs.contains(toJID))
				continue;
			String message = cursor.getString(MSG_COL);
			String packetID = cursor.getString(PACKETID_COL);
			int msgFlags = cursor.getInt(MSGFLAGS_COL);
			String lmc = cursor.getString(LMC_COL);
			String extra = cursor.getString(EXTRA_COL);
			String oob = ((msgFlags & ChatConstants.MF_FILE) != 0) ? extra : null;
			long ts = cursor.getLong(TS_COL);
			Log.d(TAG, "sendOfflineMessages: " + toJID + " > " + message);
			final Message newMessage = formatMessage(is_muc, toJID, message, lmc, oob);
			DelayInformation delay = new DelayInformation(new Date(ts));
			newMessage.addExtension(delay);
			//SMAXX newMessage.addExtension(new DelayInfo(delay));
			if ((packetID != null) && (packetID.length() > 0)) {
				newMessage.setPacketID(packetID);
			} else {
				packetID = newMessage.getStanzaId();
			}
			mark_sent.put(ChatConstants.PACKET_ID, packetID);
			mark_sent.put(ChatConstants.MSGFLAGS, msgFlags | ChatConstants.MF_DELAY);
			Uri rowuri = Uri.parse("content://" + ChatProvider.AUTHORITY
				+ "/" + ChatProvider.TABLE_NAME + "/" + _id);
			mContentResolver.update(rowuri, mark_sent,
						null, null);
			try {
				mXMPPConnection.sendStanza(newMessage);		// must be after marking delivered, otherwise it may override the SendFailListener
			} catch (Exception e) {
				throw new YaximXMPPException("sendOfflineMessages", e);
			}
		}
		cursor.close();
	}

	public static ContentValues formatMessageContentValues(int direction, String jid, String resource,
			String message, int msgflags, String lmc, String extra,
			int delivery_status, String packetID) {
		ContentValues values = new ContentValues();
		values.put(ChatConstants.DIRECTION, direction);
		values.put(ChatConstants.JID, jid);
		values.put(ChatConstants.RESOURCE, resource);
		values.put(ChatConstants.MESSAGE, message);
		values.put(ChatConstants.MSGFLAGS, msgflags);
		values.put(ChatConstants.CORRECTION, lmc);
		values.put(ChatConstants.EXTRA, extra);
		values.put(ChatConstants.DELIVERY_STATUS, delivery_status);
		values.put(ChatConstants.PACKET_ID, packetID);
		return values;
	}

	public static void addOfflineMessage(ContentResolver cr, String toJID, String message) {
		ContentValues values = formatMessageContentValues(ChatConstants.OUTGOING, toJID, null,
			message, ChatConstants.MF_TEXT, null, null,
			ChatConstants.DS_NEW, StanzaIdUtil.newStanzaId());
		values.put(ChatConstants.DATE, System.currentTimeMillis());
		cr.insert(ChatProvider.CONTENT_URI, values);
	}

	public void sendReceiptIfRequested(Message packet) {
		DeliveryReceiptRequest drr = (DeliveryReceiptRequest)packet.getExtension(
				DeliveryReceiptRequest.ELEMENT, DeliveryReceipt.NAMESPACE);
		if (drr != null) {
			if (packet.getType() != Message.Type.chat && packet.getType() != Message.Type.normal) {
				Log.w(TAG, "Ignoring receipt request from " + packet.getFrom() + " for type " + packet.getType());
				return;
			}
			Message ack = new Message(packet.getFrom(), packet.getType());
			ack.addExtension(new DeliveryReceipt(packet.getStanzaId()));
			try {
				mXMPPConnection.sendStanza(ack);
			} catch (Exception e) {
				e.printStackTrace();
				// whoops!
			}
		}
	}

	public void sendMessage(String toJID, String message, String lmc, String oob, long upsert_id) {
		final Message newMessage = formatMessage(mucJIDs.contains(toJID), toJID, message, lmc, oob);
		boolean is_auth = isAuthenticated();
		int msgFlags = TextUtils.isEmpty(oob) ? ChatConstants.MF_TEXT : ChatConstants.MF_FILE;
		int deliveryStatus = is_auth ? ChatConstants.DS_SENT_OR_READ : ChatConstants.DS_NEW;
		ContentValues cv = formatMessageContentValues(ChatConstants.OUTGOING, toJID, null,
				message, msgFlags, lmc, oob, deliveryStatus, newMessage.getStanzaId());
		addChatMessageToDB(toJID, cv, 0, upsert_id);
		if (is_auth) {
			try {
				mXMPPConnection.sendStanza(newMessage);
			} catch (Exception e) {
				// send failed
				changeMessageDeliveryStatus(toJID, newMessage.getStanzaId(), ChatConstants.DS_NEW, null);
			}
		}
	}
	public void sendMessage(String toJID, String message) {
		sendMessage(toJID, message, null, null, -1);
	}
	public void sendMessageCorrection(String toJID, String message, String lmc, long upsert_id) {
		sendMessage(toJID, message, lmc, null, upsert_id);
	}

	// method checks whether the XMPP connection is authenticated and fully bound (i.e. after resume/bind)
	public boolean isAuthenticated() {
		if (mXMPPConnection != null) {
			return mXMPPConnection.isConnected() && mXMPPConnection.isAuthenticated() &&
					mState != ConnectionState.CONNECTING;
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
			Roster.getInstanceFor(mXMPPConnection).removeRosterListener(mRosterListener);
			mXMPPConnection.removeAsyncStanzaListener(mStanzaListener);
			mXMPPConnection.removeAsyncStanzaListener(mPresenceListener);

			mXMPPConnection.removeAsyncStanzaListener(mPongListener);
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
		if (jid.contains("/")) { // MUC-PM
			String[] jid_parts = jid.split("/", 2);
			return String.format("%s (%s)", jid_parts[1],
					ChatRoomHelper.getRoomName(mService, jid_parts[0]));
		}
		RosterEntry re = null;
		try {
			re = mRoster.getEntry(JidCreate.bareFrom(jid));
		} catch (XmppStringprepException e) {
			// ignore exception and fall back to JID
		}
		if (null != re && null != re.getName() && re.getName().length() > 0) {
			return re.getName();
		} else if (mucJIDs.contains(jid)) {
			return ChatRoomHelper.getRoomName(mService, jid);
		} else {
			return jid;
		}			
	}

	public long getRowIdForMessage(String jid, String resource, int direction, String packet_id) {
		// query the DB for the RowID, return -1 if packet_id does not match
		// this will check the last 10 messages from that JID
		Cursor c = mContentResolver.query(ChatProvider.CONTENT_URI, new String[] { ChatConstants._ID, ChatConstants.PACKET_ID },
				"jid = ? AND resource = ? AND from_me = ?",
				new String[] { jid, resource, "" + direction }, "_id DESC LIMIT 10");
		long result = -1;
		while(c.moveToNext()) {
			if (c.getString(1).equals(packet_id))
				result = c.getLong(0);
		}
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
		mRoster = Roster.getInstanceFor(mXMPPConnection);
		mRoster.setSubscriptionMode(Roster.SubscriptionMode.manual);

		if (mRosterListener != null)
			mRoster.removeRosterListener(mRosterListener);

		mRosterListener = new RosterListener() {
			private boolean first_roster = true;

			public void entriesAdded(Collection<Jid> entries) {
				debugLog("entriesAdded(" + entries + ")");

				ContentValues[] cvs = new ContentValues[entries.size()];
				int i = 0;
				for (Jid entry : entries) {
					//SMAXX
					RosterEntry rosterEntry = mRoster.getEntry(entry.asBareJid());
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

			public void entriesDeleted(Collection<Jid> entries) {
				debugLog("entriesDeleted(" + entries + ")");

				for (Jid entry : entries) {
					deleteRosterEntryFromDB(entry.toString());
				}
			}

			public void entriesUpdated(Collection<Jid> entries) {
				debugLog("entriesUpdated(" + entries + ")");

				for (Jid entry : entries) {
					//SMAXX
					RosterEntry rosterEntry = mRoster.getEntry(entry.asBareJid());
					updateRosterEntryInDB(rosterEntry);
				}
			}

			public void presenceChanged(Presence presence) {
				debugLog("presenceChanged(" + presence.getFrom() + "): " + presence);

				RosterEntry rosterEntry = mRoster.getEntry(presence.getFrom().asBareJid());
				if (rosterEntry != null)
					upsertRoster(getContentValuesForRosterEntry(rosterEntry, presence),
							rosterEntry.getUser());
			}
		};
		mRoster.addRosterListener(mRosterListener);
	}

	private String getBareJID(String from) {
		String[] res = from.split("/", 2);
		return res[0].toLowerCase();
	}

	/* sanitize a jabber ID obtained from a packet:
	 *  - split into bare JID and resource
	 *  - lowercase the bare JID only
	 *  - fallback to correct default value if empty/null (default is dependent on context/session, therefore must be supplied)
	 */
	private String[] getJabberID(String from, String fallback) {
		if (from == null || from.length() == 0)
			from = fallback;
		if(from.contains("/")) {
			String[] res = from.split("/", 2);
			return new String[] { res[0].toLowerCase(), res[1] };
		} else {
			return new String[] {from.toLowerCase(), ""};
		}
	}
	private String[] getJabberID(Jid from, String fallback) {
		//SMAXX
		return getJabberID(from != null ? from.toString() : null, fallback);
	}

	public boolean changeMessageDeliveryStatus(String jid, String packetID, int new_status, String error) {
		Log.d(TAG, "changeMessageDeliveryStatus: " + jid + " - " + packetID + " --> " + new_status + " " + error);
		ContentValues cv = new ContentValues();
		cv.put(ChatConstants.DELIVERY_STATUS, new_status);
		if (error != null || new_status == ChatConstants.DS_ACKED)
			cv.put(ChatConstants.ERROR, error);
		Uri rowuri = Uri.parse("content://" + ChatProvider.AUTHORITY + "/"
				+ ChatProvider.TABLE_NAME);
		return mContentResolver.update(rowuri, cv,
				ChatConstants.JID + " = ? AND " +
				ChatConstants.PACKET_ID + " = ? AND " +
				ChatConstants.DELIVERY_STATUS + " != " + ChatConstants.DS_ACKED + " AND " +
				ChatConstants.DIRECTION + " = " + ChatConstants.OUTGOING,
				new String[] { jid, packetID }) > 0;
	}
	public boolean changeMessageDeliveryStatus(String jid, String packetID, int new_status) {
		return changeMessageDeliveryStatus(jid, packetID, new_status, null);
	}

	protected boolean is_user_watching = false;
	public void setUserWatching(boolean user_watching) {
		if (is_user_watching == user_watching)
			return;
		is_user_watching = user_watching;
		if (isAuthenticated()) {
			try {
				sendUserWatching();
			} catch (SmackException.NotConnectedException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	protected void sendUserWatching() throws SmackException.NotConnectedException, InterruptedException {
		if (is_user_watching)
			ClientStateIndicationManager.active(mXMPPConnection);
		else
			ClientStateIndicationManager.inactive(mXMPPConnection);
	}

	/** Check the server connection, reconnect if needed.
	 *
	 * This function will try to ping the server if we are connected, and try
	 * to reestablish a connection otherwise.
	 */
	public void sendServerPing() {
		if (!isAuthenticated()) {
			debugLog("Ping: requested, but not connected to server.");
			requestConnectionState(ConnectionState.ONLINE, false);
			return;
		}
		if (mPingID != null) {
			debugLog("Ping: requested, but still waiting for " + mPingID);
			return; // a ping is still on its way
		}

		try {
			if (false /*mXMPPConnection.isSmEnabled()*/) { // disable 0198 ping due to Smack API
				debugLog("Ping: sending SM request");
				mPingID = "0198-ack"; //SMAXX
				mXMPPConnection.requestSmAcknowledgement();
			} else {
				Ping ping = new Ping();
				ping.setType(Type.get);
				ping.setTo(mConfig.server);
				mPingID = ping.getStanzaId();
				debugLog("Ping: sending ping " + mPingID);
				mXMPPConnection.sendStanza(ping);
			}
			// register ping timeout handler: PACKET_TIMEOUT(30s) + 3s
			registerPongTimeout(PACKET_TIMEOUT + 3000, mPingID);
		} catch (Exception e) {
			e.printStackTrace();
			onDisconnected(e);
		}
	}

	private void gotServerPong(String pongID) {
		long latency = System.currentTimeMillis() - mPingTimestamp;
		if (pongID != null && pongID.equals(mPingID))
			Log.i(TAG, String.format("Ping: server latency %1.3fs (%s)",
						latency/1000., pongID));
		else
			Log.i(TAG, String.format("Ping: server latency %1.3fs (estimated, got %s instead of %s)",
						latency/1000., pongID, mPingID));
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
			onDisconnected(mService.getString(R.string.conn_ping_timeout));
		}
	}

	/**
	 * BroadcastReceiver to trigger sending pings to the server
	 */
	private class PingAlarmReceiver extends BroadcastReceiver {
		public void onReceive(Context ctx, Intent i) {
			try {
				Log.d(TAG, "PingAlarmReceiver.onReceive");
				//sendServerPing();
				// ping all MUCs. if no ping was received since last attempt, /cycle
				Iterator<MUCController> muc_it = multiUserChats.values().iterator();
				long ts = System.currentTimeMillis();
				ContentValues cvR = new ContentValues();
				cvR.put(RosterProvider.RosterConstants.STATUS_MESSAGE, mService.getString(R.string.conn_ping_timeout));
				cvR.put(RosterProvider.RosterConstants.STATUS_MODE, StatusMode.unknown.ordinal());
				cvR.put(RosterProvider.RosterConstants.GROUP, RosterProvider.RosterConstants.MUCS);
				while (muc_it.hasNext()) {
					MUCController mucc = muc_it.next();
					MultiUserChat muc = mucc.muc;
					if (!muc.isJoined())
						continue;
					long lastActivity = mucc.lastPong;
					if (mucPreviousPing > 0 && (lastActivity >= 0 || lastActivity < mucLastPing)) {
						// the MUC didn't give us anything in the last two ping rounds
						if (lastActivity < mucPreviousPing) {
							debugLog("Ping timeout from " + muc.getRoom());
							mucc.isTimeout = true;
							//do not leave MUC; the server is unavailable anyway - either it will recover or we will get an error
							//muc.leave();
							CharSequence lastActTime = DateUtils.getRelativeDateTimeString(mService, lastActivity,
								DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0);
							String message = String.format((Locale)null, "%s (%s)",
									mService.getString(R.string.conn_ping_timeout),
									lastActTime);
							cvR.put(RosterProvider.RosterConstants.STATUS_MESSAGE,  message);
							upsertRoster(cvR, muc.getRoom().toString());
						}
						// send a ping if we didn't receive anything during the last ping round, even multiple times in a row
						Ping ping = new Ping();
						ping.setType(Type.get);
						String jid = muc.getRoom() + "/" + muc.getNickname();
						ping.setTo(jid);
						debugLog("Ping: sending ping to " + jid);
						//SMAXX
						try {
							mXMPPConnection.sendStanza(ping);
						} catch (SmackException.NotConnectedException e) {
							e.printStackTrace();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
				syncDbRooms();
				mucPreviousPing = mucLastPing;
				mucLastPing = ts;
			} catch (NullPointerException npe) {
				/* ignore disconnect race condition */
			} catch (IllegalStateException ise) {
				/* ignore disconnect race condition */
			}
		}
	}

	private void rejoinMUC(String jid) {
		MUCController muc = multiUserChats.get(jid);
		if (muc != null) {
			try {
				if (muc.muc.isJoined())
					muc.muc.leave();
			} catch (Exception e) {
				// best effort, just ignore
			}
			syncDbRooms();
		}

	}
	private boolean isValidPingResponse(IQ response) {
		// a 'result' response means, the other party supports ping and responded appropriately
		if (response.getType() == Type.result)
			return true;
		// 'error' can be caused by s2s issues, non-existing destination, solar flares or one of these two:
		//  * 'service-unavailable': official not-supported response as of RFC6120 (§8.4) and XEP-0199 (§4.1)
		//  * 'feature-not-implemented': inoffcial not-supported response from many clients
		if (response.getType() == Type.error) {
			StanzaError e = response.getError();
			return (e.getType() == StanzaError.Type.CANCEL) &&
				(StanzaError.Condition.service_unavailable == e.getCondition() ||
				 StanzaError.Condition.feature_not_implemented == e.getCondition());
		}
		return false;
	}

	/** Updates internal structures for a sender's last activity.
	 *
	 * Currently only used for MUC self-pinging.
	 */
	//SMAXX
	private void registerLastActivity(Jid from_full) {
		//SMAXX: JID
		MUCController mucc = multiUserChats.get(from_full.asBareJid().toString());
		if (mucc != null)
			mucc.setLastActivity();
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
			mXMPPConnection.removeAsyncStanzaListener(mPongListener);

		mPongListener = new StanzaListener() {

			@Override
			public void processStanza(Stanza packet) {
				if (packet == null) return;

				if (packet instanceof IQ && packet.getFrom() != null) {
					IQ pong = (IQ)packet;
					String[] from = getJabberID(pong.getFrom(), null);
					// check for MUC self-ping response
					registerLastActivity(packet.getFrom());
					if (mucJIDs.contains(from[0]) && from[1].equals(getMyMucNick(from[0]))) {
						MUCController mucc = multiUserChats.get(from[0]);
						if (isValidPingResponse(pong) && mucc != null) {
							Log.d(TAG, "Ping: got response from MUC " + from[0]);
							if (mucc.isTimeout) {
								ContentValues cvR = new ContentValues();
								cvR.put(RosterProvider.RosterConstants.STATUS_MESSAGE, mucc.muc.getSubject());
								cvR.put(RosterProvider.RosterConstants.STATUS_MODE, StatusMode.available.ordinal());
								upsertRoster(cvR, mucc.jid);
								mucc.isTimeout = false;
							}
						} else if (pong.getError() != null) {
							Log.d(TAG, "Ping: got error from MUC " + from[0] + ": " + pong.getError());
							rejoinMUC(from[0]);
						}
					}
				}
				if (getJabberID(packet.getFrom(), mConfig.server)[0].equals(mConfig.server)
						&& mPingID != null && mPingID.equals(packet.getStanzaId()))
					gotServerPong(packet.getStanzaId());

			}

		};

		mXMPPConnection.addAsyncStanzaListener(mPongListener, new StanzaTypeFilter(IQ.class));
	}
	void registerPingAlarm() {
		mAlarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, 
				System.currentTimeMillis() + AlarmManager.INTERVAL_FIFTEEN_MINUTES, AlarmManager.INTERVAL_FIFTEEN_MINUTES, mPingAlarmPendIntent);
	}
	private void unregisterPongListener() {
		mAlarmManager.cancel(mPingAlarmPendIntent);
		mAlarmManager.cancel(mPongTimeoutAlarmPendIntent);
	}

	private void registerMessageListener() {
		StanzaTypeFilter filter = new StanzaTypeFilter(Message.class);

		// do not register multiple packet listeners
		if (mStanzaListener != null) {
			mXMPPConnection.removeSyncStanzaListener(mStanzaListener);
			mXMPPConnection.addSyncStanzaListener(mStanzaListener, filter);
			return;
		}

		mStanzaListener = new StanzaListener() {
			public void processStanza(Stanza packet) {
				try {
				if (packet instanceof Message) {
					Message msg = (Message) packet;

					String[] fromJID = getJabberID(msg.getFrom().toString(), mConfig.server);
					
					int direction = ChatConstants.INCOMING;
					CarbonExtension cc = CarbonExtension.from(msg);
					if (cc != null && !msg.getFrom().toString().equalsIgnoreCase(mConfig.jabberID)) {
						Log.w(TAG, "Received illegal carbon from " + msg.getFrom() + "!");
						cc = null;
					}

					// extract timestamp
					long ts;
					DelayInformation timestamp = DelayInformationManager.getDelayInformation(msg);
					if (cc != null) { // Carbon timestamp overrides packet timestamp
						timestamp = cc.getForwarded().getDelayInformation();
						DelayInformation inner_ts = DelayInformationManager.getDelayInformation(cc.getForwarded().getForwardedStanza());
						if (inner_ts != null) // original timestamp wrapped in carbon message overrides "outer" timestamp
							timestamp = inner_ts;
					}
					if (timestamp != null)
						ts = timestamp.getStamp().getTime();
					else
						ts = System.currentTimeMillis();

					// try to extract a carbon
					if (cc != null) {
						msg = (Message)cc.getForwarded().getForwardedStanza();

						// outgoing carbon: fromJID is actually chat peer's JID
						if (cc.getDirection() == CarbonExtension.Direction.sent) {
							fromJID = getJabberID(msg.getTo().toString(), mConfig.jabberID);
							direction = ChatConstants.OUTGOING;
						} else {
							fromJID = getJabberID(msg.getFrom().toString(), mConfig.server);
						}

						// ignore carbon copies of OTR messages sent by broken clients
						if (msg.getBody() != null && msg.getBody().startsWith("?OTR")) {
							Log.i(TAG, "Ignoring OTR carbon from " + msg.getFrom() + " to " + msg.getTo());
							return;
						}
					}
					registerLastActivity(msg.getFrom());

					// check for jabber MUC invitation
					if(direction == ChatConstants.INCOMING && handleMucInvitation(msg)) {
						sendReceiptIfRequested(msg);
						return;
					}

					String chatMessage = msg.getBody();

					boolean is_muc = (msg.getType() == Message.Type.groupchat);
					boolean is_from_me = (direction == ChatConstants.OUTGOING) ||
							(is_muc && fromJID[1].equals(getMyMucNick(fromJID[0])));

					// TODO: catch self-CSN to MUC once sent by yaxim
					if (is_from_me) {
						// perform a message-replace on self-sent MUC message, abort further processing
						if (is_muc && matchOutgoingMucReflection(msg, fromJID))
							return;
						if (timestamp == null) {
							// delayed messages don't trigger active
							Log.d(TAG, "user is active on different device --> Silent mode");
							mServiceCallBack.setGracePeriod(true);
						}
					}

					// handle MUC-PMs: messages from a nick from a known MUC or with
					// an <x> element
					MUCUser muc_x = (MUCUser)msg.getExtension("x", "http://jabber.org/protocol/muc#user");
					boolean is_muc_pm = !is_muc  && !TextUtils.isEmpty(fromJID[1]) &&
							(muc_x != null || mucJIDs.contains(fromJID[0]));

					// TODO: ignoring 'received' MUC-PM carbons, until XSF sorts out shit:
					// - if yaxim is in the MUC, it will receive a non-carbonated copy of
					//   incoming messages, but not of outgoing ones
					// - if yaxim isn't in the MUC, it can't respond anyway
					if (is_muc_pm && !is_from_me && cc != null)
						return;

					if (is_muc_pm) {
						// store MUC-PMs under the participant's full JID, not bare
						//is_from_me = fromJID[1].equals(getMyMucNick(fromJID[0]));
						fromJID[0] = fromJID[0] + "/" + fromJID[1];
						fromJID[1] = null;
						Log.d(TAG, "MUC-PM: " + fromJID[0] + " d=" + direction + " fromme=" + is_from_me);
					}

					// display error inline
					if (msg.getType() == Message.Type.error) {
						StanzaError e = msg.getError();
						String errmsg = e.toString();
						if (mucJIDs.contains(msg.getFrom()) && e.getType() == StanzaError.Type.CANCEL && e.getCondition() == StanzaError.Condition.not_acceptable) {
							// failed attempt to deliver a message to a MUC because we are not joined
							// anymore. If message ID is known, mark as NEW, trigger rejoin.
							if (changeMessageDeliveryStatus(fromJID[0], msg.getStanzaId(), ChatConstants.DS_NEW, errmsg)) {
								rejoinMUC(fromJID[0]);
								return;
							}
						}
						if (changeMessageDeliveryStatus(fromJID[0], msg.getStanzaId(), ChatConstants.DS_FAILED, errmsg))
							mServiceCallBack.notifyMessage(fromJID, errmsg, (cc != null), Message.Type.error);
						else if (mucJIDs.contains(msg.getFrom())) {
							handleKickedFromMUC(msg.getFrom().toString(), false, null,
									errmsg);
						}
						return; // we do not want to add errors as "incoming messages"
					}

					// hook off carbonated delivery receipts
					DeliveryReceipt dr = DeliveryReceipt.from(msg);
					if (dr != null && direction == ChatConstants.INCOMING) {
						Log.d(TAG, "got delivery receipt from " + fromJID[0] + " for " + dr.getId());
						changeMessageDeliveryStatus(fromJID[0], dr.getId(), ChatConstants.DS_ACKED);
					}

					// ignore empty messages
					if (chatMessage == null) {
						if (msg.getSubject() != null && msg.getType() == Message.Type.groupchat
								&& mucJIDs.contains(fromJID[0])) {
							// this is a MUC subject, update our DB
							ContentValues cvR = new ContentValues();
							cvR.put(RosterProvider.RosterConstants.STATUS_MESSAGE, msg.getSubject());
							cvR.put(RosterProvider.RosterConstants.STATUS_MODE, StatusMode.available.ordinal());
							Log.d(TAG, "MUC subject for " + fromJID[0] + " set to: " + msg.getSubject());
							upsertRoster(cvR, fromJID[0]);
							return;
						}
						Log.d(TAG, "empty message: " + msg.getStanzaId());
						return;
					}


					// carbons are old. all others are new
					int is_new = (cc == null) ? ChatConstants.DS_NEW : ChatConstants.DS_SENT_OR_READ;
					if (msg.getType() == Message.Type.error)
						is_new = ChatConstants.DS_FAILED;

					// synchronized MUCs and contacts are not silent by default
					boolean is_silent = !(is_muc ? multiUserChats.get(fromJID[0]).isSynchronized : mRoster.contains(JidCreate.bareFrom(fromJID[0])));

					long upsert_id = -1;
					if (is_muc && is_from_me) {
						// messages from our other client are "ACKed" automatically
						is_new = ChatConstants.DS_ACKED;
					}

					// obtain Last Message Correction, if present
					MessageCorrectExtension replace = (MessageCorrectExtension)msg.getExtension(MessageCorrectExtension.NAMESPACE);
					String replace_id = (replace != null) ? replace.getIdInitialMessage() : null;

					// obtain OOB data, if present
					Oob oob = (Oob)msg.getExtension(Oob.NAMESPACE);
					String oob_extra = (oob != null) ? oob.getUrl() : null;

					if (fromJID[0].equalsIgnoreCase(mConfig.jabberID)) {
						// Self-Message, no need to display it twice --> replace old one
						replace_id = msg.getStanzaId();
					}
					if (replace_id != null && upsert_id == -1) {
						// obtain row id for last message with that full JID, or -1
						upsert_id = getRowIdForMessage(fromJID[0], fromJID[1], direction, replace_id);
						Log.d(TAG, "Replacing last message from " + fromJID[0] + "/" + fromJID[1] + ": " + replace_id + " -> " + msg.getStanzaId());
					}

					if (!is_muc || checkAddMucMessage(msg, msg.getStanzaId(), fromJID, timestamp)) {
						int msgFlags = ChatConstants.MF_TEXT;
						if (oob_extra != null)
							msgFlags |= ChatConstants.MF_FILE;
						if (timestamp != null && TextUtils.isEmpty(timestamp.getFrom())) // only show as delayed if from initial sender (no @from JID)
							msgFlags |= ChatConstants.MF_DELAY;
						if (replace != null)
							msgFlags |= ChatConstants.MF_CORRECT;
						ContentValues cv = formatMessageContentValues(direction, fromJID[0], fromJID[1],
								chatMessage, msgFlags, replace_id, oob_extra, is_new, msg.getStanzaId());
						addChatMessageToDB(fromJID[0], cv, ts, upsert_id);
						// only notify on private messages or on non-system MUC messages when MUC notification requested
						boolean need_notify = !is_muc || (fromJID[1].length() > 0) && mConfig.needMucNotification(fromJID[0], getMyMucNick(fromJID[0]), chatMessage);
						// outgoing carbon -> clear notification by signalling 'null' message
						if (is_from_me) {
							mServiceCallBack.notifyMessage(fromJID, null, true, msg.getType());
							// TODO: MUC PMs
							ChatHelper.markAsRead(mService, fromJID[0]);
						} else if (direction == ChatConstants.INCOMING && need_notify) {
							// replace URL with paperclip for notification
							if (chatMessage.equals(oob_extra))
								chatMessage = "\uD83D\uDCCE";
							mServiceCallBack.notifyMessage(fromJID, chatMessage, is_silent, msg.getType());
						}
					}
					if (direction == ChatConstants.INCOMING)
						sendReceiptIfRequested(msg);
				}
				} catch (Exception e) {
					// SMACK silently discards exceptions dropped from processPacket :(
					Log.e(TAG, "failed to process packet:");
					e.printStackTrace();
				}
			}
		};

		mXMPPConnection.addSyncStanzaListener(mStanzaListener, filter);
	}

	private boolean matchOutgoingMucReflection(Message msg, String[] fromJid) {
		String muc = fromJid[0];
		String nick = fromJid[1];
		String packet_id = msg.getStanzaId();
		if (packet_id == null)
			packet_id = "";

		MUCController mucc = multiUserChats.get(muc);
		if (!nick.equals(getMyMucNick(muc)))
			return false;
		// TODO: store pending _id's in MUCController (will be needed for CSN sending!)
		if (msg.getBody() == null)
			return false; /* TODO: yaxim doesn't emit CSN, so it's another client! */
		// https://stackoverflow.com/a/8248052/539443 - securely use LIKE
		String firstline = msg.getBody().replace("!", "!!")
				.replace("%", "!%")
				.replace("_", "!_")
				.replace("[", "![");
		if (msg.getBody().length() > 400)
			firstline = firstline + "%"; /* prefix match on long lines split for IRC */
		else
			firstline = firstline + "\n%"; /* first line match on other lines */
		Cursor c = mContentResolver.query(ChatProvider.CONTENT_URI, new String[] { ChatConstants._ID, ChatConstants.PACKET_ID },
				"jid = ? AND from_me = 1 AND (pid = ? OR message = ? OR message LIKE ? ESCAPE '!') AND _id >= ? AND read = ?",
				new String[] { muc, packet_id, msg.getBody(), firstline, "" + mucc.getFirstPacketID(), "" + ChatConstants.DS_SENT_OR_READ },
				"_id DESC");
		boolean updated = false;
		if (c.moveToFirst()) {
			long _id = c.getLong(0);
			ContentValues values = new ContentValues();
			values.put(ChatConstants.RESOURCE, nick);
			values.put(ChatConstants.DIRECTION, ChatConstants.INCOMING);
			values.put(ChatConstants.MESSAGE, msg.getBody());
			values.put(ChatConstants.DELIVERY_STATUS, ChatConstants.DS_ACKED);
			values.put(ChatConstants.ERROR, (String)null);
			values.put(ChatConstants.PACKET_ID, packet_id);
			updated = mContentResolver.update(Uri.withAppendedPath(ChatProvider.CONTENT_URI, "" + _id),
					values, null, null) == 1;
		}
		c.close();
		return updated;
	}

	private boolean checkAddMucMessage(Message msg, String packet_id, String[] fromJid, DelayInformation timestamp) {
		String muc = fromJid[0];
		String nick = fromJid[1];

		MUCController mucc = multiUserChats.get(muc);
		// messages with no timestamp are always new, and always come after join is completed
		if (timestamp == null) {
			Log.d(TAG, "checkAddMucMessage(" + fromJid[0] + "): " + nick + "/" + packet_id  + " without timestamp --> isSync=true");
			mucc.isSynchronized = true;
			return true;
		}
		// messages after we have joined and synchronized the MUC are always new
		if (mucc.isSynchronized) {
			Log.d(TAG, "checkAddMucMessage(" + fromJid[0] + "): " + nick + "/" + packet_id  + " MUC already synced.");
			return true;
		}

		long ts = timestamp.getStamp().getTime();

		final String[] projection = new String[] {
				ChatConstants._ID, ChatConstants.MESSAGE,
				ChatConstants.JID, ChatConstants.RESOURCE,
				ChatConstants.PACKET_ID
		};

		if (packet_id == null) packet_id = "";
		// TODO: merge failed messages with re-send attempts when sending, disable DS_FAILED check
		final String selection = "jid = ? AND resource = ? AND (pid = ? OR date = ? OR message = ?) AND _id >= ? AND read != ?";
		final String[] selectionArgs = new String[] { muc, nick, packet_id, ""+ts, msg.getBody(), ""+mucc.getFirstPacketID(), ""+ChatConstants.DS_FAILED };
		try {
			Cursor cursor = mContentResolver.query(ChatProvider.CONTENT_URI, projection, selection, selectionArgs, null);
			Log.d(TAG, "checkAddMucMessage(" + fromJid[0] + "): " + nick + "/" + packet_id + " matched " + cursor.getCount() + " items.");
			boolean result = (cursor.getCount() == 0);
			cursor.close();
			return result;
		} catch (Exception e) { e.printStackTrace(); } // just return true...
		Log.d(TAG, "checkAddMucMessage(" + fromJid[0] + "): " + nick + "/" + packet_id + " didn't match msg in history.");
		return true;
	}

	private void handleKickedFromMUC(String room, boolean banned, Jid actor, String reason) {
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

	@Override
	public String getMyMucNick(String jid) {
		MUCController muc = multiUserChats.get(jid);
		if (muc != null && muc.muc.getNickname() != null)
			return muc.muc.getNickname().toString();
		if (mucJIDs.contains(jid)) {
			ChatRoomHelper.RoomInfo ri = ChatRoomHelper.getRoomInfo(mService, jid);
			if (ri != null && !TextUtils.isEmpty(ri.nickname))
				return ri.nickname;
			return mConfig.screenName;
		}
		return null;
	}

	private void registerPresenceListener() {
		// do not register multiple packet listeners
		if (mPresenceListener != null)
			mXMPPConnection.removeAsyncStanzaListener(mPresenceListener);

		mPresenceListener = new StanzaListener() {
			public void processStanza(Stanza packet) {
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
					// reduce MUC pinging by registering incoming presence activity
					registerLastActivity(p.getFrom());
				} catch (Exception e) {
					// SMACK silently discards exceptions dropped from processStanza :(
					Log.e(TAG, "failed to process presence:");
					e.printStackTrace();
				}
			}
		};

		mXMPPConnection.addAsyncStanzaListener(mPresenceListener, new StanzaTypeFilter(Presence.class));
	}

	private void addChatMessageToDB(String bare_jid, ContentValues values, long ts, long upsert_id) {
		if (upsert_id >= 0 &&
		    mContentResolver.update(Uri.withAppendedPath(ChatProvider.CONTENT_URI, "" + upsert_id),
				values, null, null) == 1)
			return;
		values.put(ChatConstants.DATE, (ts > 0) ? ts : System.currentTimeMillis());
		Uri res = mContentResolver.insert(ChatProvider.CONTENT_URI, values);
		MUCController mucc = multiUserChats.get(bare_jid);
		if (mucc != null)
			mucc.addPacketID(res);
	}
	private void addChatMessageToDB(int direction, String[] tJID,
									String message, int delivery_status, long ts, String packetID, long upsert_id) {
		ContentValues values = formatMessageContentValues(direction, tJID[0], tJID[1],
				message, ChatConstants.MF_TEXT, null, null,
				delivery_status, packetID);
		addChatMessageToDB(tJID[0], values, ts, upsert_id);
	}

	private void addChatMessageToDB(int direction, String JID,
			String message, int delivery_status, long ts, String packetID) {
		String[] tJID = {JID, ""};
		addChatMessageToDB(direction, tJID, message, delivery_status, ts, packetID, -1);
	}

	private ContentValues getContentValuesForRosterEntry(final RosterEntry entry) {
		Presence presence = mRoster.getPresence(entry.getJid());
		return getContentValuesForRosterEntry(entry, presence);
	}

	private ContentValues getContentValuesForRosterEntry(final RosterEntry entry, Presence presence) {
		final ContentValues values = new ContentValues();

		values.put(RosterConstants.JID, entry.getUser());
		values.put(RosterConstants.ALIAS, getName(entry));

		// handle subscription requests and errors with higher priority
		Presence sub = subscriptionRequests.get(entry.getUser());
		if (presence.getType() == Presence.Type.error) {
			String error = presence.getError().getDescriptiveText();
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
			presence = mRoster.getPresence(entry.getJid());
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

	public synchronized void updateNickname() {
		mConfig.nickchange_required = false;
		new Thread("updateNickname " + mConfig.screenName) {
			public void run() {
				if (loadOrUpdateNickname(true))
					syncDbRooms();
			}
		}.start();
	}
	// returns true if the nickname changed
	private boolean loadOrUpdateNickname(boolean force_rename) {
		try {
			// first attempt to load nickname from PEP / store it into PEP
			PubSubManager psm = PubSubManager.getInstance(mXMPPConnection, mXMPPConnection.getUser().asEntityBareJid());
			String pepNickname = null;
			try {
				LeafNode n = psm.createUnverifiedLeafNode(Nick.NAMESPACE);
				List<?> ln = n.getItems();
				if (ln.size() > 0)
					pepNickname = ((PayloadItem<Nick>)ln.get(0)).getPayload().getName();
			} catch (XMPPException.XMPPErrorException e) {
				if (e.getStanzaError().getCondition() != StanzaError.Condition.item_not_found)
					throw e;
			}
			if (pepNickname == null || force_rename) {
				Log.i(TAG, "Storing nickname into PEP: " + mConfig.screenName);
				psm.tryToPublishAndPossibleAutoCreate(Nick.NAMESPACE, new PayloadItem<>(new Nick(mConfig.screenName)));
				return true;
			} else {
				Log.i(TAG, "Using nickname from PEP: " + pepNickname);
				return mConfig.storeScreennameIfChanged(pepNickname);
			}
		} catch (Exception e) {
			Log.d(TAG, "PEP nickname request failed: " + e.getMessage());
			e.printStackTrace();
		}
		try {
			// then attempt to load nickname from VCard
			VCardManager vcm = VCardManager.getInstanceFor(mXMPPConnection);
			VCard vc = vcm.loadVCard();
			String nick = vc.getNickName();
			if (!TextUtils.isEmpty(nick)) {
				Log.i(TAG, "Using nickname from VCard: " + mConfig.screenName);
				return mConfig.storeScreennameIfChanged(nick);
			}
		} catch (Exception e) {
			Log.d(TAG, "VCard request failed: " + e.getMessage());
			e.printStackTrace();
		}
		return false;
	}
	private void discoverMUCDomain(Jid jid, DiscoverInfo info) {
		if (mConfig.mucDomain != null)
			return;

		Iterator<DiscoverInfo.Identity> identities = info.getIdentities().iterator(); //SMAXX
		while (identities.hasNext()) {
			DiscoverInfo.Identity identity = identities.next();
			// only accept conference/text, not conference/irc!
			if (identity.getCategory().equals("conference") && identity.getType().equals("text")) {
				mConfig.mucDomain = jid.toString();
				Log.d(TAG, "discoverMUCDomain: " + mConfig.mucDomain);
				return;
			}
		}
	}
	private void loadMUCBookmarks() {
		try {
			Iterator<BookmarkedConference> it = BookmarkManager.getBookmarkManager(mXMPPConnection).getBookmarkedConferences().iterator();
			ArrayList<String> bookmarked_jids = new ArrayList<String>();
			boolean added = false;
			while (it.hasNext()) {
				BookmarkedConference bookmark = it.next();
				bookmarked_jids.add(bookmark.getJid().toString());
				if (!ChatRoomHelper.isRoom(mService, bookmark.getJid().toString())) {
					String jid = bookmark.getJid().toString();
					Resourcepart nick = bookmark.getNickname();
					String nickname = (nick != null) ? nick.toString() : null;
					Log.d(TAG, "Adding MUC: " + jid + "/" + nickname + " join=" + bookmark.isAutoJoin());
					ChatRoomHelper.addRoom(mService, jid, bookmark.getPassword(), nickname, bookmark.isAutoJoin());
					added = true;
				}
			}
			ChatRoomHelper.cleanupUnimportantRooms(mService, bookmarked_jids);
			if (added)
				syncDbRooms();
		} catch (Exception e) {
			Log.d(TAG, "getBookmarks failed: " + e.getMessage());
		}
	}

	@Override
	public boolean hasFileUpload() {
		return (mXMPPConnection != null) && HttpFileUploadManager.getInstanceFor(mXMPPConnection).isUploadServiceDiscovered();
	}

	private void discoverServicesAsync() {
		new Thread("discoverServices") {
			public void run() {
				discoverServices();
				loadOrUpdateNickname(mConfig.nickchange_required);
				loadMUCBookmarks(); // XXX: hack
			}
		}.start();
	}

	private void discoverServices(ServiceDiscoveryManager sdm, Jid jid) {
		try {
			DiscoverInfo info = sdm.discoverInfo(jid);
			discoverMUCDomain(jid, info);
		} catch (Exception e) {
			Log.e(TAG, "Error response from " + jid + ": " + e.getLocalizedMessage());
		}
	}
	private void discoverServices() {
		try {
			ServiceDiscoveryManager serviceDiscoveryManager = ServiceDiscoveryManager.getInstanceFor(mXMPPConnection);
			Jid server = JidCreate.domainBareFrom(mConfig.server);
			discoverServices(serviceDiscoveryManager, server);
			DiscoverItems items = serviceDiscoveryManager.discoverItems(server);

			Iterator<DiscoverItems.Item> it = items.getItems().iterator();
			while (it.hasNext()) {
				DiscoverItems.Item item = it.next();
				Jid jid = item.getEntityID();
				discoverServices(serviceDiscoveryManager, jid);
			}
		} catch (Exception e) {
			Log.e(TAG, "Error discovering services: " + e.getLocalizedMessage());
		}
	}


	/* set MUCs as not joined after a disconnect/reconnect */
	private synchronized void cleanupMUCsList() {
		for (MUCController muc : multiUserChats.values())
			muc.cleanup();
		multiUserChats.clear();
		mucLastPing = 0;
		mucPreviousPing = 0;
	}

	/* remove stale MUCs from Roster, mark remaining ones as offline if needed */
	private synchronized void cleanupMUCsRoster(boolean set_offline) {
		// get a fresh MUC list
		Cursor cursor = mContentResolver.query(RosterProvider.MUCS_URI,
				new String[] { RosterProvider.RosterConstants.JID },
				"autojoin=1", null, null);
		mucJIDs.clear();
		while(cursor.moveToNext()) {
			mucJIDs.add(cursor.getString(0));
		}
		cursor.close();

		// delete removed MUCs
		StringBuilder exclusion = new StringBuilder(RosterProvider.RosterConstants.GROUP + " = ? AND "
				+ RosterConstants.JID + " NOT IN ('");
		exclusion.append(TextUtils.join("', '", mucJIDs));
		exclusion.append("');");
		mContentResolver.delete(RosterProvider.CONTENT_URI,
				exclusion.toString(),
				new String[] { RosterProvider.RosterConstants.MUCS });
		if (set_offline) {
			// update all other MUCs as offline
			ContentValues values = new ContentValues();
			values.put(RosterConstants.STATUS_MODE, StatusMode.offline.ordinal());
			mContentResolver.update(RosterProvider.CONTENT_URI, values, RosterProvider.RosterConstants.GROUP + " = ?",
					new String[] { RosterProvider.RosterConstants.MUCS });
		}
	}

	public synchronized void syncDbRooms() {
		if (!isAuthenticated()) {
			debugLog("syncDbRooms: aborting, not yet authenticated");
			return;
		}

		java.util.Set<String> joinedRooms = multiUserChats.keySet();
		Cursor cursor = mContentResolver.query(RosterProvider.MUCS_URI, 
				new String[] {RosterProvider.RosterConstants._ID,
					RosterProvider.RosterConstants.JID, 
					RosterProvider.RosterConstants.PASSWORD, 
					RosterProvider.RosterConstants.NICKNAME}, 
				"autojoin=1", null, null);
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
			if (TextUtils.isEmpty(nickname))
				nickname = mConfig.screenName;
			mucJIDs.add(jid);
			//debugLog("Found MUC Room: "+jid+" with nick "+nickname+" and pw "+password);
			if(!joinedRooms.contains(jid) || !multiUserChats.get(jid).muc.isJoined()) {
				debugLog("room " + jid + " isn't joined yet, i wanna join...");
				joinRoomAsync(jid, nickname, password); // TODO: make historyLen configurable
			} else {
				MultiUserChat muc = multiUserChats.get(jid).muc;
				if (!muc.getNickname().equals(nickname)) {
					debugLog("room " + jid + ": changing nickname to " + nickname);
					try {
						muc.changeNickname(Resourcepart.from(nickname));
					} catch (Exception e) {
						Log.e(TAG, "Changing nickname failed.");
						e.printStackTrace();
					}
				}
				// send pending offline messages, eg. after 0198 resume
				try {
					sendOfflineMessages(jid);
				} catch (YaximXMPPException e) {
					e.printStackTrace();
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
		cleanupMUCsRoster(false);
	}
	
	protected boolean handleMucInvitation(Message msg) {
		EntityBareJid room;
		String inviter = null;
		String reason = null;
		String password = null;
		
		MUCUser mucuser = (MUCUser)msg.getExtension("x", "http://jabber.org/protocol/muc#user");
		GroupChatInvitation direct = GroupChatInvitation.from(msg);
		if (mucuser != null && mucuser.getInvite() != null) {
			// first try official XEP-0045 mediated invitation
			MUCUser.Invite invite = mucuser.getInvite();
			room = msg.getFrom().asEntityBareJidIfPossible();
			inviter = invite.getFrom().toString();
			reason = invite.getReason();
			password = mucuser.getPassword();
		} else if (direct != null) {
			// fall back to XEP-0249 direct invitation
			try {
				room = JidCreate.entityBareFrom(direct.getRoomAddress());
			} catch (XmppStringprepException e) {
				e.printStackTrace();
				return false;
			}
			inviter = msg.getFrom().toString();
			// TODO: get reason from direct invitation, not supported in smack3
		} else return false; // not a MUC invitation

		if (mucJIDs.contains(room)) {
			Log.i(TAG, "Ignoring invitation to known MUC " + room);
			return true;
		}
		Log.d(TAG, "MUC invitation from " + inviter + " to " + room);
		asyncProcessMucInvitation(room, inviter, reason, password);
		return true;
	}

	protected void asyncProcessMucInvitation(final EntityBareJid room, final String inviter,
			final String reason, final String password) {
		new Thread("processMucInvitation " + room.toString()) {
			public void run() {
				processMucInvitation(room, inviter, reason, password);
			}
		}.start();
	}
	protected void processMucInvitation(final EntityBareJid room, final String inviter,
										final String reason, final String password) {
		String roomname = room.toString();
		String inviter_name = null;
		if (getBareJID(inviter).equalsIgnoreCase(room.toString())) {
			// from == participant JID, display as "user (MUC)"
			inviter_name = getNameForJID(inviter);
		} else {
			// from == user bare or full JID
			inviter_name = getNameForJID(getBareJID(inviter));
		}
		String description = null;
		String inv_from = mService.getString(R.string.muc_invitation_from,
				inviter_name);

		// query room for info
		try {
			Log.d(TAG, "Requesting disco#info from " + room);
			RoomInfo ri = MultiUserChatManager.getInstanceFor(mXMPPConnection).getRoomInfo(room);
			String rn = ri.getName();
			if (rn != null && rn.length() > 0)
				roomname = String.format("%s (%s)", rn, roomname);
			description = ri.getSubject();
			if (!TextUtils.isEmpty(description))
				description = ri.getDescription();
			description = mService.getString(R.string.muc_invitation_occupants,
					description, ri.getOccupantsCount());
			Log.d(TAG, "MUC name after disco: " + roomname);
		} catch (Exception e) {
			// ignore a failed room info request
			Log.d(TAG, "MUC room IQ failed: " + room);
			e.printStackTrace();
		}

		mServiceCallBack.mucInvitationReceived(
				roomname,
				room.toString(),
				password,
				inv_from,
				description);
	}
	
	private Map<String,Runnable> ongoingMucJoins = new java.util.concurrent.ConcurrentHashMap<String, Runnable>();
	private synchronized void joinRoomAsync(final String room, final String nickname, final String password) {
		if (ongoingMucJoins.containsKey(room))
			return;
		Thread joiner = new Thread("join " + room) {
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
		// work around smack3 bug: can't rejoin with "used" MultiUserChat instance; need to manually
		// flush old MUC instance and create a new.
		MUCController mucc = multiUserChats.get(room);
		if (mucc != null)
			mucc.cleanup();
		mucc = new MUCController(mXMPPConnection, room);
		MultiUserChat muc = mucc.muc;
		mucc.loadPacketIDs(mContentResolver);

		Log.d(TAG, "created new MUC instance: " + room + " " + muc);
		muc.addUserStatusListener(new org.jivesoftware.smackx.muc.DefaultUserStatusListener() {
			@Override
			public void kicked(Jid actor, String reason) {
				debugLog("Kicked from " + room + " by " + actor + ": " + reason);
				handleKickedFromMUC(room, false, actor, reason);
			}
			@Override
			public void banned(Jid actor, String reason) {
				debugLog("Banned from " + room + " by " + actor + ": " + reason);
				handleKickedFromMUC(room, true, actor, reason);
			}
		});

		Date lastDate = null;
		final String[] projection = new String[] {
				ChatConstants._ID, ChatConstants.DATE
		};
		Cursor cursor = mContentResolver.query(ChatProvider.CONTENT_URI, projection, 
				ChatConstants.JID + " = ? AND " +
				ChatConstants.DIRECTION + " = " + ChatConstants.INCOMING,
				new String[] { room }, "_id DESC LIMIT 1");
		if(cursor.getCount()>0) {
			cursor.moveToFirst();
			lastDate = new Date(cursor.getLong(1));
			Log.d(TAG, "Getting room history for " + room + " starting at " + lastDate);
		} else Log.d(TAG, "Getting room history for " + room + " (full history)");
		cursor.close();
		
		ContentValues cvR = new ContentValues();
		cvR.put(RosterProvider.RosterConstants.JID, room);
		cvR.put(RosterProvider.RosterConstants.ALIAS, room);
		cvR.put(RosterProvider.RosterConstants.STATUS_MESSAGE, mService.getString(R.string.muc_synchronizing));
		cvR.put(RosterProvider.RosterConstants.STATUS_MODE, StatusMode.dnd.ordinal());
		cvR.put(RosterProvider.RosterConstants.GROUP, RosterProvider.RosterConstants.MUCS);
		upsertRoster(cvR, room);
		cvR.clear();
		cvR.put(RosterProvider.RosterConstants.JID, room);
		try {
			Presence force_resync = new Presence(Presence.Type.unavailable);
			force_resync.setTo(room + "/" + nickname);
			mXMPPConnection.sendStanza(force_resync);
			Presence join_presence = new Presence(Presence.Type.available);
			setStatusFromConfig(join_presence);
			MucEnterConfiguration.Builder mecb = muc.getEnterConfigurationBuilder(Resourcepart.from(nickname))
					.withPassword(password)
					.withPresence(join_presence)
					.requestMaxStanzasHistory(MUCController.LOOKUP_SIZE)
					.requestHistorySince(lastDate);
			muc.join(mecb.build());
		} catch (Exception e) {
			Log.e(TAG, "Could not join MUC-room "+room);
			e.printStackTrace();
			// work around race condition when MUC was removed while joining
			if(mucJIDs.contains(room)) {
				cvR.put(RosterProvider.RosterConstants.STATUS_MESSAGE, mService.getString(R.string.conn_error, e.getLocalizedMessage()));
				cvR.put(RosterProvider.RosterConstants.STATUS_MODE, StatusMode.offline.ordinal());
				upsertRoster(cvR, room);
			}
			//SMAXX muc.cleanup();
			return false;
		}

		if(muc.isJoined()) {
			synchronized(this) {
				multiUserChats.put(room, mucc);
			}
			String roomname = room.split("@")[0];
			try {
				String rn = muc.getRoomInfo().getName();
				if (rn != null && rn.length() > 0)
					roomname = rn;
				Log.d(TAG, "MUC name after disco: " + roomname);
			} catch (Exception e) {
				// ignore a failed room info request
				Log.d(TAG, "MUC room IQ failed: " + room);
				e.printStackTrace();
			}
			// delay requesting subject until room info IQ returned/failed
			String subject = muc.getSubject();
			cvR.put(RosterProvider.RosterConstants.ALIAS, roomname);
			cvR.put(RosterProvider.RosterConstants.STATUS_MESSAGE, subject);
			cvR.put(RosterProvider.RosterConstants.STATUS_MODE, StatusMode.available.ordinal());
			Log.d(TAG, "upserting MUC as online: " + roomname);
			upsertRoster(cvR, room);
			try {
				sendOfflineMessages(room);
			} catch (YaximXMPPException e) {
				Log.d(TAG, "MUC send offline failed!");
				e.printStackTrace();
			}
			return true;
		}
		
		//SMAXX muc.cleanup();
		return false;
	}

	private void quitRoom(String room) {
		Log.d(TAG, "Leaving MUC " + room);
		MultiUserChat muc = multiUserChats.get(room).muc;
		try {
			muc.leave();
		} catch (Exception e) {
			Log.d(TAG, "Failed to leave MUC: " + room);
			e.printStackTrace();
		}
		multiUserChats.remove(room);
		mContentResolver.delete(RosterProvider.CONTENT_URI, "jid = ?", new String[] {room});
	}

	@Override
	public boolean inviteToRoom(String contactJid, String roomJid) {
		MultiUserChat muc = multiUserChats.get(roomJid).muc;
		if(contactJid.contains("/")) {
			contactJid = contactJid.split("/")[0];
		}
		Log.d(TAG, "invitng contact: "+contactJid+" to room: "+muc);
		try {
			muc.invite(JidCreate.entityBareFromUnescaped(contactJid), "User "+contactJid+" has invited you to a chat!");
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	public List<EntityInfo> getUserList(String jid) {
		MUCController mucc = multiUserChats.get(jid);
		if (mucc == null) {
			return null;
		}
		MultiUserChat muc = mucc.muc;
		boolean non_anon = (muc.getRoomInfo() != null) && muc.getRoomInfo().isNonanonymous();
		Log.d(TAG, "MUC instance: " + jid + " " + muc);
		Iterator<EntityFullJid> occIter = muc.getOccupants().iterator();
		ArrayList<EntityInfo> tmpList = new ArrayList<EntityInfo>();
		while(occIter.hasNext()) {
			Presence occupantPresence = muc.getOccupantPresence(occIter.next());
			EntityInfo ei = new EntityInfo(EnumSet.of(EntityInfo.Type.MUC_PM), occupantPresence);
			// work around nameless participant from ejabberd MUC vcard
			if (occupantPresence.getFrom().getResourceOrNull() != null) {
				// Default bare_jid to the actual full occupant JID (muc@domain/nickname) for MUCChatWindow
				ei.jid = occupantPresence.getFrom().toString();
				ei.name = occupantPresence.getFrom().getResourceOrEmpty().toString();
				MUCUser mu = (MUCUser) occupantPresence.getExtension("x", "http://jabber.org/protocol/muc#user");
				// override bare_jid with real bare_jid if non-anon MUC and JID is known
				if (non_anon && mu != null && mu.getItem() != null && !TextUtils.isEmpty(mu.getItem().getJid()))
					ei.jid = mu.getItem().getJid().asBareJid().toString();
				tmpList.add(ei);
			}
		}
		Collections.sort(tmpList, new Comparator<EntityInfo>() {
			@Override
			public int compare(EntityInfo lhs, EntityInfo rhs) {
				return java.text.Collator.getInstance().compare(lhs.name, rhs.name);
			}
		});
		Log.d(TAG, "getUserList(" + jid + "): " + tmpList.size());
		return tmpList;
	}

	@Override
	public XMPPConnection getConnection() {
		return mXMPPConnection;
	}

}
