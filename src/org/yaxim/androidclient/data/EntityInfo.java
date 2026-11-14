package org.yaxim.androidclient.data;

import android.text.TextUtils;

import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.StanzaError;
import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
import org.jivesoftware.smackx.disco.packet.DiscoverItems;
import org.jivesoftware.smackx.muc.packet.MUCInitialPresence;
import org.jivesoftware.smackx.xdata.Form;
import org.jivesoftware.smackx.xdata.FormField;
import org.yaxim.androidclient.util.StatusMode;
import org.yaxim.androidclient.util.XMPPHelper;

import java.util.EnumSet;
import java.util.List;

public class EntityInfo {
	public String jid;
	public StatusMode statusMode;
	public int unread;
	public String name;
	public String status;
	public int users;
	// TODO: timestamp
	// TODO: last message body
	// TODO: avatar

	public EnumSet<Type> type;
	public Object data;

	public EntityInfo() {
	}

	public EntityInfo(EnumSet<Type> type, String jid, StatusMode statusMode, int unread, String name, String status, int users, Object data) {
		this.type = type;
		this.jid = jid;
		this.statusMode = statusMode;
		this.unread = unread;
		this.name = name;
		this.status = status;
		this.users = users;
		this.data = data;
	}

	public EntityInfo(EnumSet<Type> type, Presence p) {
		this.type = type;
		this.jid = p.getFrom().asBareJid().toString();
		setPresenceStatus(p);
		this.data = p;
	}

	public static EntityInfo fromJidName(String jid, String name) {
		return new EntityInfo(EnumSet.of(Type.User), jid, StatusMode.unknown, 0, name, jid, 0, null);
	}
	public static EntityInfo fromJid(String jid) {
		return new EntityInfo(EnumSet.of(Type.User), jid, StatusMode.unknown, 0, jid, null, 0, null);
	}
	public static EntityInfo fromError(Throwable ex) {
		String msg = ex.getLocalizedMessage();
		if (ex instanceof XMPPException.XMPPErrorException) {
			StanzaError se = ((XMPPException.XMPPErrorException) ex).getStanzaError();
			if (se.getDescriptiveText() != null)
				msg = se.getDescriptiveText();
			else
				msg = se.toString();
		}
		return new EntityInfo(EnumSet.noneOf(Type.class),null, null, 0, msg, null, 0, ex);
	}


	public static EntityInfo fromDisco(String jid, DiscoverInfo di) {
		EntityInfo ei = new EntityInfo();
		ei.jid = jid;
		ei.type = EnumSet.of(Type.User);
		// boring: set name to JID
		ei.name = XMPPHelper.jid2mxid(jid);
		// obtain fallback name from first identity, if available
		List<DiscoverInfo.Identity> i = di.getIdentities();
		if (i != null && i.size() > 0 && !TextUtils.isEmpty(i.get(0).getName())) {
			ei.name = i.get(0).getName();
			ei.status = jid;
		}
		if (di.containsFeature(MUCInitialPresence.NAMESPACE)) {
			// check for MUC
			// MUC disco sucks, no way to distinguish a MUC service from a room!?!
			if (jid.contains("@")) {
				ei.statusMode = StatusMode.available;
				ei.type = EnumSet.of(EntityInfo.Type.MUC);
			} else {
				ei.statusMode = StatusMode.chat;
				ei.type = EnumSet.of(EntityInfo.Type.Domain);
			}
			try {
				Form form = Form.getFormFrom(di);
				ei.name = getFormValue(form, "muc#roominfo_name");
				ei.status = getFormValue(form, "muc#roominfo_description");
				if (TextUtils.isEmpty(ei.status))
					ei.status = getFormValue(form, "muc#roominfo_subject");
				if (TextUtils.isEmpty(ei.status))
					ei.status = jid;
				ei.users = getFormIntValue(form, "muc#roominfo_occupants");
				if (ei.users < 0)
					ei.users = 0;
			} catch (Exception e) { /* ignore */ }

		} else
		if (di.containsFeature(DiscoverItems.NAMESPACE)) {
			// check for disco#items
			ei.statusMode = StatusMode.chat;
			ei.type = EnumSet.of(EntityInfo.Type.Domain);
		} else
		if (di.hasIdentity("account", "registered")) {
			// add as contact
			ei.statusMode = StatusMode.unknown;
			ei.type = EnumSet.of(EntityInfo.Type.User);
		}
		return ei;
	}

	private static String getFormValue(Form form, String fieldName) {
		if (form == null)
			return null;
		FormField field = form.getField(fieldName);
		if (field == null)
			return null;
		List<CharSequence> values = field.getValues();
		if (values == null || values.isEmpty())
			return null;
		return values.get(0).toString();
	}

	private static int getFormIntValue(Form form, String fieldName) {
		String value = getFormValue(form, fieldName);
		if (TextUtils.isEmpty(value))
			return 0;
		return Integer.parseInt(value);
	}

	public void setPresenceStatus(Presence p) {
		if (p == null)
			return;
		this.status = p.getStatus();
		Presence.Mode pm = p.getMode();
		this.statusMode = (pm == null) ? StatusMode.available : StatusMode.valueOf(pm.name());
	}
	/**
	 * Type of EntityInfo
	 *
	 * Needs to map:
	 *  - user JID
	 *  - MUC
	 *  - MUC-PM person
	 *  - server-side list / adhoc / somesuch?
	 *  - is a contact/bookmark?
	 *  - is an invitation?
	 */
	public enum Type {
		User(1),
		MUC(2),
		MUC_PM(3),
		Domain(4),
		Known(256),
		SearchResult(512);

		private int bits;
		Type(int bits) {
			this.bits = bits;
		}
		public int getValue() {
			return bits;
		}
	}
}
