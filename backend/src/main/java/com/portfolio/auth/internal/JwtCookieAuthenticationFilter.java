package com.portfolio.auth.internal;

import com.portfolio.auth.AdminPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates the request from the session cookie. An invalid or expired token never blocks the request:
 * the visitor simply stays anonymous (protected routes then answer 401) and the stale cookie is cleared.
 */
class JwtCookieAuthenticationFilter extends OncePerRequestFilter
{
    private final JwtService jwtService;
    private final SessionCookies cookies;

    JwtCookieAuthenticationFilter(JwtService jwtService, SessionCookies cookies)
    {
        this.jwtService = jwtService;
        this.cookies = cookies;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException
    {
        Optional<String> token = cookies.read(request);
        if (token.isPresent())
        {
            Optional<AdminPrincipal> admin = jwtService.verify(token.get());
            if (admin.isPresent())
            {
                UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                    admin.get(), null, List.of(new SimpleGrantedAuthority("ROLE_" + JwtService.ADMIN_ROLE)));
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
            }
            else
            {
                response.addHeader(HttpHeaders.SET_COOKIE, cookies.clear().toString());
            }
        }
        chain.doFilter(request, response);
    }
}
