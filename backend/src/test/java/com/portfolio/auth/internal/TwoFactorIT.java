package com.portfolio.auth.internal;

import static com.portfolio.TestRequests.ADMIN_EMAIL;
import static com.portfolio.TestRequests.ADMIN_PASSWORD;
import static com.portfolio.TestRequests.SESSION_COOKIE;
import static com.portfolio.TestRequests.adminSession;
import static com.portfolio.TestRequests.remoteAddr;
import static com.portfolio.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

@IntegrationTest
class TwoFactorIT
{
    private static final String BASE = "/api/admin/security/2fa";
    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void disableTwoFactor()
    {
        jdbc.update("""
            UPDATE admin_user SET totp_secret = NULL, totp_pending_secret = NULL, totp_enabled = false,
                totp_last_step = 0
            """);
        jdbc.update("DELETE FROM admin_recovery_code");
    }

    @Test
    void statusIsOffByDefaultAndNeedsASession() throws Exception
    {
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE).cookie(adminSession(mvc)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void enablingNeedsAValidCodeAndReturnsTenRecoveryCodes() throws Exception
    {
        Cookie session = adminSession(mvc);
        String secret = setup(session);

        mvc.perform(post(BASE + "/enable").with(xsrf()).cookie(session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"000000\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_CODE"));
        mvc.perform(post(BASE + "/enable").with(xsrf()).cookie(session).contentType(MediaType.APPLICATION_JSON)
                .content(code(secret, 0)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recoveryCodes.length()").value(RecoveryCodes.COUNT));
        mvc.perform(get(BASE).cookie(session))
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(jsonPath("$.recoveryCodesLeft").value(RecoveryCodes.COUNT));

        String stored = jdbc.queryForObject("SELECT totp_secret FROM admin_user", String.class);
        assertThat(stored).isNotNull().doesNotContain(secret);
    }

    @Test
    void passwordAloneNoLongerOpensASession() throws Exception
    {
        String secret = enable();

        Cookie mfa = mvc.perform(login())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mfaRequired").value(true))
            .andExpect(jsonPath("$.email").doesNotExist())
            .andExpect(cookie().doesNotExist(SESSION_COOKIE))
            .andExpect(cookie().httpOnly(SessionCookies.MFA_COOKIE, true))
            .andReturn().getResponse().getCookie(SessionCookies.MFA_COOKIE);
        assertThat(mfa).isNotNull();

        // The step token is not a session.
        mvc.perform(get("/api/auth/me").cookie(new Cookie(SESSION_COOKIE, mfa.getValue())))
            .andExpect(status().isUnauthorized());

        mvc.perform(verify(mfa, "{\"code\":\"123456\"}")).andExpect(status().isBadRequest());
        mvc.perform(verify(mfa, code(secret, 1)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(ADMIN_EMAIL))
            .andExpect(cookie().exists(SESSION_COOKIE))
            .andExpect(cookie().maxAge(SessionCookies.MFA_COOKIE, 0));
    }

    @Test
    void aCodeCannotBeUsedTwice() throws Exception
    {
        String secret = enable();
        Cookie mfa = mfaCookie();

        mvc.perform(verify(mfa, code(secret, 1))).andExpect(status().isOk());
        mvc.perform(verify(mfaCookie(), code(secret, 1))).andExpect(status().isBadRequest());
    }

    @Test
    void recoveryCodesWorkOnce() throws Exception
    {
        Cookie session = adminSession(mvc);
        String secret = setup(session);
        JsonNode codes = json(mvc.perform(post(BASE + "/enable").with(xsrf()).cookie(session)
                .contentType(MediaType.APPLICATION_JSON).content(code(secret, 0)))
            .andReturn()).path("recoveryCodes");
        String recovery = codes.get(0).asText();

        mvc.perform(verify(mfaCookie(), "{\"code\":\"" + recovery.toUpperCase() + "\"}"))
            .andExpect(status().isOk());
        mvc.perform(verify(mfaCookie(), "{\"code\":\"" + recovery + "\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void verifyWithoutTheFirstStepIsRefused() throws Exception
    {
        mvc.perform(post("/api/auth/2fa/verify").with(xsrf()).with(remoteAddr(nextIp()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void disablingNeedsAValidCode() throws Exception
    {
        Cookie setupSession = adminSession(mvc);
        String secret = setup(setupSession);
        String recovery = json(mvc.perform(post(BASE + "/enable").with(xsrf()).cookie(setupSession)
                .contentType(MediaType.APPLICATION_JSON).content(code(secret, 0)))
            .andReturn()).path("recoveryCodes").get(0).asText();
        Cookie session = sessionWith(secret);

        mvc.perform(post(BASE + "/disable").with(xsrf()).cookie(session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"000000\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post(BASE + "/disable").with(xsrf()).cookie(session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + recovery + "\"}"))
            .andExpect(status().isNoContent());
        mvc.perform(login()).andExpect(jsonPath("$.mfaRequired").value(false))
            .andExpect(cookie().exists(SESSION_COOKIE));
    }

    // ---------------------------------------------------------------- helpers

    private String enable() throws Exception
    {
        Cookie session = adminSession(mvc);
        String secret = setup(session);
        mvc.perform(post(BASE + "/enable").with(xsrf()).cookie(session).contentType(MediaType.APPLICATION_JSON)
                .content(code(secret, 0)))
            .andExpect(status().isOk());
        return secret;
    }

    private Cookie sessionWith(String secret) throws Exception
    {
        return mvc.perform(verify(mfaCookie(), code(secret, 1))).andExpect(status().isOk())
            .andReturn().getResponse().getCookie(SESSION_COOKIE);
    }

    private String setup(Cookie session) throws Exception
    {
        JsonNode setup = json(mvc.perform(post(BASE + "/setup").with(xsrf()).cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.otpauthUri").value(startsWith("otpauth://totp/")))
            .andReturn());
        return setup.path("secret").asText();
    }

    private Cookie mfaCookie() throws Exception
    {
        return mvc.perform(login()).andExpect(status().isOk()).andReturn().getResponse()
            .getCookie(SessionCookies.MFA_COOKIE);
    }

    private RequestBuilder login()
    {
        return post("/api/auth/login").with(xsrf()).with(remoteAddr(nextIp()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + ADMIN_EMAIL + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}");
    }

    private RequestBuilder verify(Cookie mfa, String body)
    {
        return post("/api/auth/2fa/verify").with(xsrf()).with(remoteAddr(nextIp())).cookie(mfa)
            .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    /** The code for the current step plus {@code offset} (one step of drift is accepted). */
    private static String code(String secret, int offset)
    {
        long step = Instant.now().getEpochSecond() / Totp.STEP_SECONDS + offset;
        return "{\"code\":\"" + Totp.code(Totp.decodeBase32(secret), step) + "\"}";
    }

    private JsonNode json(MvcResult result) throws Exception
    {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String nextIp()
    {
        return "198.19.1." + (NEXT_IP.getAndIncrement() % 250 + 1);
    }
}
