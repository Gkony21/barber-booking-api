package gr.barber.booking.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(BookingProperties.class)
public class TimeConfig {

    // Injected instead of calling Instant.now() directly, so tests can control "now".
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
