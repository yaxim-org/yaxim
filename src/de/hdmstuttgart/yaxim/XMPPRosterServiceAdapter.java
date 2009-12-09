package de.hdmstuttgart.yaxim;

import java.util.List;

import android.os.RemoteException;
import android.util.Log;
import de.hdmstuttgart.yaxim.data.RosterItem;
import de.hdmstuttgart.yaxim.service.IXMPPRosterService;
import de.hdmstuttgart.yaxim.util.StatusMode;

public class XMPPRosterServiceAdapter {
	
	private static final String TAG = "XMPPRosterServiceAdapter";
	private IXMPPRosterService xmppServiceStub;
	
	public XMPPRosterServiceAdapter(IXMPPRosterService xmppServiceStub) {
		Log.i(TAG, "New XMPPRosterServiceAdapter construced");
		this.xmppServiceStub = xmppServiceStub;
	}
	
	public void setStatus(StatusMode status, String statusMsg) {
		try {
			xmppServiceStub.setStatus(status.name(), statusMsg);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	public void addRosterItem(String user, String alias, String group) {
		try {
			xmppServiceStub.addRosterItem(user, alias, group);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}
	
	public void renameRosterGroup(String group, String newGroup){
		try {
			xmppServiceStub.renameRosterGroup(group, newGroup);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}
	
	public void renameRosterItem(String contact, String newItemName){
		try {
			xmppServiceStub.renameRosterItem(contact, newItemName);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}
	
	
	public void moveRosterItemToGroup(String user, String group){
		try {
			xmppServiceStub.moveRosterItemToGroup(user, group);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}
	
	public void addRosterGroup(String group){
		try {
			xmppServiceStub.addRosterGroup(group);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}
	
	public void removeRosterItem(String user) {
		try {
			xmppServiceStub.removeRosterItem(user);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}
	
	public List<String> getRosterGroups() {
		List<String> rosterGroups = null;
		try {
			rosterGroups = xmppServiceStub.getRosterGroups();
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		return rosterGroups;
	}
	
	public void disconnect() {
		try {
			xmppServiceStub.disconnect();
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}
	
	public void connect() {
		try {
			xmppServiceStub.connect();
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	public List<RosterItem> getGroupItems(String group) {
		List<RosterItem> itemsInRosterGroup = null;
		try {
			itemsInRosterGroup = xmppServiceStub.getRosterEntriesByGroup(group);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		return itemsInRosterGroup;
	}
	
	public void registerUICallback(IXMPPRosterCallback uiCallback) {
		try {
			xmppServiceStub.registerRosterCallback(uiCallback);
		} catch (RemoteException e) {
			e.printStackTrace();
		} catch (NullPointerException e) {
			e.printStackTrace();
		}
	}

	public void unregisterUICallback(IXMPPRosterCallback uiCallback) {
		try {
			xmppServiceStub.unregisterRosterCallback(uiCallback);
		} catch (RemoteException e) {
			e.printStackTrace();
		} catch (NullPointerException e) {
			e.printStackTrace();
		}
	}

	public boolean isAuthenticated() {
		try {
			return xmppServiceStub.isAuthenticated();
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		return false;
	}
}
