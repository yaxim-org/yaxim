package org.yaxim.bruno;

/*
	IPC interface for XMPPService to send broadcasts to UI
*/

interface IXMPPRosterCallback {
	void connectionStatusChanged(boolean isConnected, boolean willReconnect);
}
