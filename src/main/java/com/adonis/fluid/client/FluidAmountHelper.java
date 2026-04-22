package com.adonis.fluid.client;

public class FluidAmountHelper {

	public static String format(int amountMB) {
		// 统一显示为桶数小数，不带单位后缀，节省空间
		String text = String.valueOf(amountMB / 1000f);
		if (text.endsWith(".0")) {
			text = text.substring(0, text.length() - 2);
		}
		return text;
	}
}
