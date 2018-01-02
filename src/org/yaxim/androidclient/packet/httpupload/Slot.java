package org.yaxim.androidclient.packet.httpupload;

import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.provider.IQProvider;
import org.jivesoftware.smack.util.StringUtils;
import org.xmlpull.v1.XmlPullParser;

public class Slot extends IQ {
    public static final String NAME = "slot";
    public static final String XMLNS = "urn:xmpp:http:upload";

    private String putUrl;
    private String getUrl;

    public Slot(String putUrl, String getUrl) {
        this.putUrl = putUrl;
        this.getUrl = getUrl;
    }

    public String getPutUrl() { return putUrl; }

    public String getGetUrl() { return getUrl; }

    @Override
    public String getChildElementXML() {
        String xml = "<"+ NAME + " xmlns=\"" + XMLNS + "\">";
        if (putUrl != null && !putUrl.equals("")) {
            xml += "<put>" + StringUtils.escapeForXML(putUrl) + "</put>";
        }
        if (getUrl != null && !getUrl.equals("")) {
            xml += "<get>" + StringUtils.escapeForXML(getUrl) + "</get>";
        }
        xml += "</" + NAME + ">";
        return xml;
    }

    public static  class Provider implements IQProvider {
        public Slot parseIQ(XmlPullParser parser) throws Exception {
            String putUrl = null;
            String getUrl = null;

            int event;
            boolean done = false;
            while (!done) {
                event = parser.next();
                switch (event) {
                    case XmlPullParser.START_TAG:
                        if (parser.getName().equals("put")) {
                            putUrl = parser.nextText();
                        } else if (parser.getName().equals("get")) {
                            getUrl = parser.nextText();
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        if (parser.getName().equals("slot")) {
                            done = true;
                        }
                        break;
                    default:
                        break;
                }
            }

            return new Slot(putUrl, getUrl);
        }
    }
}
