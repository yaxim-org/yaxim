package org.yaxim.androidclient.packet;

import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.provider.IQProvider;
import org.jivesoftware.smackx.xdata.FormField;
import org.jivesoftware.smackx.xdata.packet.DataForm;
import org.jivesoftware.smackx.xdata.provider.DataFormProvider;
import org.xmlpull.v1.XmlPullParser;

/**
 * Implements the protocol currently used to search MUCs on a MUClumbus bot.
 *
 * This part is the request IQ and parser.
 *
 * Documentation at https://search.jabber.network/docs/api
 *
 * @author Georg Lukas
 */

public class MuclumbusIQ extends IQ {
	public final static String ELEMENT = "search";
	public final static String NAMESPACE = "https://xmlns.zombofant.net/muclumbus/search/1.0";

	DataForm searchform;

	/** Create a Muclumbus request IQ with a pre-filled data form.
	 *
	 * @param searchform the request data form to send to the search bot
	 */
	public MuclumbusIQ(DataForm searchform) {
		super(ELEMENT, NAMESPACE);
		this.searchform = searchform;
		if (searchform != null)
			addExtension(searchform);
	}
	public MuclumbusIQ() {
		this(null);
	}


	/** Create a default search request for a given query string.
	 *
	 * This will return a pre-filled request to search for a string, with the
	 * results sorted by number of users (a.k.a. relevance).
	 *
	 * @param query string to search for
	 *
	 * @return an IQ that can be sent to the search bot
	 */
	public static MuclumbusIQ searchFor(String query) {
		DataForm sf = new DataForm(DataForm.Type.submit);
		FormField fieldType = new FormField(FormField.FORM_TYPE);
		fieldType.setType(FormField.Type.hidden);
		fieldType.addValue(NAMESPACE + "#params");
		sf.addField(fieldType);
		FormField fieldKey = new FormField("key");
		fieldKey.addValue("nusers");
		sf.addField(fieldKey);
		FormField queryKey = new FormField("q");
		queryKey.addValue(query);
		sf.addField(queryKey);
		return new MuclumbusIQ(sf);
	}

	public DataForm getSearchForm() {
		return searchform;
	}

	@Override
	protected IQChildElementXmlStringBuilder getIQChildElementBuilder(IQChildElementXmlStringBuilder xml) {
		xml.rightAngleBracket();
		return xml;
	}

	public static class Provider extends IQProvider<MuclumbusIQ> {
		@Override
		public MuclumbusIQ parse(XmlPullParser parser, int initialDepth) throws Exception {
			DataForm dataForm = null;

			outerloop: while (true) {
				final int eventType = parser.next();
				final String name = parser.getName();

				switch (eventType) {
					case XmlPullParser.START_TAG:
						switch (name) {
							case DataForm.ELEMENT:
								dataForm = DataFormProvider.INSTANCE.parse(parser);
								break;
						}
						break;
					case XmlPullParser.END_TAG:
						if (parser.getDepth() == initialDepth) {
							break outerloop;
						}
						break;
				}
			}

			return new MuclumbusIQ(dataForm);
		}

	}
}
