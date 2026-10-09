package com.ghana.dashboard.common;

import java.time.LocalDate;
import java.time.ZoneId;

public final class DateDefaults {

    private static final ZoneId SHOP_ZONE = ZoneId.of("Asia/Kolkata");

    private DateDefaults() {
    }

    public static LocalDate today() {
        return LocalDate.now(SHOP_ZONE);
    }

    public static LocalDate monthStart() {
        return today().withDayOfMonth(1);
    }

    public static void requireOrdered(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new DomainException(400, "Start date must not be after end date");
        }
    }
}
