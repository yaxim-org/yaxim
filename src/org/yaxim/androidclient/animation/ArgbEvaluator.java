package org.yaxim.androidclient.animation;

import android.animation.TypeEvaluator;

/**
 * This evaluator can be used to perform type interpolation between integer
 * values that represent ARGB colors.
 */
public class ArgbEvaluator {

	/**
	 * This function returns the calculated in-between value for a color
	 * given integers that represent the start and end values in the four
	 * bytes of the 32-bit int. Each channel is separately linearly interpolated
	 * and the resulting calculated values are recombined into the return value.
	 *
	 * @param fraction The fraction from the starting to the ending values
	 * @param startValue A 32-bit int value representing colors in the
	 * separate bytes of the parameter
	 * @param endValue A 32-bit int value representing colors in the
	 * separate bytes of the parameter
	 * @return A value that is calculated to be the linearly interpolated
	 * result, derived by separating the start and end values into separate
	 * color channels and interpolating each one separately, recombining the
	 * resulting values in the same way.
	 */
	public static int evaluate(float fraction, int startValue, int endValue) {
		int startA = (startValue >> 24);
		int startR = (startValue >> 16) & 0xff;
		int startG = (startValue >> 8) & 0xff;
		int startB = startValue & 0xff;

		int endA = (endValue >> 24);
		int endR = (endValue >> 16) & 0xff;
		int endG = (endValue >> 8) & 0xff;
		int endB = endValue & 0xff;

		return (int)((startA + (int)(fraction * (endA - startA))) << 24) |
				(int)((startR + (int)(fraction * (endR - startR))) << 16) |
				(int)((startG + (int)(fraction * (endG - startG))) << 8) |
				(int)((startB + (int)(fraction * (endB - startB))));
	}
}
