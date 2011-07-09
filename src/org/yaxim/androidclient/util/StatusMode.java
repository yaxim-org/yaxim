package org.yaxim.androidclient.util;

import org.yaxim.androidclient.R;

public enum StatusMode {
	offline(R.string.status_offline, R.drawable.ic_status_available),
	dnd(R.string.status_dnd, R.drawable.ic_status_available),
	xa(R.string.status_xa, R.drawable.ic_status_available),
	away(R.string.status_away, R.drawable.ic_status_available),
	available(R.string.status_available, R.drawable.ic_status_available),
	chat(R.string.status_chat, R.drawable.ic_status_available);

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
