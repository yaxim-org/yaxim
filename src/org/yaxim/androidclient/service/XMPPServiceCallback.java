package org.yaxim.androidclient.service;

public interface XMPPServiceCallback {
	void newMessage(String from, String messageBody, boolean silent_notification);
	void messageError(String from, String errorBody, boolean silent_notification);
	void connectionStateChanged();
	void rosterChanged(); // TODO: remove that!
}
