package de.hdmstuttgart.yaxim.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

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

import de.hdmstuttgart.yaxim.data.RosterItem;
import de.hdmstuttgart.yaxim.data.YaximConfiguration;
import de.hdmstuttgart.yaxim.exceptions.YaximXMPPException;
import de.hdmstuttgart.yaxim.util.AdapterConstants;
import de.hdmstuttgart.yaxim.util.StatusMode;

public class SmackableImp implements Smackable {

	final static private int PACKET_TIMEOUT = 12000;
	final static private int KEEPALIVE_TIMEOUT = 300000; // 5min

	private final YaximConfiguration mConfig;
	private final ConnectionConfiguration mXMPPConfig;
	private final XMPPConnection mXMPPConnection;
	
	private XMPPServiceCallback mServiceCallBack;
	private Roster mRoster;

	private final ConcurrentHashMap<String, ConcurrentHashMap<String, RosterItem>> rosterItemsByGroup = new ConcurrentHashMap<String, ConcurrentHashMap<String, RosterItem>>();
	private final HashMap<String, ArrayList<String>> incomingMessageQueue = new HashMap<String, ArrayList<String>>();


	public SmackableImp(YaximConfiguration config) {
		this.mConfig = config;
		this.mXMPPConfig = new ConnectionConfiguration(mConfig.server, mConfig.port);
		this.mXMPPConfig.setReconnectionAllowed(true);
		this.mXMPPConnection = new XMPPConnection(mXMPPConfig);
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
		return (mXMPPConnection.isConnected() && mXMPPConnection.isAuthenticated());
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
		rosterItemsByGroup.remove(group);
		groupToRename.setName(newGroup);
	}

	public void moveRosterItemToGroup(String user, String group)
			throws YaximXMPPException {
		tryToMoveRosterEntryToGroup(user, group);
	}

	private ArrayList<String> getMessageQueueForContact(String jabberID) {
		if (!incomingMessageQueue.containsKey(jabberID)) {
			ArrayList<String> queue = new ArrayList<String>();
			incomingMessageQueue.put(jabberID, queue);
			return queue;
		}
		return incomingMessageQueue.get(jabberID);
	}

	public ArrayList<String> pullMessagesForContact(String jabberID) {
		ArrayList<String> queue = getMessageQueueForContact(jabberID);
		incomingMessageQueue.remove(jabberID);
		return queue;
	}

	private void tryToConnect() throws YaximXMPPException {
		try {
			SmackConfiguration.setPacketReplyTimeout(PACKET_TIMEOUT);
			SmackConfiguration.setKeepAliveInterval(KEEPALIVE_TIMEOUT);
			mXMPPConnection.connect();
			mXMPPConnection.login(mConfig.userName, mConfig.password, mConfig.ressource);
		} catch (XMPPException e) {
			throw new YaximXMPPException(e.getMessage());
		}
	}

	private void tryToMoveRosterEntryToGroup(String user, String group)
			throws YaximXMPPException {

		if (!(group.length() > 0)) {
			throw new YaximXMPPException("Can't move " + user
					+ " to a group without a name!");
		}

		mRoster = mXMPPConnection.getRoster();
		RosterGroup rosterGroup = mRoster.getGroup(group);

		if (!(group.equals(AdapterConstants.EMPTY_GROUP))) {
			if (rosterGroup == null)
				rosterGroup = mRoster.createGroup(group);
		}

		RosterEntry rosterEntry = mRoster.getEntry(user);
		unSetRosterEntry(rosterEntry);
		removeRosterEntryFromGroups(rosterEntry);

		if (group.equals(AdapterConstants.EMPTY_GROUP))
			return;
		else {
			try {
				rosterGroup.addEntry(rosterEntry);
			} catch (XMPPException e) {
				throw new YaximXMPPException(e.getMessage());
			}
		}
	}

	private void removeRosterEntryFromGroups(RosterEntry rosterEntry)
			throws YaximXMPPException {
		Collection<RosterGroup> oldGroups = rosterEntry.getGroups();
		for (RosterGroup group : oldGroups) {
			tryToRemoveUserFromGroup(group, rosterEntry);
			if (group.getEntries().size() < 1)
				rosterItemsByGroup.remove(group.getName());
		}
	}

	private void tryToRemoveUserFromGroup(RosterGroup group,
			RosterEntry rosterEntry) throws YaximXMPPException {
		try {
			group.removeEntry(rosterEntry);
		} catch (XMPPException e) {
			throw new YaximXMPPException(e.getMessage());
		}
	}

	private void tryToRemoveRosterEntry(String user) throws YaximXMPPException {
		mRoster = mXMPPConnection.getRoster();
		try {
			RosterEntry rosterEntry = mRoster.getEntry(user);
			unSetRosterEntry(rosterEntry);
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
		String groupName = getGroup(rosterEntry.getGroups());
		ConcurrentMap<String, RosterItem> entryMap = getEntryMapForGroup(groupName);
		String jabberID = rosterEntry.getUser();
		if (entryMap.containsKey(jabberID))
			entryMap.remove(jabberID);
		if (entryMap.size() < 1)
			rosterItemsByGroup.remove(groupName);
	}

	private ConcurrentMap<String, RosterItem> getEntryMapForGroup(
			String groupName) {
		ConcurrentHashMap<String, RosterItem> tmpItemList;
		if (rosterItemsByGroup.containsKey(groupName))
			return rosterItemsByGroup.get(groupName);
		else {
			tmpItemList = new ConcurrentHashMap<String, RosterItem>();
			rosterItemsByGroup.put(groupName, tmpItemList);
		}
		return tmpItemList;
	}

	public ArrayList<RosterItem> getRosterEntriesByGroup(String group) {
		ArrayList<RosterItem> rosterItems = new ArrayList<RosterItem>(
				rosterItemsByGroup.get(group).values());
		Collections.sort(rosterItems);
		return rosterItems;
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
				rosterItemsByGroup.keySet());
		Collections.sort(rosterGroups, new Comparator<String>() {
			public int compare(String object1, String object2) {
				if (object1.equals(AdapterConstants.EMPTY_GROUP))
					return -1;
				return object1.toLowerCase().compareTo(object2.toLowerCase());
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

	public void sendMessage(String user, String message) {
		final Message newMessage = new Message(user, Message.Type.chat);
		newMessage.setBody(message);
		if (isAuthenticated()) {
			mXMPPConnection.sendPacket(newMessage);
		}
	}

	public boolean isAuthenticated() {
		if (mXMPPConnection != null) {
			return mXMPPConnection.isAuthenticated();
		}
		return false;
	}

	public void registerCallback(XMPPServiceCallback callBack) {
		this.mServiceCallBack = callBack;
	}

	public void unRegisterCallback() {
		mXMPPConnection.disconnect();
		rosterItemsByGroup.clear();
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
		Pattern p = Pattern.compile("\\/");
		String[] res = p.split(from);
		return res[0];
	}

	private void registerMessageHandler() {
		PacketTypeFilter filter = new PacketTypeFilter(Message.class);

		PacketListener myListener = new PacketListener() {

			public void processPacket(Packet packet) {
				if (packet instanceof Message) {
					Message message = (Message) packet;
					String msg = message.getBody();

					if (msg == null) {
						return;
					}

					String jabberID = getJabberID(message.getFrom())
							.toLowerCase();

					if (!mServiceCallBack.isBoundTo(jabberID)) {
						ArrayList<String> queue = getMessageQueueForContact(jabberID);
						queue.add(msg);
					}

					mServiceCallBack.newMessage(jabberID, msg);
				}
			}
		};
		mXMPPConnection.addPacketListener(myListener, filter);
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
