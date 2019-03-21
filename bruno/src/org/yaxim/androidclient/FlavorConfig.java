package org.yaxim.androidclient;

public class FlavorConfig {
	public static int getTheme(String theme) {
		switch (theme) {
		case "ice":
			return R.style.YaximIceTheme;
		case "pine":
			return R.style.YaximPineTheme;
		case "light":
			return R.style.YaximLightTheme;
		default:
			return R.style.YaximDarkTheme;
		}
	}
}
