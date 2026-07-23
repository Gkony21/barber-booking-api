package gr.barber.booking.security;

import org.springframework.stereotype.Component;

@Component
public class JwtAuthenticationFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

}