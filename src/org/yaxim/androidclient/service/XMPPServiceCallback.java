package org.yaxim.androidclient.service;

public interface XMPPServiceCallback {
	void newMessage(String from, String messageBody, boolean silent_notification);
	void stateChanged();
	void disconnectOnError();
}
