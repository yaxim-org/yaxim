package org.yaxim.androidclient.data;

import org.yaxim.androidclient.util.StatusMode;

import android.os.Parcel;
import android.os.Parcelable;

public class RosterItem implements Parcelable, Comparable<RosterItem> {

	public final String jabberID;
	public final String screenName;
	public final String statusMessage;
	public final String group;

	private final String statusMode;

	public RosterItem(String jabberID, String screenName,
			StatusMode statusMode, String statusMessage, String group) {
		this.jabberID = jabberID;
		this.screenName = screenName;
		this.statusMode = statusMode.name();
		this.statusMessage = statusMessage;
		this.group = group;
	}

	private RosterItem(Parcel in) {
		this.jabberID = in.readString();
		this.screenName = in.readString();
		this.statusMode = in.readString();
		this.statusMessage = in.readString();
		this.group = in.readString();
	}

	@SuppressWarnings("unchecked")
	public static final Parcelable.Creator CREATOR = new Parcelable.Creator() {

		public RosterItem createFromParcel(Parcel in) {
			return new RosterItem(in);
		}

		public RosterItem[] newArray(int size) {
			return new RosterItem[size];

		}
	};

	public void writeToParcel(Parcel out, int flags) {
		out.writeString(jabberID);
		out.writeString(screenName);
		out.writeString(statusMode);
		out.writeString(statusMessage);
		out.writeString(group);
	}

	public StatusMode getStatusMode() {
		return StatusMode.valueOf(statusMode);
	}

	public int describeContents() {
		return 0;
	}

	public String toString() {
		return this.screenName;
	}

	public int compareTo(RosterItem that) {
		if (this.getStatusMode() == that.getStatusMode()) {
			return this.screenName.compareTo(that.screenName);
		} else {
			return compareStatusModes(that.getStatusMode());
		}
	}

	private int compareStatusModes(StatusMode otherMode) {
		int compVal = (otherMode.getPriority() - this.getStatusMode()
				.getPriority());
		if (compVal < 0) {
			return -1;
		}
		if (compVal > 0) {
			return 1;
		}
		return 0;
	}
}
