package org.yaxim.androidclient.service;

import org.jivesoftware.smack.packet.Message;

public interface XMPPServiceCallback {
	void notifyMessage(String[] from, String messageBody, boolean silent_notification, Message.Type msgType);
	void connectionStateChanged();
	void mucInvitationReceived(String room, String password, String body);
}
