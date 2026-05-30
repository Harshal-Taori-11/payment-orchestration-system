package com.payment.payment_orchestration_system.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyUtil {
    private static final int MINOR_UNIT_SCALE = 2;
    private MoneyUtil() { }

    // Handles the toMinorUnits operation.
    public static Long toMinorUnits( BigDecimal amount ) {
        return amount .setScale( MINOR_UNIT_SCALE, RoundingMode.HALF_UP )
                .movePointRight(MINOR_UNIT_SCALE)
                .longValueExact();
    }

    // Handles the toMajorUnits operation.
    public static BigDecimal toMajorUnits(Long amount ) {
        return BigDecimal.valueOf(amount) .movePointLeft(MINOR_UNIT_SCALE);
    }
}
