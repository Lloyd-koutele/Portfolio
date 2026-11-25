package com.portfolio;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Request helpers for integration tests. {@link #xsrf()} sends a matching XSRF cookie and header, exactly
 * like the browser client does, instead of Spring's {@code csrf()} helper which swaps the token repository.
 */
public final class TestRequests
{
    public static final String ADMIN_EMAIL = "admin@test.local";
    public static final String ADMIN_PASSWORD = "correct-horse-battery";
    public static final String SESSION_COOKIE = "PORTFOLIO_SESSION";

    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    private TestRequests()
    {
    }

    /**
     * Logs in as the test administrator (from a fresh address, so the rate limit never interferes).
     */
    public static Cookie adminSession(MockMvc mvc) throws Exception
    {
        Cookie cookie = mvc.perform(post("/api/auth/login")
                .with(xsrf())
                .with(remoteAddr("198.18.0." + (NEXT_IP.getAndIncrement() % 250 + 1)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + ADMIN_EMAIL + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
            .andReturn().getResponse().getCookie(SESSION_COOKIE);
        if (cookie == null)
        {
            throw new IllegalStateException("Admin login failed in test setup");
        }
        return cookie;
    }

    public static RequestPostProcessor xsrf()
    {
        String token = UUID.randomUUID().toString();
        return request ->
        {
            List<Cookie> cookies = new ArrayList<>();
            if (request.getCookies() != null)
            {
                cookies.addAll(Arrays.asList(request.getCookies()));
            }
            cookies.add(new Cookie("XSRF-TOKEN", token));
            request.setCookies(cookies.toArray(new Cookie[0]));
            request.addHeader("X-XSRF-TOKEN", token);
            return request;
        };
    }

    public static RequestPostProcessor remoteAddr(String ip)
    {
        return request ->
        {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
