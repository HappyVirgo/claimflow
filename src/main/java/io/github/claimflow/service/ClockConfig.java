package io.github.claimflow.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the clock used for report dates and audit timestamps. Setting
 * {@code claimflow.clock.fixed-date} (ISO date) pins "today" so tests and the benchmark stay valid
 * against the fixed policy terms in the seed data.
 */
@Configuration
public class ClockConfig {

    /** System UTC clock, or a clock fixed at noon UTC on the configured date. */
    @Bean
    public Clock clock(@Value("${claimflow.clock.fixed-date:}") String fixedDate) {
        if (fixedDate.isBlank()) {
            return Clock.systemUTC();
        }
        return Clock.fixed(LocalDate.parse(fixedDate).atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    }
}
