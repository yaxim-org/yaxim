package org.yaxim.androidclient.util;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import android.content.res.Resources;
import android.graphics.Color;
import android.util.TypedValue;

import org.hsluv.HUSLColorConverter;
import org.yaxim.androidclient.R;

public class XEP0392Helper {
	static final double KR=0.2627;
	static final double KG = 0.587;
	static final double KB=0.0593;
	static final double y = 0.5;

	public static double angleFromNick(String nickname) {
		try {
			MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
			byte[] digest = sha1.digest(nickname.getBytes("UTF-8"));
			int angle = ((int)(digest[0])&0xff) + ((int)(digest[1])&0xff)*256;
			return angle/65536.;
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return 0.0;
	}
	public static int clipColorValue(double color) {
		return (int)Math.max(0, Math.min(255, color*255));
	}

	public static int rgbFromCbCr(double angle) {
		// copy&pasted from XEP-0392
		double cr = Math.sin(angle);
		double cb = Math.cos(angle);
		double factor = 0.5;
		//if (Math.abs(cr) > Math.abs(cb)) {
		//	factor = 0.5 / Math.abs(cr);
		//} else {
		//	factor = 0.5 / Math.abs(cb);
		//}
		cb = cb * factor;
		cr = cr * factor;

		double r = 2*(1 - KR)*cr + y;
		double b = 2*(1 - KB)*cb + y;
		double g = (y - KR*r - KB*b)/KG;
		return Color.rgb(clipColorValue(r), clipColorValue(g), clipColorValue(b));
	}
	public static int rgbFromNickCbCr(String nick) {
		return rgbFromCbCr(angleFromNick(nick)*2*Math.PI);
	}
	public static int rgbFromNick(String nick) {
		double[] hsluv = new double[3];
		hsluv[0] = angleFromNick(nick) * 360;
		hsluv[1] = 100;
		hsluv[2] = 50;
		double[] rgb = HUSLColorConverter.hsluvToRgb(hsluv);
		return Color.rgb((int) Math.round(rgb[0] * 255), (int) Math.round(rgb[1] * 255), (int) Math.round(rgb[2] * 255));
	}

	public static int mixValues(int fg, int bg, int factor) {
		return (fg*(255-factor) + (255-bg)*factor)/255;
	}
	public static int mixColors(int fg, int bg, int factor) {
		int r = mixValues(Color.red(fg), Color.red(bg), factor);
		int g = mixValues(Color.green(fg), Color.green(bg), factor);
		int b = mixValues(Color.blue(fg), Color.blue(bg), factor);
		return Color.rgb(r, g, b);
	}
	public static int mixNickWithBackground(String nick, Resources.Theme theme, int yaxim_theme) {
		// obtain theme's background color - https://stackoverflow.com/a/14468034/539443
		TypedValue tv = new TypedValue();
		theme.resolveAttribute(android.R.attr.windowBackground, tv, true);
		if (tv.type < TypedValue.TYPE_FIRST_COLOR_INT || tv.type > TypedValue.TYPE_LAST_COLOR_INT) {
			// fall back to black or white, depending on theme
			tv.data = (yaxim_theme == R.style.YaximLightTheme) ? 0xffffff : 0x000000;
		}
		return mixColors(rgbFromNick(nick), tv.data, 100 /*0.4*/);
	}
}
