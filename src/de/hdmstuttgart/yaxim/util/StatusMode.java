package de.hdmstuttgart.yaxim.util;

public enum StatusMode {
	chat(5),
	available(4),
	away(3),
	xa(2),
	dnd(1),
	offline(0);
	
	private int priority;
	
	private StatusMode(int weight) {
		this.priority = weight;
	}
	
	public String toString() {
		return name();
	}
	public int getPriority() {
		return priority;
	}
}
