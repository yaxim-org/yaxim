package org.yaxim.androidclient;

import org.yaxim.androidclient.data.YaximConfiguration;

import android.app.Activity;
import android.app.Application;
import android.app.Service;
import android.content.Context;
import android.preference.PreferenceManager;

import de.duenndns.ssl.MemorizingTrustManager;

public class YaximApplication extends Application {
	// identity name and type, see:
	// http://xmpp.org/registrar/disco-categories.html
	public static final String XMPP_IDENTITY_NAME = "yaxim";
	public static final String XMPP_IDENTITY_TYPE = "phone";

	// MTM is needed globally for both the backend (connect)
	// and the frontend (display dialog)
	public MemorizingTrustManager mMTM;

	private YaximConfiguration mConfig;

	public YaximApplication() {
		super();
	}

	@Override
	public void onCreate() {
		mMTM = new MemorizingTrustManager(this);
		mConfig = new YaximConfiguration(PreferenceManager
				.getDefaultSharedPreferences(this));
	}

	public static YaximApplication getApp(Activity ctx) {
		android.util.Log.d("YaximApplication", "app = " + ctx.getApplication());
		return (YaximApplication)ctx.getApplication();
	}
	public static YaximApplication getApp(Service ctx) {
		android.util.Log.d("YaximApplication", "app = " + ctx.getApplication());
		return (YaximApplication)ctx.getApplication();
	}

	public static YaximConfiguration getConfig(Activity ctx) {
		return getApp(ctx).mConfig;
	}
	public static YaximConfiguration getConfig(Service ctx) {
		return getApp(ctx).mConfig;
	}
}

