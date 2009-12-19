package org.yaxim.androidclient;

/*
	IPC interface for XMPPService to send broadcasts to UI
*/

interface IXMPPRosterCallback {
	void connectionSuccessful();
	void connectionFailed(boolean willReconnect);
	void rosterChanged();
}
