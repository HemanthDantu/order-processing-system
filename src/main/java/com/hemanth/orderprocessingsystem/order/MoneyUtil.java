package com.hemanth.orderprocessingsystem.order;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money helpers for consistent scale and rounding.
 */
public final class MoneyUtil {

    public static final String DEFAULT_CURRENCY = "USD";
    private static final int MONEY_SCALE = 2;

    private MoneyUtil() {
    }

    /**
     * Normalizes a money amount to the project-wide scale.
     */
    public static BigDecimal normalize(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Calculates a line total from quantity and unit price.
     */
    public static BigDecimal lineTotal(int quantity, BigDecimal unitPrice) {
        return normalize(unitPrice).multiply(BigDecimal.valueOf(quantity)).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
