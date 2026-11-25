package com.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class ApplicationStartupIT
{
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp()
    {
        TestData.reset(jdbc);
    }

    @Test
    void healthEndpointReportsUp() throws Exception
    {
        mvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void otherActuatorEndpointsAreNotReachable() throws Exception
    {
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    @Test
    void flywayCreatesTheTwelveTables()
    {
        List<String> tables = jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'"
                + " AND table_name <> 'flyway_schema_history'",
            String.class);

        assertThat(tables).containsExactlyInAnyOrder("admin_user", "site_settings", "page", "project", "media",
            "visit", "book_edition", "admin_recovery_code", "profile", "cv_document", "download",
            "theme_snapshot");
    }

    @Test
    void siteSettingsHasItsSingleDefaultRow()
    {
        Integer width = jdbc.queryForObject("SELECT page_width FROM site_settings WHERE id = 1", Integer.class);
        String ipMode = jdbc.queryForObject("SELECT ip_mode FROM site_settings WHERE id = 1", String.class);

        assertThat(width).isEqualTo(800);
        assertThat(ipMode).isEqualTo("MASKED");
    }
}
