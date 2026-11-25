package com.portfolio.stats.internal;

import static com.portfolio.TestRequests.adminSession;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portfolio.IntegrationTest;
import com.portfolio.TestData;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Known data set: 6 human visits from 3 IPs on 2 pages over the last 3 days, plus one robot visit and two visits
 * in the previous period.
 */
@IntegrationTest
class StatsIT
{
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private Cookie session;
    private String home;
    private String projects;
    private LocalDate today;

    @BeforeEach
    void setUp() throws Exception
    {
        TestData.reset(jdbc);
        session = adminSession(mvc);
        home = TestData.insertPublishedPage(jdbc, "accueil", 0);
        projects = TestData.insertPublishedPage(jdbc, "projets", 1);
        today = LocalDate.now(ZoneOffset.UTC);
        insert(0, "a", home, "DESKTOP", false);
        insert(0, "a", projects, "DESKTOP", false);
        insert(0, "b", home, "MOBILE", false);
        insert(1, "b", home, "MOBILE", false);
        insert(2, "c", home, "TABLET", false);
        insert(2, "c", projects, "TABLET", false);
        insert(0, "robot", home, "BOT", true);
        insert(40, "a", home, "DESKTOP", false);
        insert(45, "d", home, "DESKTOP", false);
    }

    @Test
    void summaryCountsHumansOnly() throws Exception
    {
        mvc.perform(get("/api/admin/stats/summary").cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalVisits").value(6))
            .andExpect(jsonPath("$.uniqueVisitors").value(3))
            .andExpect(jsonPath("$.visitsToday").value(3))
            .andExpect(jsonPath("$.previous.visits").value(2))
            .andExpect(jsonPath("$.previous.uniqueVisitors").value(2))
            .andExpect(jsonPath("$.visitsChange").value(200.0))
            .andExpect(jsonPath("$.devices.DESKTOP").value(2))
            .andExpect(jsonPath("$.devices.MOBILE").value(2))
            .andExpect(jsonPath("$.devices.TABLET").value(2));
    }

    @Test
    void timeseriesHasOnePointPerDay() throws Exception
    {
        String from = today.minusDays(6).toString();
        mvc.perform(get("/api/admin/stats/timeseries").param("from", from).param("to", today.toString())
                .cookie(session))
            .andExpect(jsonPath("$", hasSize(7)))
            .andExpect(jsonPath("$[6].date").value(today.toString()))
            .andExpect(jsonPath("$[6].visits").value(3))
            .andExpect(jsonPath("$[6].uniqueVisitors").value(2))
            .andExpect(jsonPath("$[5].visits").value(1))
            .andExpect(jsonPath("$[0].visits").value(0));
    }

    @Test
    void pagesAreSortedByVisits() throws Exception
    {
        mvc.perform(get("/api/admin/stats/pages").cookie(session))
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].slug").value("accueil"))
            .andExpect(jsonPath("$[0].title").value("Titre accueil"))
            .andExpect(jsonPath("$[0].visits").value(4))
            .andExpect(jsonPath("$[1].visits").value(2));
    }

    @Test
    void visitListIsPagedAndFiltered() throws Exception
    {
        mvc.perform(get("/api/admin/stats/visits").param("size", "4").cookie(session))
            .andExpect(jsonPath("$.content", hasSize(4)))
            .andExpect(jsonPath("$.page.totalElements").value(6))
            .andExpect(jsonPath("$.page.totalPages").value(2));
        mvc.perform(get("/api/admin/stats/visits").param("includeBots", "true").cookie(session))
            .andExpect(jsonPath("$.page.totalElements").value(7));
        mvc.perform(get("/api/admin/stats/visits").param("device", "MOBILE").cookie(session))
            .andExpect(jsonPath("$.page.totalElements").value(2));
        mvc.perform(get("/api/admin/stats/visits").param("pageId", projects).cookie(session))
            .andExpect(jsonPath("$.page.totalElements").value(2))
            .andExpect(jsonPath("$.content[0].pageTitle").value("Titre projets"));
        mvc.perform(get("/api/admin/stats/visits").param("ip", "ip-b").cookie(session))
            .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void csvExportMatchesTheListAndNeutralisesFormulas() throws Exception
    {
        jdbc.update("UPDATE visit SET referer = '=HYPERLINK(\"x\")' WHERE ip_display = 'ip-c'");

        byte[] csv = mvc.perform(get("/api/admin/stats/visits.csv").cookie(session))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsByteArray();

        String text = new String(csv, StandardCharsets.UTF_8);
        assertThat(text.lines().count()).isEqualTo(7);
        assertThat(text).contains("date;ip;page;appareil;robot;provenance").contains("'=HYPERLINK");
    }

    @Test
    void invalidPeriodIsRejected() throws Exception
    {
        mvc.perform(get("/api/admin/stats/summary").param("from", "2026-02-01").param("to", "2026-01-01")
                .cookie(session))
            .andExpect(status().isBadRequest());
    }

    @Test
    void statisticsRequireAdmin() throws Exception
    {
        mvc.perform(get("/api/admin/stats/summary")).andExpect(status().isUnauthorized());
    }

    private void insert(int daysAgo, String ip, String pageId, String device, boolean bot)
    {
        jdbc.update("""
            INSERT INTO visit (occurred_at, ip_display, ip_hash, device_type, is_bot, page_id, page_slug, session_id)
            VALUES (?::timestamptz, ?, ?, ?, ?, ?::uuid, (SELECT slug FROM page WHERE id = ?::uuid), ?)
            """, today.minusDays(daysAgo).atTime(12, 0).atOffset(ZoneOffset.UTC).toString(), "ip-" + ip,
            String.format("%64s", ip).replace(' ', '0'), device, bot, pageId, pageId, UUID.randomUUID());
    }
}
