package org.yaxim.androidclient.service;

import org.jivesoftware.smack.packet.Packet;

import android.util.Log;

public class YaximSmHandler implements StreamManagementListener {

	private static final String TAG = "YaximSmHandler";

	@Override
	public void SmPacketStatusIndication(Packet packet, int status) {
		Log.d(TAG, "packetAcked called.");
	}

}
