package org.yaxim.androidclient;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.yaxim.androidclient.data.YaximConfiguration;

/**
 * Base Activity for all non-preference yaxim activities.
 *
 * Provides theme, config and custom ActionBar.
 */

public class ThemedActivity extends AppCompatActivity {
	protected ActionBar actionBar;
	protected YaximConfiguration mConfig;
	protected String mTheme;
	protected ImageView mStatusMode;
	protected TextView mTitle;
	protected TextView mSubTitle;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mConfig = YaximApplication.getConfig();
		mTheme = mConfig.theme;
		setTheme(mConfig.getTheme());
		actionBar = getSupportActionBar();
		// set custom title layout
		LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
		View layout = inflater.inflate(R.layout.chat_action_title, null);
		mStatusMode = (ImageView)layout.findViewById(R.id.action_bar_status);
		mTitle = (TextView)layout.findViewById(R.id.action_bar_title);
		mSubTitle = (TextView)layout.findViewById(R.id.action_bar_subtitle);
		mTitle.setText(getTitle());
		actionBar.setTitle(null);
		actionBar.setCustomView(layout);
		actionBar.setDisplayShowCustomEnabled(true);
		layout.setClickable(true);
		layout.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				onTitleClicked(view);
			}
		});
	}
	@Override
	public void setTitle(CharSequence title) {
		mTitle.setText(title);

	}
	public void setSubtitle(CharSequence subtitle) {
		mSubTitle.setText(subtitle);
		mSubTitle.setVisibility(TextUtils.isEmpty(subtitle) ? View.GONE : View.VISIBLE);

	}
	public void setIcon(int drawable) {
		mStatusMode.setImageDrawable(getResources().getDrawable(drawable /*, mConfig.getTheme()*/));
	}
	protected void onTitleClicked(View view) {
		Log.d("ThemedActivity", "Title clicked: " + view);
	}
}

