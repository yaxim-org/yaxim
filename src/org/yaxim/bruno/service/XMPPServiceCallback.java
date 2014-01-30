package org.yaxim.bruno.service;

import org.jivesoftware.smack.packet.Message;
import org.yaxim.bruno.util.ConnectionState;

public interface XMPPServiceCallback {
	void notifyMessage(String[] from, String messageBody, boolean silent_notification, Message.Type msgType);
	void setGracePeriod(boolean activate);
	void connectionStateChanged(ConnectionState connection_state);
	void mucInvitationReceived(String roomname, String room, String password, String invite_from, String roomdescription);
}
