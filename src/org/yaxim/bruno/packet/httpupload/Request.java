package org.yaxim.bruno.packet.httpupload;

import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.provider.IQProvider;
import org.jivesoftware.smack.util.StringUtils;
import org.xmlpull.v1.XmlPullParser;

public class Request extends IQ {
    public static final String XMLNS = "urn:xmpp:http:upload";
    private String filename;
    private String size;
    private String contentType;

    public Request(String filename, String size) {
        this.filename = filename;
        this.size = size;
        setType(IQ.Type.GET);
    }

    public Request(String filename, String size, String contentType) {
        this.filename = filename;
        this.size = size;
        this.contentType = contentType;
        setType(IQ.Type.GET);
    }

    @Override
    public String getChildElementXML() {
        String xml = "<request xmlns=\"" + XMLNS + "\">";
        xml += "<filename>" + StringUtils.escapeForXML(this.filename) + "</filename>";
        xml += "<size>" + this.size + "</size>";
        if (contentType != null && !contentType.equals("")) xml += "<content-type>" + StringUtils.escapeForXML(contentType) + "</content-type>";
        xml += "</request>";
        return xml;
    }

    public static class Provider implements IQProvider {

        @Override
        public IQ parseIQ(XmlPullParser parser) throws Exception {
            String filename = null;
            String size = null;
            String contentType = null;

            boolean done = false;
            while (!done) {
                switch (parser.getEventType()) {
                    case XmlPullParser.START_TAG:
                        if (parser.getName().equals("request")) {
                            for (int i = 0; i < parser.getAttributeCount(); i++) {
                                if (parser.getAttributeName(i).equals("filename")) {
                                    filename = parser.getAttributeValue(i);
                                } else if (parser.getAttributeName(i).equals("size")) {
                                    size = parser.getAttributeValue(i);
                                } else if (parser.getAttributeName(i).equals("content-type")) {
                                    contentType = parser.getAttributeValue(i);
                                }
                            }
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        if (parser.getName().equals("request")) {
                            done = true;
                        }
                        break;
                    default:
                        break;
                }
                parser.next();
            }

            if (filename != null && !filename.equals("") && size != null && !size.equals("")) {
                return new Request(filename, size, contentType);
            } else {
                return null;
            }
        }
    }
}
