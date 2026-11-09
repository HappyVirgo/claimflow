package io.github.claimflow.service;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Triage configuration.
 *
 * @param fastTrackMaxAmount claims with a larger total always go to a human adjuster
 */
@ConfigurationProperties(prefix = "claimflow.triage")
public record TriageProperties(BigDecimal fastTrackMaxAmount) {
}
