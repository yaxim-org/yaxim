package org.yaxim.androidclient.util;

import org.yaxim.androidclient.R;

public enum StatusMode {
	offline(R.string.status_offline, R.drawable.offline),
	dnd(R.string.status_dnd, R.drawable.donotdisturb),
	xa(R.string.status_xa, R.drawable.xa),
	away(R.string.status_away, R.drawable.away),
	available(R.string.status_available, R.drawable.ic_status_available),
	chat(R.string.status_chat, R.drawable.chat);

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

	public int getPriority() {
		return ordinal();
	}

	public static StatusMode fromString(String status) {
		return StatusMode.valueOf(status);
	}

}
