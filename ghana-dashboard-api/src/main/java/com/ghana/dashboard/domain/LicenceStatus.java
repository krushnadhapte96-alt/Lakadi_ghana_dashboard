package com.ghana.dashboard.domain;

/** Derived from {@code expiry_date} by the daily sweep; never set by hand. */
public enum LicenceStatus {
    ACTIVE,
    EXPIRING_SOON,
    EXPIRED
}