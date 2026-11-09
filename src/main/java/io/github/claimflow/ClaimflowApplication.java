package io.github.claimflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Spring Boot entry point for the claimflow service. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ClaimflowApplication {

    /** Starts the application. */
    public static void main(String[] args) {
        SpringApplication.run(ClaimflowApplication.class, args);
    }
}
