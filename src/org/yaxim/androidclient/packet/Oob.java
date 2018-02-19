package org.yaxim.androidclient.packet;

import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.provider.PacketExtensionProvider;
import org.jivesoftware.smack.util.StringUtils;
import org.xmlpull.v1.XmlPullParser;

/**
 * XEP-0066: Out-of-Band Data
 * Used to inform HTTP Upload capable clients of an inline image
 */

public class Oob implements PacketExtension {
	public final static String NAMESPACE = "jabber:x:oob";
	public final static String ELEMENT = "x";
	private String url;

	public Oob(String url) {
		this.url = url;
	}

	public String getElementName() {
		return ELEMENT;
	}

	public String getNamespace() {
		return NAMESPACE;
	}

	public String getUrl() {
		return url;
	}

	public String toXML() {
		if (url != null)
			return "<" + getElementName() + " xmlns=\"" + getNamespace() + "\" ><url>"
				+ StringUtils.escapeForXML(getUrl()) + "</url></" + getElementName() + ">";
		else
			return "<" + getElementName() + " xmlns=\"" + getNamespace() + "\" />";
	}

	public static class Provider implements PacketExtensionProvider {
		public PacketExtension parseExtension(XmlPullParser parser) throws Exception {
			String url = null;
			boolean done = false;
			while (!done) {
				parser.next();
				String elementName = parser.getName();
				if (parser.getEventType() == XmlPullParser.START_TAG) {
					if ("url".equals(elementName)) {
						url = parser.nextText();
					}
				}
				else if (parser.getEventType() == XmlPullParser.END_TAG && ELEMENT.equals(elementName)) {
					done = true;
				}
			}
			return new Oob(url);
		}
	}
}
