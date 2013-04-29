package org.yaxim.androidclient.service;

public interface XMPPServiceCallback {
	void newMessage(String from, String messageBody);
	void stateChanged();
	void disconnectOnError();
}
