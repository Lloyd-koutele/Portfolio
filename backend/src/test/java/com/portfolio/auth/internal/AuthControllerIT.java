package com.portfolio.auth.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static com.portfolio.TestRequests.remoteAddr;
import static com.portfolio.TestRequests.xsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portfolio.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@IntegrationTest
class AuthControllerIT
{
    private static final String EMAIL = "admin@test.local";
    private static final String PASSWORD = "correct-horse-battery";
    private static final String COOKIE = "PORTFOLIO_SESSION";
    /** Each test logs in from its own address so the per-IP rate limit never leaks between tests. */
    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void adminRoutesAnswer401WithoutCookie() throws Exception
    {
        mvc.perform(get("/api/admin/pages"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(401));
        mvc.perform(post("/api/admin/pages").with(xsrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void adminAccountIsCreatedWithBcryptCost12()
    {
        String hash = jdbc.queryForObject("SELECT password_hash FROM admin_user WHERE email = ?", String.class, EMAIL);

        assertThat(hash).startsWith("$2a$12$");
    }

    @Test
    void validLoginSetsAnHttpOnlyStrictSessionCookie() throws Exception
    {
        MvcResult result = mvc.perform(login(EMAIL, PASSWORD, nextIp()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(EMAIL))
            .andExpect(cookie().httpOnly(COOKIE, true))
            .andExpect(cookie().secure(COOKIE, true))
            .andExpect(cookie().sameSite(COOKIE, "Strict"))
            .andExpect(cookie().maxAge(COOKIE, 8 * 3600))
            .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain(PASSWORD);
    }

    @Test
    void rememberMeKeepsTheSessionThirtyDays() throws Exception
    {
        mvc.perform(post("/api/auth/login").with(xsrf()).with(remoteAddr(nextIp()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\",\"remember\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mfaRequired").value(false))
            .andExpect(cookie().maxAge(COOKIE, 30 * 24 * 3600));
    }

    @Test
    void emailIsCaseInsensitive() throws Exception
    {
        mvc.perform(login("Admin@Test.LOCAL", PASSWORD, nextIp())).andExpect(status().isOk());
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameAnswer() throws Exception
    {
        String ip = nextIp();
        String wrongPassword = mvc.perform(login(EMAIL, "not-the-password", ip))
            .andExpect(status().isUnauthorized())
            .andExpect(cookie().doesNotExist(COOKIE))
            .andReturn().getResponse().getContentAsString();
        String unknownEmail = mvc.perform(login("nobody@test.local", PASSWORD, ip))
            .andExpect(status().isUnauthorized())
            .andReturn().getResponse().getContentAsString();

        assertThat(unknownEmail).isEqualTo(wrongPassword);
    }

    @Test
    void sixthAttemptWithinAMinuteIsRejectedWith429() throws Exception
    {
        String ip = nextIp();
        for (int attempt = 1; attempt <= 5; attempt++)
        {
            mvc.perform(login(EMAIL, "wrong-" + attempt, ip)).andExpect(status().isUnauthorized());
        }

        mvc.perform(login(EMAIL, PASSWORD, ip))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
            .andExpect(jsonPath("$.status").value(429));
        mvc.perform(login(EMAIL, PASSWORD, nextIp())).andExpect(status().isOk());
    }

    @Test
    void forgedForwardedForFromAnUntrustedClientDoesNotBypassTheLimit() throws Exception
    {
        String ip = nextIp();
        for (int attempt = 1; attempt <= 5; attempt++)
        {
            mvc.perform(login(EMAIL, "wrong", ip).header("X-Forwarded-For", "203.0.113." + attempt))
                .andExpect(status().isUnauthorized());
        }

        mvc.perform(login(EMAIL, PASSWORD, ip).header("X-Forwarded-For", "203.0.113.99"))
            .andExpect(status().isTooManyRequests());
    }

    @Test
    void loginWithoutCsrfTokenIsForbidden() throws Exception
    {
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials(EMAIL, PASSWORD)))
            .andExpect(status().isForbidden())
            .andExpect(cookie().doesNotExist(COOKIE));
    }

    @Test
    void invalidBodyIsRejectedWithFieldErrors() throws Exception
    {
        mvc.perform(post("/api/auth/login").with(xsrf()).with(remoteAddr(nextIp()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\",\"password\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.email").exists())
            .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void meReturnsTheAdminWithAValidCookieAnd401Otherwise() throws Exception
    {
        Cookie session = loginAndGetCookie();

        mvc.perform(get("/api/auth/me").cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(EMAIL));
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void validCookiePassesTheAdminGuard() throws Exception
    {
        Cookie session = loginAndGetCookie();

        int statusCode = mvc.perform(get("/api/admin/pages").cookie(session))
            .andReturn().getResponse().getStatus();

        assertThat(statusCode).isNotIn(401, 403);
    }

    @Test
    void tamperedTokenIsIgnoredAndCleared() throws Exception
    {
        Cookie session = loginAndGetCookie();
        String value = session.getValue();
        Cookie tampered = new Cookie(COOKIE, value.substring(0, value.length() - 2) + "xx");

        mvc.perform(get("/api/auth/me").cookie(tampered))
            .andExpect(status().isUnauthorized())
            .andExpect(cookie().maxAge(COOKIE, 0));
        mvc.perform(get("/actuator/health").cookie(tampered)).andExpect(status().isOk());
    }

    @Test
    void logoutClearsTheCookie() throws Exception
    {
        mvc.perform(post("/api/auth/logout").with(xsrf()))
            .andExpect(status().isNoContent())
            .andExpect(cookie().maxAge(COOKIE, 0))
            .andExpect(cookie().value(COOKIE, ""));
    }

    @Test
    void csrfEndpointIssuesAReadableXsrfCookie() throws Exception
    {
        mvc.perform(get("/api/auth/csrf"))
            .andExpect(status().isNoContent())
            .andExpect(cookie().exists("XSRF-TOKEN"))
            .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
    }

    @Test
    void rawCsrfTokenFromCookieIsAcceptedInHeader() throws Exception
    {
        Cookie xsrf = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).isNotNull();

        mvc.perform(post("/api/auth/login").with(remoteAddr(nextIp()))
                .cookie(xsrf)
                .header("X-XSRF-TOKEN", xsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials(EMAIL, PASSWORD)))
            .andExpect(status().isOk());
    }

    private Cookie loginAndGetCookie() throws Exception
    {
        Cookie session = mvc.perform(login(EMAIL, PASSWORD, nextIp()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getCookie(COOKIE);
        assertThat(session).isNotNull();
        return session;
    }

    private static MockHttpServletRequestBuilder login(String email, String password, String ip)
    {
        return post("/api/auth/login")
            .with(xsrf())
            .with(remoteAddr(ip))
            .contentType(MediaType.APPLICATION_JSON)
            .content(credentials(email, password));
    }

    private static String credentials(String email, String password)
    {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private static String nextIp()
    {
        return "192.0.2." + NEXT_IP.getAndIncrement();
    }
}
