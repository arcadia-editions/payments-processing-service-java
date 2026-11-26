package com.arcadiaeditions.payments.processing.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class ClockConfiguration {

    @Bean
    @Profile("!test")
    public Clock clock() {
        return Clock.systemUTC();
    }
}
