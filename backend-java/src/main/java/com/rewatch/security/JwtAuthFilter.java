package com.rewatch.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.rewatch.model.User;
import com.rewatch.repository.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Reads `Authorization: Bearer <token>`, and if it's a valid, non-revoked JWT,
 * authenticates the request as that user id for the rest of the filter chain.
 * Absent/invalid tokens simply leave the request unauthenticated — routes that
 * require auth are rejected downstream by SecurityConfig, and routes that
 * permit anonymous access (the logged-out browsing paths) proceed with a null
 * principal.
 *
 * This does one DB read per authenticated request (there wasn't one before) —
 * needed both to check the token's embedded tokenVersion against the live
 * column (revocation) and to grant ROLE_ADMIN based on the live role, so an
 * admin grant/revoke takes effect on the very next request rather than only
 * after the user's token happens to expire.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepo;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepo) {
        this.jwtService = jwtService;
        this.userRepo = userRepo;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            JwtService.ValidatedToken parsed = jwtService.validate(token);

            if (parsed != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                User user = userRepo.findById(parsed.userId()).orElse(null);
                if (user != null && user.getTokenVersion() == parsed.tokenVersion()) {
                    List<GrantedAuthority> authorities = new ArrayList<>();
                    authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                    if (user.getRole() == User.Role.ADMIN) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                    }
                    var authentication = new UsernamePasswordAuthenticationToken(
                            user.getId(), null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
                // A mismatched tokenVersion (revoked) or a deleted user: the request
                // proceeds unauthenticated, same as an invalid token today.
            }
        }

        filterChain.doFilter(request, response);
    }
}
