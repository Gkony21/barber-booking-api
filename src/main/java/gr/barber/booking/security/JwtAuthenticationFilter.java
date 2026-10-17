package gr.barber.booking.security;

import gr.barber.booking.model.User;
import gr.barber.booking.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // An expired, tampered or revoked token leaves the request unauthenticated, so public
            // endpoints still work and protected ones answer 401 (the web app then signs out).
            currentUser(authHeader.substring(7)).ifPresent(user ->
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                            user.getEmail(),
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))));
        }

        filterChain.doFilter(request, response);
    }

    // The account must still exist and the token must not predate a password reset. The role is
    // the current one from the database, so a change takes effect without signing in again.
    private Optional<User> currentUser(String token) {
        Claims claims;
        try {
            claims = jwtService.parse(token);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
        Integer version = claims.get(JwtService.VERSION_CLAIM, Integer.class);
        return Optional.ofNullable(claims.getSubject())
                .flatMap(userRepository::findByEmail)
                .filter(user -> Objects.equals(version, user.getTokenVersion()));
    }
}
