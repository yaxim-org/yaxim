package org.yaxim.androidclient.util;

public enum ConnectionState {
	OFFLINE,		/// no connection is desired by the user
	CONNECTING,		/// currently opening a connection
	ONLINE,			/// connected and authenticated
	DISCONNECTING,		/// disconnect in progress
	DISCONNECTED,		/// the network caused a disconnect
	RECONNECT_NETWORK,	/// waiting for the network to become available
	RECONNECT_DELAYED;	/// waiting for a reconnect timer
};
