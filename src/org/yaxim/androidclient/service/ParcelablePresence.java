package org.yaxim.androidclient.service;

import org.jivesoftware.smack.packet.Presence;
import org.yaxim.androidclient.util.StatusMode;

import android.os.Parcel;
import android.os.Parcelable;

public class ParcelablePresence implements Parcelable {
	public String bare_jid;
	public String resource;
	public String status;
	public StatusMode status_mode;

	public ParcelablePresence(String bare_jid, String resource, String status, StatusMode status_mode) {
		this.bare_jid = bare_jid;
		this.resource = resource;
		this.status = status;
		this.status_mode = status_mode;
	}
	
	public ParcelablePresence(Presence p) {
		String[] full_jid = p.getFrom().split("/", 2);
		this.bare_jid = full_jid[0];
		if (full_jid.length > 1)
			this.resource = full_jid[1];
		this.status = p.getStatus();
		Presence.Mode pm = p.getMode();
		this.status_mode = (pm == null) ? StatusMode.available : StatusMode.valueOf(pm.name());
	}

	@Override
	public int describeContents() {
		return 0; // Google???
	}

	@Override
	public void writeToParcel(Parcel p, int flags) {
		p.writeString(bare_jid);
		p.writeString(resource);
		p.writeString(status);
		p.writeInt(status_mode.ordinal());
	}

	public static Creator<ParcelablePresence> CREATOR = new Creator<ParcelablePresence>() {
		@Override
		public ParcelablePresence createFromParcel(Parcel source) {
			String bare_jid = source.readString();
			String resource = source.readString();
			String status = source.readString();
			StatusMode status_mode = StatusMode.values()[source.readInt()];
			return new ParcelablePresence(bare_jid, resource, status, status_mode);
		}
		@Override
		public ParcelablePresence[] newArray(int size) {
			return new ParcelablePresence[size];
		}
	};
}
