package gr.barber.booking.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Runs @Async methods (the email listeners) on Spring Boot's background thread pool, so a slow
 * mail server never makes a booking or sign-up wait. Off in tests (app.async.enabled=false),
 * where emails are checked right after the request.
 */
@Configuration
@EnableAsync
@ConditionalOnProperty(value = "app.async.enabled", havingValue = "true", matchIfMissing = true)
public class AsyncConfig {
}
