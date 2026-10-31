package com.hrmspro.config.security;

import com.hrmspro.common.context.TenantContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String jwt = extractJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && jwtService.validateToken(jwt)) {
                Claims claims = jwtService.parseAndValidateClaims(jwt);
                UUID userId = UUID.fromString(claims.getSubject());

                UUID tenantId = null;
                String tenantIdStr = claims.get("tenant_id", String.class);
                if (StringUtils.hasText(tenantIdStr)) {
                    tenantId = UUID.fromString(tenantIdStr);
                    TenantContext.setTenantId(tenantId);
                }

                UUID membershipId = null;
                String membershipIdStr = claims.get("membership_id", String.class);
                if (StringUtils.hasText(membershipIdStr)) {
                    membershipId = UUID.fromString(membershipIdStr);
                }

                @SuppressWarnings("unchecked")
                List<String> roles = (List<String>) claims.get("roles", List.class);
                List<SimpleGrantedAuthority> authorities = roles == null ? Collections.emptyList() :
                        roles.stream()
                                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                                .collect(Collectors.toList());

                UserPrincipal principal = UserPrincipal.builder()
                        .userId(userId)
                        .email(claims.get("email", String.class))
                        .tenantId(tenantId)
                        .membershipId(membershipId)
                        .authorities(authorities)
                        .active(true)
                        .build();

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }

            filterChain.doFilter(request, response);
        } finally {
            // Clean up ThreadLocal to prevent connection/thread pollution
            TenantContext.clear();
        }
    }

    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}