package com.portfolio.page.internal;

import static com.portfolio.TestRequests.adminSession;
import static com.portfolio.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.IntegrationTest;
import com.portfolio.TestData;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Book structure: Cover, Table of contents, projects with their sheets, Contact; the table of contents
 * gives each project the number of its first published page and follows every change.
 */
@IntegrationTest
class BookStructureIT
{
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private FixedPagesInitializer initializer;

    private Cookie session;

    @BeforeEach
    void setUp() throws Exception
    {
        TestData.reset(jdbc);
        TestData.insertFixedPages(jdbc);
        session = adminSession(mvc);
    }

    @Test
    void pageNumbersInTheTableOfContentsFollowTheBook() throws Exception
    {
        JsonNode alpha = createProject("Alpha");
        JsonNode beta = createProject("Beta");
        publishSite();

        mvc.perform(get("/api/public/book"))
            .andExpect(jsonPath("$.pages", hasSize(5)))
            .andExpect(jsonPath("$.pages[0].slug").value("cover"))
            .andExpect(jsonPath("$.pages[1].slug").value("table-of-contents"))
            .andExpect(jsonPath("$.pages[2].slug").value("alpha"))
            .andExpect(jsonPath("$.pages[3].slug").value("beta"))
            .andExpect(jsonPath("$.pages[4].slug").value("contact"))
            .andExpect(jsonPath("$.toc", hasSize(2)))
            .andExpect(jsonPath("$.toc[0].title").value("Alpha"))
            .andExpect(jsonPath("$.toc[0].page").value(3))
            .andExpect(jsonPath("$.toc[1].page").value(4));

        // A second sheet in Alpha: the editor sees the new numbers at once, readers after publishing.
        JsonNode alpha2 = addSheet(alpha);
        mvc.perform(get("/api/admin/book").cookie(session)).andExpect(jsonPath("$.toc[1].page").value(5));
        mvc.perform(get("/api/public/book")).andExpect(jsonPath("$.toc[1].page").value(4));
        publishSite();
        mvc.perform(get("/api/public/book"))
            .andExpect(jsonPath("$.pages[3].id").value(alpha2.get("id").asText()))
            .andExpect(jsonPath("$.toc[1].page").value(5));

        // Removing it brings Beta back.
        mvc.perform(delete("/api/admin/pages/" + alpha2.get("id").asText()).cookie(session).with(xsrf()))
            .andExpect(status().isNoContent());
        publishSite();
        mvc.perform(get("/api/public/book")).andExpect(jsonPath("$.toc[1].page").value(4));

        // Moving Beta before Alpha swaps the numbers.
        mvc.perform(put("/api/admin/projects/order").cookie(session).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"" + beta.get("projectId").asText() + "\",\"" + alpha.get("projectId").asText()
                    + "\"]}"))
            .andExpect(status().isNoContent());
        publishSite();
        mvc.perform(get("/api/public/book"))
            .andExpect(jsonPath("$.toc[0].title").value("Beta"))
            .andExpect(jsonPath("$.toc[0].page").value(3))
            .andExpect(jsonPath("$.toc[1].page").value(4));
    }

    @Test
    void publicationStatusTellsWhetherReadersAreUpToDate() throws Exception
    {
        mvc.perform(get("/api/admin/book").cookie(session))
            .andExpect(jsonPath("$.publication.upToDate").value(false))
            .andExpect(jsonPath("$.publication.publishedAt").doesNotExist());
        publishSite();
        mvc.perform(get("/api/admin/book").cookie(session)).andExpect(jsonPath("$.publication.upToDate").value(true));

        JsonNode project = createProject("Nouveau");
        mvc.perform(get("/api/admin/book").cookie(session)).andExpect(jsonPath("$.publication.upToDate").value(false));
        publishSite();
        mvc.perform(put("/api/admin/projects/" + project.get("projectId").asText()).cookie(session).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Renommé\"}"))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/book").cookie(session)).andExpect(jsonPath("$.publication.upToDate").value(false));
    }

    @Test
    void structureGroupsPagesAndGivesPageRanges() throws Exception
    {
        JsonNode project = createProject("Projet");
        addSheet(project);
        addSheet(project);
        mvc.perform(put("/api/admin/projects/" + project.get("projectId").asText()).cookie(session).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Projet renommé\"}"))
            .andExpect(status().isNoContent());

        mvc.perform(get("/api/admin/book").cookie(session))
            .andExpect(jsonPath("$.start", hasSize(2)))
            .andExpect(jsonPath("$.start[0].kind").value("COVER"))
            .andExpect(jsonPath("$.end[0].kind").value("CONTACT"))
            .andExpect(jsonPath("$.end[0].pageNumber").value(6))
            .andExpect(jsonPath("$.projects[0].title").value("Projet renommé"))
            .andExpect(jsonPath("$.projects[0].sheets", hasSize(3)))
            .andExpect(jsonPath("$.projects[0].sheets[1].title").value("Projet — 2"))
            .andExpect(jsonPath("$.projects[0].firstPage").value(3))
            .andExpect(jsonPath("$.projects[0].lastPage").value(5))
            .andExpect(jsonPath("$.toc[0].title").value("Projet renommé"));
    }

    @Test
    void sheetsCanBeReordered() throws Exception
    {
        JsonNode first = createProject("Ordre");
        JsonNode second = addSheet(first);
        String projectId = first.get("projectId").asText();

        mvc.perform(put("/api/admin/projects/" + projectId + "/sheets/order").cookie(session).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"" + second.get("id").asText() + "\",\"" + first.get("id").asText() + "\"]}"))
            .andExpect(status().isNoContent());

        mvc.perform(get("/api/admin/book").cookie(session))
            .andExpect(jsonPath("$.projects[0].sheets[0].id").value(second.get("id").asText()));
        mvc.perform(put("/api/admin/projects/" + projectId + "/sheets/order").cookie(session).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[\"" + first.get("id").asText() + "\"]}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deletingAProjectDeletesItsSheets() throws Exception
    {
        JsonNode project = createProject("Éphémère");
        addSheet(project);

        mvc.perform(delete("/api/admin/projects/" + project.get("projectId").asText()).cookie(session).with(xsrf()))
            .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM page WHERE kind = 'PROJECT_SHEET'", Integer.class))
            .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project", Integer.class)).isZero();
    }

    @Test
    void fixedPagesCannotBeDeletedOrDuplicated() throws Exception
    {
        String cover = jdbc.queryForObject("SELECT id::text FROM page WHERE kind = 'COVER'", String.class);

        mvc.perform(delete("/api/admin/pages/" + cover).cookie(session).with(xsrf()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("FIXED_PAGE"));
        mvc.perform(post("/api/admin/pages/" + cover + "/duplicate").cookie(session).with(xsrf()))
            .andExpect(status().isConflict());
    }

    @Test
    void missingFixedPagesAreRecreatedAtStartup() throws Exception
    {
        jdbc.update("DELETE FROM page WHERE kind IN ('TABLE_OF_CONTENTS', 'CONTACT')");

        initializer.run(new DefaultApplicationArguments());

        assertThat(jdbc.queryForList("SELECT kind FROM page", String.class))
            .containsExactlyInAnyOrder("COVER", "TABLE_OF_CONTENTS", "CONTACT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM page WHERE kind = 'TABLE_OF_CONTENTS'"
            + " AND jsonb_path_exists(content, '$.elements[*] ? (@.type == \"toc\")')", Integer.class)).isEqualTo(1);
    }

    // ---------------------------------------------------------------- helpers

    private JsonNode createProject(String title) throws Exception
    {
        return json.readTree(mvc.perform(post("/api/admin/projects").cookie(session).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"" + title + "\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString());
    }

    private JsonNode addSheet(JsonNode sheet) throws Exception
    {
        return json.readTree(mvc.perform(post("/api/admin/projects/" + sheet.get("projectId").asText() + "/sheets")
                .cookie(session).with(xsrf()))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString());
    }

    private void publishSite() throws Exception
    {
        mvc.perform(post("/api/admin/book/publish").cookie(session).with(xsrf())).andExpect(status().isOk());
    }
}
