package org.yaxim.androidclient;

import android.app.Application;
import android.content.Context;

import de.duenndns.ssl.MemorizingTrustManager;

public class YaximApplication extends Application {
	// MTM is needed globally for both the backend (connect)
	// and the frontend (display dialog)
	public MemorizingTrustManager mMTM;

	public YaximApplication() {
		super();
	}

	@Override
	public void onCreate() {
		mMTM = new MemorizingTrustManager(this);
	}

	public static YaximApplication getApp(Context ctx) {
		return (YaximApplication)ctx.getApplicationContext();
	}
}

