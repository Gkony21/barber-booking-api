package gr.barber.booking.security;

import gr.barber.booking.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebSecurityTest extends IntegrationTest {

    @Test
    void webAppOriginMayCallTheApi() throws Exception {
        mockMvc.perform(options("/appointments")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")));
    }

    @Test
    void otherOriginsAreRefused() throws Exception {
        mockMvc.perform(options("/appointments")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingOrInvalidTokenIsUnauthorizedNotForbidden() throws Exception {
        mockMvc.perform(get("/appointments/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/appointments/me").header("Authorization", "Bearer not-a-token"))
                .andExpect(status().isUnauthorized());
    }
}
