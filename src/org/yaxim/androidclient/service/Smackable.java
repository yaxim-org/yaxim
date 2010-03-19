package org.yaxim.androidclient.service;

import java.util.ArrayList;

import org.yaxim.androidclient.data.RosterItem;
import org.yaxim.androidclient.exceptions.YaximXMPPException;
import org.yaxim.androidclient.util.StatusMode;



public interface Smackable {
	boolean doConnect() throws YaximXMPPException;
	boolean isAuthenticated();

	void addRosterItem(String user, String alias, String group) throws YaximXMPPException;
	void removeRosterItem(String user) throws YaximXMPPException;
	void renameRosterItem(String user, String newName) throws YaximXMPPException;
	void moveRosterItemToGroup(String user, String group) throws YaximXMPPException;
	void renameRosterGroup(String group, String newGroup);
	void requestAuthorizationForRosterItem(String user);
	void addRosterGroup(String group);
	
	void setStatus(StatusMode status, String statusMsg);
	void sendMessage(String user, String message);
	
	void registerCallback(XMPPServiceCallback callBack);
	void unRegisterCallback();
	
	ArrayList<RosterItem> getRosterEntriesByGroup(String group);
	ArrayList<String> getRosterGroups();
	
	String getNameForJID(String jid);
}