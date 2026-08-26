package com.rewatch.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.rewatch.model.User;
import com.rewatch.repository.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Unit-level pin on JwtAuthFilter's own branching, underneath
 * SecurityConfigTest's end-to-end coverage of the same scenarios through
 * the real HTTP filter chain.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock private JwtService jwtService;
    @Mock private UserRepository userRepo;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    private JwtAuthFilter filter() {
        return new JwtAuthFilter(jwtService, userRepo);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private User userWithRole(User.Role role, int tokenVersion) {
        User user = new User();
        user.setId(1L);
        user.setRole(role);
        user.setTokenVersion(tokenVersion);
        return user;
    }

    @Test
    void noAuthorizationHeaderLeavesTheRequestUnauthenticatedAndStillProceeds() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter().doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void aHeaderWithoutTheBearerPrefixIsIgnored() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        filter().doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void anInvalidTokenLeavesTheRequestUnauthenticated() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer garbage");
        when(jwtService.validate("garbage")).thenReturn(null);

        filter().doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void aValidTokenForAnOrdinaryUserAuthenticatesWithRoleUserOnly() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer good-token");
        when(jwtService.validate("good-token")).thenReturn(new JwtService.ValidatedToken(1L, 0));
        when(userRepo.findById(1L)).thenReturn(Optional.of(userWithRole(User.Role.USER, 0)));

        filter().doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(1L);
        List<String> authorities = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        assertThat(authorities).containsExactly("ROLE_USER");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void aValidTokenForAnAdminGrantsBothRoles() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer admin-token");
        when(jwtService.validate("admin-token")).thenReturn(new JwtService.ValidatedToken(1L, 0));
        when(userRepo.findById(1L)).thenReturn(Optional.of(userWithRole(User.Role.ADMIN, 0)));

        filter().doFilterInternal(request, response, filterChain);

        List<String> authorities = SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        assertThat(authorities).containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void aTokenWhoseEmbeddedVersionDoesNotMatchTheLiveRowIsTreatedAsRevoked() throws Exception {
        // The whole revocation mechanism: tv=0 in the token, live row now at 1.
        when(request.getHeader("Authorization")).thenReturn("Bearer stale-token");
        when(jwtService.validate("stale-token")).thenReturn(new JwtService.ValidatedToken(1L, 0));
        when(userRepo.findById(1L)).thenReturn(Optional.of(userWithRole(User.Role.USER, 1)));

        filter().doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void aTokenForAUserThatNoLongerExistsIsTreatedAsUnauthenticated() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer deleted-user-token");
        when(jwtService.validate("deleted-user-token")).thenReturn(new JwtService.ValidatedToken(1L, 0));
        when(userRepo.findById(1L)).thenReturn(Optional.empty());

        filter().doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void anAlreadyAuthenticatedContextIsNeverOverwritten() throws Exception {
        // validate() still runs (the null-authentication check only gates
        // whether a *new* Authentication gets set, not the lookup itself) —
        // what actually matters is the existing context survives untouched.
        Authentication existing = new UsernamePasswordAuthenticationToken(999L, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(existing);
        when(request.getHeader("Authorization")).thenReturn("Bearer good-token");
        when(jwtService.validate("good-token")).thenReturn(new JwtService.ValidatedToken(1L, 0));

        filter().doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
        verify(userRepo, never()).findById(any());
        verify(filterChain).doFilter(request, response);
    }
}
