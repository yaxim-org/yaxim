package org.yaxim.androidclient.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.RosterEntry;
import org.jivesoftware.smack.RosterGroup;
import org.jivesoftware.smack.RosterListener;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Presence.Mode;
import org.jivesoftware.smack.util.StringUtils;
import org.yaxim.androidclient.data.ChatProvider;
import org.yaxim.androidclient.data.RosterItem;
import org.yaxim.androidclient.data.YaximConfiguration;
import org.yaxim.androidclient.data.ChatProvider.Constants;
import org.yaxim.androidclient.exceptions.YaximXMPPException;
import org.yaxim.androidclient.util.AdapterConstants;
import org.yaxim.androidclient.util.StatusMode;

import android.content.ContentResolver;
import android.content.ContentValues;

public class SmackableImp implements Smackable {

	final static private int PACKET_TIMEOUT = 12000;
	final static private int KEEPALIVE_TIMEOUT = 300000; // 5min

	private final YaximConfiguration mConfig;
	private final ConnectionConfiguration mXMPPConfig;
	private final XMPPConnection mXMPPConnection;

	private XMPPServiceCallback mServiceCallBack;
	private Roster mRoster;

	private final ConcurrentHashMap<String, ConcurrentHashMap<String, RosterItem>> mRosterItemsByGroup = new ConcurrentHashMap<String, ConcurrentHashMap<String, RosterItem>>();
	private final HashMap<String, ArrayList<String>> mIncomingMessageQueue = new HashMap<String, ArrayList<String>>();
	private final ContentResolver mContentResolver;

	public SmackableImp(YaximConfiguration config,
			ContentResolver contentResolver) {
		this.mConfig = config;
		this.mXMPPConfig = new ConnectionConfiguration(mConfig.server,
				mConfig.port);
		this.mXMPPConfig.setReconnectionAllowed(true);
		this.mXMPPConnection = new XMPPConnection(mXMPPConfig);
		this.mContentResolver = contentResolver;
	}

	public boolean doConnect() throws YaximXMPPException {
		if (!mXMPPConnection.isConnected()) {

			tryToConnect();

			if (isAuthenticated()) {
				registerMessageHandler();
				registerRosterHandler();
				Presence presence = new Presence(Presence.Type.available);
				presence.setPriority(mConfig.priority);
				mXMPPConnection.sendPacket(presence);
				setRosterEntries();
			}
		}
		return isAuthenticated();
	}

	public void addRosterItem(String user, String alias, String group)
			throws YaximXMPPException {
		tryToAddRosterEntry(user, alias, group);
	}

	public void removeRosterItem(String user) throws YaximXMPPException {
		tryToRemoveRosterEntry(user);
		mServiceCallBack.rosterChanged();
	}

	public void renameRosterItem(String user, String newName)
			throws YaximXMPPException {
		mRoster = mXMPPConnection.getRoster();
		RosterEntry rosterEntry = mRoster.getEntry(user);

		if (!(newName.length() > 0) || (rosterEntry == null)) {
			throw new YaximXMPPException("JabberID to rename is invalid!");
		}
		rosterEntry.setName(newName);
	}

	public void addRosterGroup(String group) {
		mRoster = mXMPPConnection.getRoster();
		mRoster.createGroup(group);
	}

	public void renameRosterGroup(String group, String newGroup) {
		mRoster = mXMPPConnection.getRoster();
		RosterGroup groupToRename = mRoster.getGroup(group);
		mRosterItemsByGroup.remove(group);
		groupToRename.setName(newGroup);
	}

	public void moveRosterItemToGroup(String user, String group)
			throws YaximXMPPException {
		tryToMoveRosterEntryToGroup(user, group);
	}

	private ArrayList<String> getMessageQueueForContact(String jabberID) {
		if (!mIncomingMessageQueue.containsKey(jabberID)) {
			ArrayList<String> queue = new ArrayList<String>();
			mIncomingMessageQueue.put(jabberID, queue);
			return queue;
		}
		return mIncomingMessageQueue.get(jabberID);
	}

	public ArrayList<String> pullMessagesForContact(String jabberID) {
		ArrayList<String> queue = getMessageQueueForContact(jabberID);
		mIncomingMessageQueue.remove(jabberID);
		return queue;
	}

	private void tryToConnect() throws YaximXMPPException {
		try {
			SmackConfiguration.setPacketReplyTimeout(PACKET_TIMEOUT);
			SmackConfiguration.setKeepAliveInterval(KEEPALIVE_TIMEOUT);
			mXMPPConnection.connect();
			mXMPPConnection.login(mConfig.userName, mConfig.password,
					mConfig.ressource);
		} catch (XMPPException e) {
			throw new YaximXMPPException(e.getMessage());
		}
	}

	private void tryToMoveRosterEntryToGroup(String userName, String groupName)
			throws YaximXMPPException {

		if (!(groupName.length() > 0)) {
			throw new YaximXMPPException("Can't move " + userName
					+ " to a group without a name!");
		}

		mRoster = mXMPPConnection.getRoster();
		RosterGroup rosterGroup = getRosterGroup(groupName);
		RosterEntry rosterEntry = mRoster.getEntry(userName);
		// unSetRosterEntry(rosterEntry);
		removeRosterEntryFromGroups(rosterEntry);

		if (groupName.equals(AdapterConstants.EMPTY_GROUP))
			return;
		else {
			try {
				rosterGroup.addEntry(rosterEntry);
			} catch (XMPPException e) {
				throw new YaximXMPPException(e.getMessage());
			}
		}
	}

	private RosterGroup getRosterGroup(String groupName) {
		RosterGroup rosterGroup = mRoster.getGroup(groupName);

		if (!(groupName.equals(AdapterConstants.EMPTY_GROUP))) {
			if (rosterGroup == null) {
				rosterGroup = mRoster.createGroup(groupName);
			}
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
			if (group.getEntries().size() < 1) {
				mRosterItemsByGroup.remove(group.getName());
			}
		} catch (XMPPException e) {
			throw new YaximXMPPException(e.getMessage());
		}
	}

	private void tryToRemoveRosterEntry(String user) throws YaximXMPPException {
		mRoster = mXMPPConnection.getRoster();
		try {
			RosterEntry rosterEntry = mRoster.getEntry(user);
			// unSetRosterEntry(rosterEntry);
			mRoster.removeEntry(rosterEntry);
		} catch (XMPPException e) {
			throw new YaximXMPPException(e.getMessage());
		}
	}

	private void tryToAddRosterEntry(String user, String alias, String group)
			throws YaximXMPPException {
		mRoster = mXMPPConnection.getRoster();
		try {
			mRoster.createEntry(user, alias, new String[] { group });
		} catch (XMPPException e) {
			throw new YaximXMPPException(e.getMessage());
		}
	}

	private void setRosterEntries() {
		mRoster = mXMPPConnection.getRoster();
		Collection<RosterEntry> rosterEntries = mRoster.getEntries();
		for (RosterEntry rosterEntry : rosterEntries) {
			setRosterEntry(rosterEntry);
		}
	}

	private void setRosterEntry(RosterEntry rosterEntry) {
		String groupName = getGroup(rosterEntry.getGroups());
		ConcurrentMap<String, RosterItem> entryMap = getEntryMapForGroup(groupName);
		RosterItem rosterItem = getRosterItemForRosterEntry(rosterEntry);
		entryMap.put(rosterItem.jabberID, rosterItem);
	}

	private void unSetRosterEntry(RosterEntry rosterEntry) {
		String jabberID = rosterEntry.getUser();

		Set<Entry<String, ConcurrentHashMap<String, RosterItem>>> groupMaps = mRosterItemsByGroup
				.entrySet();

		for (Entry<String, ConcurrentHashMap<String, RosterItem>> entry : groupMaps) {
			ConcurrentHashMap<String, RosterItem> entryMap = entry.getValue();
			String groupName = entry.getKey();

			if (entryMap.containsKey(jabberID)) {
				entryMap.remove(jabberID);
			}
			if (entryMap.size() < 1) {
				mRosterItemsByGroup.remove(groupName);
			}
		}
	}

	private ConcurrentMap<String, RosterItem> getEntryMapForGroup(
			String groupName) {
		ConcurrentHashMap<String, RosterItem> tmpItemList;
		if (mRosterItemsByGroup.containsKey(groupName))
			return mRosterItemsByGroup.get(groupName);
		else {
			tmpItemList = new ConcurrentHashMap<String, RosterItem>();
			mRosterItemsByGroup.put(groupName, tmpItemList);
		}
		return tmpItemList;
	}

	public ArrayList<RosterItem> getRosterEntriesByGroup(String group) {
		ArrayList<RosterItem> groupItems = new ArrayList<RosterItem>();

		ConcurrentHashMap<String, RosterItem> rosterItemMap = mRosterItemsByGroup
				.get(group);

		if (rosterItemMap != null) {
			groupItems.addAll(rosterItemMap.values());
			Collections.sort(groupItems);
		}

		return groupItems;
	}

	private RosterItem getRosterItemForRosterEntry(RosterEntry entry) {
		String jabberID = entry.getUser();
		String userName = getName(entry);
		Presence userPresence = mRoster.getPresence(entry.getUser());
		StatusMode userStatus = getStatus(userPresence);
		String userStatusMessage = userPresence.getStatus();
		String userGroups = getGroup(entry.getGroups());

		return new RosterItem(jabberID, userName, userStatus,
				userStatusMessage, userGroups);
	}

	public ArrayList<String> getRosterGroups() {
		ArrayList<String> rosterGroups = new ArrayList<String>(
				mRosterItemsByGroup.keySet());
		Collections.sort(rosterGroups, new Comparator<String>() {
			public int compare(String group1, String group2) {
				if (group1.equals(AdapterConstants.EMPTY_GROUP))
					return -1;
				return group1.toLowerCase().compareTo(group2.toLowerCase());
			}
		});
		return rosterGroups;
	}

	public void setStatus(StatusMode status, String statusMsg) {
		Presence presence = new Presence(Presence.Type.available);
		Mode mode = stringToMode(status.name());
		presence.setMode(mode);
		presence.setStatus(statusMsg);
		mXMPPConnection.sendPacket(presence);
	}

	private Mode stringToMode(String modeStr) {
		return Mode.valueOf(modeStr);
	}

	public void sendMessage(String toJID, String message) {
		final Message newMessage = new Message(toJID, Message.Type.chat);
		newMessage.setBody(message);
		if (isAuthenticated()) {
			mXMPPConnection.sendPacket(newMessage);
			writeToDB(mConfig.jabberID, toJID, message, true);
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
	}

	public void unRegisterCallback() {
		mXMPPConnection.disconnect();
		mRosterItemsByGroup.clear();
		this.mServiceCallBack = null;
	}

	private void registerRosterHandler() {
		mRoster = mXMPPConnection.getRoster();

		mRoster.addRosterListener(new RosterListener() {

			public void entriesAdded(Collection<String> entries) {
				for (String entry : entries) {
					RosterEntry rosterEntry = mRoster.getEntry(entry);
					setRosterEntry(rosterEntry);
				}
				mServiceCallBack.rosterChanged();
			}

			public void entriesDeleted(Collection<String> entries) {
				for (String entry : entries) {
					RosterEntry rosterEntry = mRoster.getEntry(entry);
					unSetRosterEntry(rosterEntry);
				}
				mServiceCallBack.rosterChanged();
			}

			public void entriesUpdated(Collection<String> entries) {
				for (String entry : entries) {
					RosterEntry rosterEntry = mRoster.getEntry(entry);
					unSetRosterEntry(rosterEntry);
					setRosterEntry(rosterEntry);
				}
				mServiceCallBack.rosterChanged();
			}

			public void presenceChanged(Presence presence) {
				String jabberID = getJabberID(presence.getFrom());
				RosterEntry rosterEntry = mRoster.getEntry(jabberID);
				setRosterEntry(rosterEntry);
				mServiceCallBack.rosterChanged();
			}
		});
	}

	private String getJabberID(String from) {
		String[] res = from.split("/");
		return res[0];
	}

	private void registerMessageHandler() {
		PacketTypeFilter filter = new PacketTypeFilter(Message.class);

		PacketListener myListener = new PacketListener() {

			public void processPacket(Packet packet) {
				if (packet instanceof Message) {
					Message msg = (Message) packet;
					String chatMessage = msg.getBody();

					if (chatMessage == null) {
						return;
					}

					String fromJID = getJabberID(msg.getFrom());
					String toJID = getJabberID(msg.getTo());

					writeToDB(fromJID, toJID, chatMessage, false);

				}
			}
		};
		mXMPPConnection.addPacketListener(myListener, filter);
	}

	private void writeToDB(String fromJID, String toJID, String message,
			boolean read) {
		ContentValues values = new ContentValues();

		values.put(Constants.FROM_JID, fromJID);
		values.put(Constants.TO_JID, toJID);
		values.put(Constants.MESSAGE, message);
		values.put(Constants.HAS_BEEN_READ, false);
		values.put(Constants.DATE, System.currentTimeMillis());

		mContentResolver.insert(ChatProvider.CONTENT_URI, values);
	}

	private String getGroup(Collection<RosterGroup> groups) {
		for (RosterGroup group : groups) {
			return group.getName();
		}
		return AdapterConstants.EMPTY_GROUP;
	}

	private String getName(RosterEntry rosterEntry) {
		String name = rosterEntry.getName();
		if (name != null) {
			return name;
		}
		return StringUtils.parseName(rosterEntry.getUser());
	}

	private StatusMode getStatus(Presence presence) {
		if (presence.getType() == Presence.Type.available) {
			if (presence.getMode() != null) {
				return StatusMode.valueOf(presence.getMode().name());
			}
			return StatusMode.available;
		}
		return StatusMode.offline;
	}
}
