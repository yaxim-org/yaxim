package org.yaxim.androidclient.packet;

import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.provider.PacketExtensionProvider;

import org.xmlpull.v1.XmlPullParser;

public class Replace implements PacketExtension {
	public final static String NAMESPACE = "urn:xmpp:message-correct:0";
	private String id;

	public Replace(String id) {
		this.id = id;
	}

	public String getElementName() {
		return "replace";
	}

	public String getNamespace() {
		return NAMESPACE;
	}

	public String getId() {
		return id;
	}

	public String toXML() {
		return "<" + getElementName() + " id=\"" + getId() + "\" xmlns=\"" + getNamespace() + "\" />";
	}

	public static class Provider implements PacketExtensionProvider {
		public PacketExtension parseExtension(XmlPullParser parser) throws Exception {
			return new Replace(parser.getAttributeValue(null, "id"));
		}
	}
}
