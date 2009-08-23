package de.hdmstuttgart.yaxim.service;

import java.util.ArrayList;

import de.hdmstuttgart.yaxim.data.RosterItem;
import de.hdmstuttgart.yaxim.exceptions.YaximXMPPException;
import de.hdmstuttgart.yaxim.util.StatusMode;


public interface Smackable {
	boolean doConnect() throws YaximXMPPException;
	boolean isAuthenticated();

	void addRosterItem(String user, String alias, String group) throws YaximXMPPException;
	void removeRosterItem(String user) throws YaximXMPPException;
	void renameRosterItem(String user, String newName) throws YaximXMPPException;
	void moveRosterItemToGroup(String user, String group) throws YaximXMPPException;
	void renameRosterGroup(String group, String newGroup);
	void addRosterGroup(String group);
	
	void setStatus(StatusMode status, String statusMsg);
	void sendMessage(String user, String message);
	
	void registerCallback(XMPPServiceCallback callBack);
	void unRegisterCallback();
	
	ArrayList<RosterItem> getRosterEntriesByGroup(String group);
	ArrayList<String> getRosterGroups();
	ArrayList<String> pullMessagesForContact(String jabberID);
}