package org.yaxim.androidclient;

public class FlavorConfig {
	public static int getTheme(String theme) {
		switch (theme) {
		case "light":
			return R.style.YaximLightTheme;
		default:
			return R.style.YaximDarkTheme;
		}
	}
}
