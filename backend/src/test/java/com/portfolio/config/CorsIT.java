package com.portfolio.config;

import static com.portfolio.TestRequests.xsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portfolio.IntegrationTest;
import com.portfolio.TestData;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Behind Traefik the browser says "Origin: https://..." while the backend receives plain HTTP: every public origin
 * listed in APP_ORIGIN must be accepted, any other origin refused.
 */
@IntegrationTest
class CorsIT
{
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp()
    {
        TestData.reset(jdbc);
        TestData.insertPublishedPage(jdbc, "accueil", 0);
    }

    @Test
    void acceptsEachConfiguredOriginBehindTheTlsProxy() throws Exception
    {
        mvc.perform(visit("https://portfolio-public")).andExpect(status().isNoContent());
        mvc.perform(visit("https://portfolio-admin")).andExpect(status().isNoContent());
    }

    @Test
    void refusesOtherOrigins() throws Exception
    {
        mvc.perform(visit("https://evil.example")).andExpect(status().isForbidden());
    }

    private static MockHttpServletRequestBuilder visit(String origin)
    {
        return post("http://portfolio-public/api/public/visits")
            .header("Origin", origin)
            .with(xsrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"pageSlug\":\"accueil\",\"sessionId\":\"" + UUID.randomUUID() + "\"}");
    }
}
