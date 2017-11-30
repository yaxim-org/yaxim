package org.yaxim.androidclient.util;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import android.graphics.Color;

public class XEP0392Helper {
	static final double KR = 0.299;
	static final double KG = 0.587;
	static final double KB = 0.114;
	static final double y = 0.5;

	public static double cbCrFromNick(String nickname) {
		try {
			MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
			byte[] digest = sha1.digest(nickname.getBytes("UTF-8"));
			int angle = ((int)(digest[0])&0xff) + ((int)(digest[1])&0xff)*256;
			return angle*2.*Math.PI/65536.;
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
	public static int rgbFromNick(String nick) {
		return rgbFromCbCr(cbCrFromNick(nick));
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
}
