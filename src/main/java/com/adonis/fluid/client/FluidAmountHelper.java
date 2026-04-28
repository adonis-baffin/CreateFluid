package com.adonis.fluid.client;

public class FluidAmountHelper {

	public static String format(int amountMB) {
		String text = String.valueOf(amountMB / 1000f);
		if (text.endsWith(".0")) {
			text = text.substring(0, text.length() - 2);
		}
		return text;
	}

	public static String formatWithUnit(int amountMB) {
		int clamped = Math.max(0, amountMB);
		if (clamped < 1000) {
			return clamped + "mb";
		}
		if (clamped % 1000 == 0) {
			return clamped / 1000 + "b";
		}
		return format(clamped) + "b";
	}
}
