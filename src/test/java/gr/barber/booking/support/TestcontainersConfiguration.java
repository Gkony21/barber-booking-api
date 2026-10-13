package gr.barber.booking.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    // Monday 7 January 2030, 10:00 in Athens.
    public static final Instant NOW = Instant.parse("2030-01-07T08:00:00Z");

    // Same major version as local development, so migrations behave identically.
    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:16-alpine");
    }

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock(NOW);
    }
}
