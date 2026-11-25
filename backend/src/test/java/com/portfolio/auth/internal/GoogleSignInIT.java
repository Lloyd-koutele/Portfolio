package com.portfolio.auth.internal;

import static com.portfolio.TestRequests.ADMIN_EMAIL;
import static com.portfolio.TestRequests.SESSION_COOKIE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portfolio.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class GoogleSignInIT
{
    private static final String ADMIN_URL = "https://portfolio-admin";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FakeGoogleIdentityClient google;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void reset()
    {
        google.willFail();
        jdbc.update("UPDATE admin_user SET totp_secret = NULL, totp_enabled = false, totp_last_step = 0");
    }

    @Test
    void providersTellTheLoginPageWhetherGoogleIsConfigured() throws Exception
    {
        mvc.perform(get("/api/auth/providers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.google").value(true));
    }

    @Test
    void startSendsTheBrowserToGoogleWithPkceAndABindingCookie() throws Exception
    {
        MockHttpServletResponse response = mvc.perform(get("/api/auth/google"))
            .andExpect(status().isFound())
            .andExpect(cookie().httpOnly(SessionCookies.GOOGLE_COOKIE, true))
            .andExpect(cookie().sameSite(SessionCookies.GOOGLE_COOKIE, "Lax"))
            .andExpect(cookie().path(SessionCookies.GOOGLE_COOKIE, SessionCookies.GOOGLE_PATH))
            .andReturn().getResponse();

        URI location = URI.create(response.getHeader(HttpHeaders.LOCATION));
        Map<String, String> query = query(location);
        assertThat(location.getHost()).isEqualTo("accounts.google.com");
        assertThat(query).containsEntry("client_id", "test-client-id.apps.googleusercontent.com")
            .containsEntry("redirect_uri", "https://localhost/api/auth/google/callback")
            .containsEntry("code_challenge_method", "S256")
            .containsKeys("state", "nonce", "code_challenge");
    }

    @Test
    void theAdministratorsGoogleAccountOpensASession() throws Exception
    {
        google.willReturn(ADMIN_EMAIL.toUpperCase(), true);
        Flow flow = start(true);

        String ticketUrl = callback(flow.state());
        assertThat(ticketUrl).startsWith(ADMIN_URL + "/api/auth/google/complete?ticket=");
        assertThat(google.lastCode()).isEqualTo("the-code");

        mvc.perform(get(URI.create(ticketUrl).getPath() + "?" + URI.create(ticketUrl).getRawQuery())
                .cookie(flow.binding()))
            .andExpect(status().isFound())
            .andExpect(header().string(HttpHeaders.LOCATION, "/admin/pages"))
            .andExpect(cookie().exists(SESSION_COOKIE))
            .andExpect(cookie().maxAge(SESSION_COOKIE, 30 * 24 * 3600))
            .andExpect(cookie().maxAge(SessionCookies.GOOGLE_COOKIE, 0));
    }

    @Test
    void anotherBrowserCannotRedeemTheTicket() throws Exception
    {
        google.willReturn(ADMIN_EMAIL, true);
        Flow flow = start(false);
        String ticketUrl = callback(flow.state());
        String path = URI.create(ticketUrl).getPath() + "?" + URI.create(ticketUrl).getRawQuery();

        mvc.perform(get(path).cookie(new Cookie(SessionCookies.GOOGLE_COOKIE, "someone-else")))
            .andExpect(header().string(HttpHeaders.LOCATION, "/admin/login?error=google_failed"))
            .andExpect(cookie().doesNotExist(SESSION_COOKIE));
        // The ticket is single-use: even the right browser cannot use it now.
        mvc.perform(get(path).cookie(flow.binding()))
            .andExpect(header().string(HttpHeaders.LOCATION, "/admin/login?error=google_failed"));
    }

    @Test
    void unknownOrUnverifiedAddressesAreRefused() throws Exception
    {
        google.willReturn("stranger@gmail.com", true);
        Flow stranger = start(false);
        String ticketUrl = callback(stranger.state());
        mvc.perform(get(URI.create(ticketUrl).getPath() + "?" + URI.create(ticketUrl).getRawQuery())
                .cookie(stranger.binding()))
            .andExpect(header().string(HttpHeaders.LOCATION, "/admin/login?error=google_account"))
            .andExpect(cookie().doesNotExist(SESSION_COOKIE));

        google.willReturn(ADMIN_EMAIL, false);
        assertThat(callback(start(false).state())).isEqualTo(ADMIN_URL + "/admin/login?error=google_failed");
    }

    @Test
    void aStateIsUsedOnlyOnce() throws Exception
    {
        google.willReturn(ADMIN_EMAIL, true);
        Flow flow = start(false);

        assertThat(callback(flow.state())).contains("ticket=");
        assertThat(callback(flow.state())).isEqualTo(ADMIN_URL + "/admin/login?error=google_failed");
        assertThat(callback("forged-state")).isEqualTo(ADMIN_URL + "/admin/login?error=google_failed");
    }

    @Test
    void withTwoFactorOnGoogleLeadsToTheCodeStep() throws Exception
    {
        jdbc.update("UPDATE admin_user SET totp_secret = 'x', totp_enabled = true");
        google.willReturn(ADMIN_EMAIL, true);
        Flow flow = start(false);
        String ticketUrl = callback(flow.state());

        mvc.perform(get(URI.create(ticketUrl).getPath() + "?" + URI.create(ticketUrl).getRawQuery())
                .cookie(flow.binding()))
            .andExpect(header().string(HttpHeaders.LOCATION, "/admin/login?step=2fa"))
            .andExpect(cookie().exists(SessionCookies.MFA_COOKIE))
            .andExpect(cookie().doesNotExist(SESSION_COOKIE));
    }

    private Flow start(boolean remember) throws Exception
    {
        MockHttpServletResponse response = mvc.perform(get("/api/auth/google").param("remember",
                String.valueOf(remember)))
            .andReturn().getResponse();
        String state = query(URI.create(response.getHeader(HttpHeaders.LOCATION))).get("state");
        return new Flow(state, response.getCookie(SessionCookies.GOOGLE_COOKIE));
    }

    private String callback(String state) throws Exception
    {
        return mvc.perform(get("/api/auth/google/callback").param("state", state).param("code", "the-code"))
            .andExpect(status().isFound())
            .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
    }

    private static Map<String, String> query(URI uri)
    {
        return Arrays.stream(uri.getRawQuery().split("&"))
            .map(pair -> pair.split("=", 2))
            .collect(Collectors.toMap(pair -> pair[0],
                pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
    }

    private record Flow(String state, Cookie binding)
    {
    }
}
