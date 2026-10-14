package gr.barber.booking.security;

import gr.barber.booking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "app.rate-limit.enabled=true")
class RateLimitTest extends IntegrationTest {

    @Test
    void tooManyLoginAttemptsFromOneAddressAreRefusedForAWhile() throws Exception {
        for (int i = 0; i < 10; i++) {
            login().andExpect(status().isUnauthorized());
        }

        login().andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                // CORS headers are present, so the browser can read the 429.
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));

        clock.advance(Duration.ofMinutes(11));
        login().andExpect(status().isUnauthorized());
    }

    @Test
    void otherEndpointsAreNotLimited() throws Exception {
        for (int i = 0; i < 15; i++) {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/barbers"))
                    .andExpect(status().isOk());
        }
    }

    private ResultActions login() throws Exception {
        return mockMvc.perform(post("/auth/login")
                .header("Origin", "http://localhost:5173")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@test.gr\",\"password\":\"wrong-pass\"}"));
    }
}
