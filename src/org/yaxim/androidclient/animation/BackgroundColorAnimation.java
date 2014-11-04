package org.yaxim.androidclient.animation;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Transformation;

/**
 * A poor man's substitute for the PropertyAnimator introduced in
 * Ice Cream Sandwich. Given the simplicity of this it made sense to just
 * implement it rather than importing a library
 */
public class BackgroundColorAnimation extends Animation {
	private View mTarget;

	private int mStartColor;
	private int mEndColor;

	public BackgroundColorAnimation() {
		super();
	}

	/**
	 * Sets the target view of the animation
	 *
	 * @param target View to animate the background of
	 */
	public void setTarget(final View target) {
		mTarget = target;
	}

	/**
	 * Sets the colors to animate between
	 *
	 * @param startColor Starting color
	 * @param endColor Ending color
	 */
	public void setColors(final int startColor, final int endColor) {
		mStartColor = startColor;
		mEndColor = endColor;
	}

	protected void applyTransformation(float interpolatedTime, Transformation t) {
		final int currentColor = ArgbEvaluator.evaluate(interpolatedTime, mStartColor, mEndColor);
		if (mTarget != null) {
			mTarget.setBackgroundColor(currentColor);
		}
	}
}
