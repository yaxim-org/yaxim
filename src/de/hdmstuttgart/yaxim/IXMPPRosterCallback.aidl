package de.hdmstuttgart.yaxim;

/*
	IPC interface for XMPPService to send broadcasts to UI
*/

interface IXMPPRosterCallback {
	void connectionSuccessful();
	void connectionFailed();
	void rosterChanged();
}