package org.yaxim.bruno.service;

import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.provider.IQProvider;
import org.xmlpull.v1.XmlPullParser;

public class PingProvider implements IQProvider{

	@Override
	public IQ parseIQ(XmlPullParser arg0) throws Exception {
		return new Ping();
	}

}
