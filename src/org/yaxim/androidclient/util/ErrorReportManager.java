package org.yaxim.androidclient.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import org.yaxim.androidclient.YaximApplication;

import ee.smmv.trace.ExceptionHandler;

/**
 * This class is responsible for centrally managing error / crash reports and for sending them
 * to the developer.
 */

public class ErrorReportManager {
	static final String TAG = "yaxim.ErrorReportMgr";
	static ErrorReportManager manager;
	static final Thread.UncaughtExceptionHandler NOP_HANDLER = new Thread.UncaughtExceptionHandler() {
		@Override
		public void uncaughtException(Thread thread, Throwable throwable) {
			//nop!
		}
	};

	Thread.UncaughtExceptionHandler handler;
	Context ctx;

	synchronized public static ErrorReportManager get(Context ctx) {
		if (manager == null)
			manager = new ErrorReportManager(ctx);
		return manager;
	}

	public ErrorReportManager(Context ctx) {
		this.ctx = ctx;
		String appVersion = "unknown";
		try {
			PackageInfo packageInfo = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
			appVersion = packageInfo.versionName;
		} catch (PackageManager.NameNotFoundException e) {
		}
		final String filePath = ctx.getDir("stacktraces", 0).getAbsolutePath();
		this.handler = new ExceptionHandler(NOP_HANDLER, appVersion, filePath, false);
	}

	public void report(Throwable e) {
		if (!YaximApplication.getInstance().getConfig().reportCrash)
			return;
		handler.uncaughtException(Thread.currentThread(), e);
		sendReports();
	}
	public void sendReports() {
		new Thread() {
			@Override public void run() {
				//Log.d(TAG, "Submitting stack traces...");
				//ExceptionHandler.submitStackTraces();
				//Log.d(TAG, "Finished submitting stack traces...");
			}
		}.start();
	}
}
