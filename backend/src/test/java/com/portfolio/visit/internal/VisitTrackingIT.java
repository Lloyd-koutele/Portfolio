package com.portfolio.visit.internal;

import static com.portfolio.TestRequests.adminSession;
import static com.portfolio.TestRequests.remoteAddr;
import static com.portfolio.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portfolio.IntegrationTest;
import com.portfolio.TestData;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@IntegrationTest
class VisitTrackingIT
{
    private static final String CHROME = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        + "(KHTML, like Gecko) Chrome/130.0 Safari/537.36";
    private static final String IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 "
        + "(KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";
    private static final String IPAD = "Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) AppleWebKit/605.1.15 "
        + "(KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";
    private static final String GOOGLEBOT = "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private VisitPurgeJob purgeJob;

    @BeforeEach
    void setUp()
    {
        TestData.reset(jdbc);
        TestData.insertPublishedPage(jdbc, "accueil", 0);
    }

    @Test
    void untrustedForwardedForIsIgnored() throws Exception
    {
        mvc.perform(visit("accueil", UUID.randomUUID(), "198.51.100.9").header("X-Forwarded-For", "1.2.3.4"))
            .andExpect(status().isNoContent());

        assertThat(lastVisit().get("ip_display")).isEqualTo("198.51.100.xxx");
    }

    @Test
    void forwardedForFromTheTrustedProxyIsUsed() throws Exception
    {
        mvc.perform(visit("accueil", UUID.randomUUID(), "10.0.0.1").header("X-Forwarded-For", "41.82.14.25"))
            .andExpect(status().isNoContent());

        assertThat(lastVisit().get("ip_display")).isEqualTo("41.82.14.xxx");
    }

    @Test
    void maskedModeTruncatesIpv6To48Bits() throws Exception
    {
        mvc.perform(visit("accueil", UUID.randomUUID(), "2001:db8:abcd:12:1:2:3:4"));

        assertThat(lastVisit().get("ip_display")).isEqualTo("2001:db8:abcd::/48");
    }

    @Test
    void fullModeStoresTheCompleteAddress() throws Exception
    {
        jdbc.update("UPDATE site_settings SET ip_mode = 'FULL'");

        mvc.perform(visit("accueil", UUID.randomUUID(), "41.82.14.25"));

        assertThat(lastVisit().get("ip_display")).isEqualTo("41.82.14.25");
    }

    @Test
    void hashedModeStoresOnlyAPseudonym() throws Exception
    {
        jdbc.update("UPDATE site_settings SET ip_mode = 'HASHED'");

        mvc.perform(visit("accueil", UUID.randomUUID(), "41.82.14.25"));

        Map<String, Object> row = lastVisit();
        String hash = (String) row.get("ip_hash");
        assertThat(row.get("ip_display")).isEqualTo("visiteur-" + hash.substring(0, 6));
        assertThat(hash).hasSize(64).doesNotContain("41.82");
    }

    @Test
    void sameIpGivesSameHashWhateverTheMode() throws Exception
    {
        mvc.perform(visit("accueil", UUID.randomUUID(), "41.82.14.25"));
        jdbc.update("UPDATE site_settings SET ip_mode = 'HASHED'");
        mvc.perform(visit("accueil", UUID.randomUUID(), "41.82.14.25"));

        assertThat(jdbc.queryForObject("SELECT count(DISTINCT ip_hash) FROM visit", Integer.class)).isEqualTo(1);
    }

    @Test
    void sameSessionAndPageWithin30MinutesIsCountedOnce() throws Exception
    {
        UUID session = UUID.randomUUID();
        mvc.perform(visit("accueil", session, "41.82.14.25"));
        mvc.perform(visit("accueil", session, "41.82.14.25"));
        mvc.perform(visit("accueil", UUID.randomUUID(), "41.82.14.25"));

        assertThat(count()).isEqualTo(2);

        jdbc.update("UPDATE visit SET occurred_at = now() - interval '31 minutes' WHERE session_id = ?", session);
        mvc.perform(visit("accueil", session, "41.82.14.25"));
        assertThat(count()).isEqualTo(3);
    }

    @Test
    void administratorVisitsAreNotRecorded() throws Exception
    {
        Cookie admin = adminSession(mvc);

        mvc.perform(visit("accueil", UUID.randomUUID(), "41.82.14.25").cookie(admin))
            .andExpect(status().isNoContent());

        assertThat(count()).isZero();
    }

    @Test
    void unknownOrUnpublishedPagesAreIgnored() throws Exception
    {
        mvc.perform(visit("inconnue", UUID.randomUUID(), "41.82.14.25")).andExpect(status().isNoContent());
        jdbc.update("DELETE FROM book_edition");
        mvc.perform(visit("accueil", UUID.randomUUID(), "41.82.14.25")).andExpect(status().isNoContent());

        assertThat(count()).isZero();
    }

    @Test
    void aPageStillOnlineButDeletedFromTheWorkingCopyIsCountedWithoutLink() throws Exception
    {
        jdbc.update("DELETE FROM page");

        mvc.perform(visit("accueil", UUID.randomUUID(), "41.82.14.25")).andExpect(status().isNoContent());

        assertThat(lastVisit().get("page_id")).isNull();
        assertThat(lastVisit().get("page_slug")).isEqualTo("accueil");
    }

    @Test
    void devicesAndRobotsAreDetected() throws Exception
    {
        mvc.perform(visit("accueil", UUID.randomUUID(), "1.1.1.0"));
        mvc.perform(visit("accueil", UUID.randomUUID(), "1.1.1.1", IPHONE));
        mvc.perform(visit("accueil", UUID.randomUUID(), "1.1.1.2", IPAD));
        mvc.perform(visit("accueil", UUID.randomUUID(), "1.1.1.3", GOOGLEBOT));

        assertThat(jdbc.queryForList("SELECT device_type FROM visit ORDER BY id", String.class))
            .containsExactly("DESKTOP", "MOBILE", "TABLET", "BOT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM visit WHERE is_bot", Integer.class)).isEqualTo(1);
    }

    @Test
    void refererKeepsNoQueryString() throws Exception
    {
        mvc.perform(post("/api/public/visits").with(xsrf()).with(remoteAddr("1.1.1.1"))
            .header(HttpHeaders.USER_AGENT, CHROME)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"pageSlug\":\"accueil\",\"sessionId\":\"" + UUID.randomUUID()
                + "\",\"referer\":\"https://www.Google.com/search?q=nom+prenom\"}"));

        assertThat(lastVisit().get("referer")).isEqualTo("https://www.google.com/search");
    }

    @Test
    void invalidBodyIsRejected() throws Exception
    {
        mvc.perform(post("/api/public/visits").with(xsrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"pageSlug\":\"\",\"sessionId\":\"not-a-uuid\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void visitsOlderThan13MonthsArePurged() throws Exception
    {
        mvc.perform(visit("accueil", UUID.randomUUID(), "1.1.1.1"));
        mvc.perform(visit("accueil", UUID.randomUUID(), "1.1.1.2"));
        jdbc.update("UPDATE visit SET occurred_at = now() - interval '14 months' WHERE ip_display = '1.1.1.xxx'"
            + " AND id = (SELECT min(id) FROM visit)");

        assertThat(purgeJob.purge()).isEqualTo(1);
        assertThat(count()).isEqualTo(1);
    }

    private MockHttpServletRequestBuilder visit(String slug, UUID session, String ip)
    {
        return visit(slug, session, ip, CHROME);
    }

    private MockHttpServletRequestBuilder visit(String slug, UUID session, String ip, String userAgent)
    {
        return post("/api/public/visits")
            .with(xsrf())
            .with(remoteAddr(ip))
            .header(HttpHeaders.USER_AGENT, userAgent)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"pageSlug\":\"" + slug + "\",\"sessionId\":\"" + session + "\"}");
    }

    private int count()
    {
        Integer value = jdbc.queryForObject("SELECT count(*) FROM visit", Integer.class);
        return value == null ? 0 : value;
    }

    private Map<String, Object> lastVisit()
    {
        return jdbc.queryForMap("SELECT * FROM visit ORDER BY id DESC LIMIT 1");
    }
}
