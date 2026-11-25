package com.arcadiaeditions.payments.processing.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Fixed test "now" for Spring tests (profile test). */
@Configuration
@Profile("test")
public class TestClockConfiguration {

    @Bean
    public Clock clock() {
        return TestClock.FIXED;
    }
}
