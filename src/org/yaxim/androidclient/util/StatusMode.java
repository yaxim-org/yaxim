package org.yaxim.androidclient.util;

import org.yaxim.androidclient.R;

public enum StatusMode {
	offline(R.string.status_offline, R.drawable.ic_status_offline),
	dnd(R.string.status_dnd, R.drawable.ic_status_dnd),
	xa(R.string.status_xa, R.drawable.ic_status_xa),
	away(R.string.status_away, R.drawable.ic_status_away),
	available(R.string.status_available, R.drawable.ic_status_available),
	chat(R.string.status_chat, R.drawable.ic_status_chat),
	subscribe(0 /* not a status you can set */, R.drawable.ic_status_subscribe);

	private final int textId;
	private final int drawableId;

	StatusMode(int textId, int drawableId) {
		this.textId = textId;
		this.drawableId = drawableId;
	}

	public int getTextId() {
		return textId;
	}

	public int getDrawableId() {
		return drawableId;
	}

	public String toString() {
		return name();
	}

	public static StatusMode fromString(String status) {
		return StatusMode.valueOf(status);
	}

}
