package org.yaxim.androidclient.packet;

import org.bouncycastle.asn1.cmp.OOBCertHash;
import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smack.util.XmlStringBuilder;
import org.xmlpull.v1.XmlPullParser;

/**
 * XEP-0066: Out-of-Band Data
 * Used to inform HTTP Upload capable clients of an inline image
 *
 * TODO: implement <desc/>
 */

public class Oob implements ExtensionElement {
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

	public CharSequence toXML(String enclosingNamespace) {
		XmlStringBuilder xml = new XmlStringBuilder(this);
		if (url != null) {
			xml.rightAngleBracket().element("url", url).closeElement(ELEMENT);
		} else
			xml.closeEmptyElement();
		return xml;
	}

	public static class Provider extends ExtensionElementProvider<Oob> {
		public Oob parse(XmlPullParser parser, int initialDepth) throws Exception {
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
