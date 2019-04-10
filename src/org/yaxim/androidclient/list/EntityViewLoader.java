package org.yaxim.androidclient.list;

import android.app.Activity;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;

import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.StanzaError;
import org.jivesoftware.smackx.disco.ServiceDiscoveryManager;
import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
import org.jivesoftware.smackx.vcardtemp.VCardManager;
import org.jivesoftware.smackx.vcardtemp.packet.VCard;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.yaxim.androidclient.YaximApplication;
import org.yaxim.androidclient.data.EntityInfo;

/**
 * AsyncTask for loading info about a given JID, and storing it into an entity view.
 *
 */
class EntityViewLoader extends AsyncTask<String, EntityInfo, Throwable> {
	String search;
	LayoutInflater inflater;
	View view;

	public EntityViewLoader(Activity act, View view) {
		inflater = (LayoutInflater) act.getSystemService(act.LAYOUT_INFLATER_SERVICE);
		this.view = view;
	}

	void loadJid(XMPPConnection c, String jid) throws Exception {
		// validate that it's a bare JID or a domain JID
		Jid smackJid;
		try {
			smackJid = JidCreate.from(jid);
		} catch (Exception e) {
			publishProgress(null);
			return;
		}
		ServiceDiscoveryManager sm = ServiceDiscoveryManager.getInstanceFor(c);
		try {
			DiscoverInfo di = sm.discoverInfo(smackJid);
			publishProgress(EntityInfo.fromDisco(jid, di));
		} catch (XMPPException.XMPPErrorException e) {
			switch (e.getStanzaError().getCondition()) {
			case remote_server_not_found: // user hasn't finished typing yet
				publishProgress(null);
				break;
			case service_unavailable: // this is maybe a user account
			case subscription_required: // this is probably a user account (from ejabberd)
				publishProgress(EntityInfo.fromJid(jid));
				break;
			default:
				throw e;
			}
		}
		try {
			VCard vc = VCardManager.getInstanceFor(c).loadVCard(smackJid.asEntityBareJidOrThrow());
			String nick = vc.getNickName();
			String fn = vc.getField("FN");
			String name;
			if (nick.length() > 0 && fn.length() > 0)
				name = nick + " (" + fn + ")";
			else if (nick.length() > 0)
				name = nick;
			else if (fn.length() > 0)
				name = fn;
			else return;
			publishProgress(EntityInfo.fromJidName(jid, name));
		} catch (Exception e2) {
			// ignore
		}
	}

	@Override
	protected Throwable doInBackground(String... param) {
		if (param.length > 0 && param[0].length() > 0) {
			search = param[0];
			XMPPConnection c = YaximApplication.getInstance().getSmackable().getConnection();
			try {
				loadJid(c, search);
			} catch (Exception e) {
				publishProgress(EntityInfo.fromError(e));
			}
		} else publishProgress(null);
		return null;
	}

	@Override
	protected void onProgressUpdate(EntityInfo... pp) {
		if (view == null && pp == null)
			return; // nothing to do
		else if (view != null) {
			int v = (pp == null) ? View.GONE : View.VISIBLE;
			EntityInfo ei = (pp != null) ? pp[0] : new EntityInfo();
			view = EntityListAdapter.getChildView(inflater, ei, false, view, null);
			view.setVisibility(v);
		}
	}

	@Override
	protected void onPostExecute(Throwable ex) {
	}

}
