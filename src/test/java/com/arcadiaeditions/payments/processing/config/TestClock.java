package com.arcadiaeditions.payments.processing.config;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/** Fixed test "now" (data.sql header). */
public final class TestClock {

    public static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");

    public static final Clock FIXED = Clock.fixed(NOW, ZoneOffset.UTC);

    private TestClock() {}
}
