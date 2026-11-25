package com.portfolio.settings.internal;

import static com.portfolio.TestRequests.adminSession;
import static com.portfolio.TestRequests.xsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portfolio.IntegrationTest;
import com.portfolio.TestData;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class SettingsIT
{
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private Cookie session;

    @BeforeEach
    void setUp() throws Exception
    {
        TestData.reset(jdbc);
        session = adminSession(mvc);
    }

    @Test
    void readsAndUpdatesSettings() throws Exception
    {
        mvc.perform(get("/api/admin/settings").cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ipMode").value("MASKED"));

        mvc.perform(put("/api/admin/settings").cookie(session).with(xsrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body("#222222", null, "HASHED")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Mon livre"))
            .andExpect(jsonPath("$.ipMode").value("HASHED"));

        mvc.perform(get("/api/public/book"))
            .andExpect(jsonPath("$.settings.title").value("Mon livre"))
            .andExpect(jsonPath("$.settings.privacyText").value("Texte"))
            .andExpect(jsonPath("$.settings.backgroundColor").value("#222222"));
    }

    @Test
    void rejectsInvalidValues() throws Exception
    {
        mvc.perform(put("/api/admin/settings").cookie(session).with(xsrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body("red", null, "MASKED")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.backgroundColor").exists());
        mvc.perform(put("/api/admin/settings").cookie(session).with(xsrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body("#222222", UUID.randomUUID().toString(), "MASKED")))
            .andExpect(status().isBadRequest());
    }

    private static String body(String color, String backgroundMediaId, String ipMode)
    {
        return "{\"title\":\"Mon livre\",\"pageWidth\":800,\"pageHeight\":1000,\"backgroundColor\":\"" + color
            + "\",\"backgroundMediaId\":" + (backgroundMediaId == null ? "null" : "\"" + backgroundMediaId + "\"")
            + ",\"ipMode\":\"" + ipMode + "\",\"privacyText\":\"Texte\"}";
    }
}
