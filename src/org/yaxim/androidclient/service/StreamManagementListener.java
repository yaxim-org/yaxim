package org.yaxim.androidclient.service;

import org.jivesoftware.smack.packet.Packet;

public interface StreamManagementListener {

	static final int SM_PACKET_STATUS_SM_UNAVAILABLE = -1;
	static final int SM_PACKET_STATUS_LOST = 0;
	static final int SM_PACKET_STATUS_ACKED = 1;

	public void SmPacketStatusIndication(Packet packet, int status);
}
