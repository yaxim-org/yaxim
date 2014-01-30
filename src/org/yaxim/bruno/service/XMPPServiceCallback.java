package org.yaxim.bruno.service;

import org.jivesoftware.smack.packet.Message;

public interface XMPPServiceCallback {
	void notifyMessage(String[] from, String messageBody, boolean silent_notification, Message.Type msgType);
	void connectionStateChanged();
	void mucInvitationReceived(String roomname, String room, String password, String invite_from, String roomdescription);
}
