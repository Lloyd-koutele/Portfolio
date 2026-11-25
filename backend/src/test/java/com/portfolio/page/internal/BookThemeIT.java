package com.portfolio.page.internal;

import static com.portfolio.TestRequests.adminSession;
import static com.portfolio.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.portfolio.IntegrationTest;
import com.portfolio.TestData;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class BookThemeIT
{
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    private Cookie session;

    @BeforeEach
    void setUp() throws Exception
    {
        TestData.reset(jdbc);
        session = adminSession(mvc);
        createProject("Alpha");
        createProject("Beta");
    }

    @Test
    void appliesToEveryPageThenGoesBackExactly() throws Exception
    {
        String oldThumbnail = upload("THUMBNAIL");
        String firstId = pages().get(0).get("id").asText();
        jdbc.update("UPDATE page SET thumbnail_media_id = ?::uuid WHERE id = ?::uuid", oldThumbnail, firstId);
        String newThumbnail = upload("THUMBNAIL");

        ObjectNode body = themed("#123456", "#0a0a0a");
        ((ObjectNode) body.get("pages").get(0)).put("thumbnailMediaId", newThumbnail);
        JsonNode history = read(apply(body).andExpect(status().isOk()));

        assertThat(history.get("available").asBoolean()).isTrue();
        assertThat(history.get("pages").asInt()).isEqualTo(2);
        assertThat(history.get("themeId").asText()).isEqualTo("nuit");
        assertThat(backgrounds()).containsOnly("#123456");
        assertThat(siteColor()).isEqualTo("#0a0a0a");
        // The replaced thumbnail stays as long as the theme can be taken back.
        assertThat(mediaExists(oldThumbnail)).isTrue();

        mvc.perform(post("/api/admin/book/theme/" + history.get("id").asLong() + "/revert").cookie(session)
                .with(xsrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false));

        assertThat(backgrounds()).containsOnly("#ffffff");
        assertThat(siteColor()).isEqualTo("#1f1a17");
        assertThat(jdbc.queryForObject("SELECT thumbnail_media_id::text FROM page WHERE id = ?::uuid", String.class,
            firstId)).isEqualTo(oldThumbnail);
        assertThat(mediaExists(newThumbnail)).isFalse();
    }

    @Test
    void aPageChangedMeanwhileRefusesTheWholeTheme() throws Exception
    {
        ObjectNode body = themed("#123456", "#0a0a0a");
        jdbc.update("UPDATE page SET version = version + 1 WHERE id = ?::uuid",
            body.get("pages").get(1).get("id").asText());

        apply(body).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BOOK_CHANGED"));

        assertThat(backgrounds()).containsOnly("#ffffff");
        assertThat(siteColor()).isEqualTo("#1f1a17");
    }

    @Test
    void aMissingPageRefusesTheWholeTheme() throws Exception
    {
        ObjectNode body = themed("#123456", "#0a0a0a");
        ((ArrayNode) body.get("pages")).remove(1);

        apply(body).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BOOK_CHANGED"));
        assertThat(backgrounds()).containsOnly("#ffffff");
    }

    @Test
    void invalidContentRefusesTheWholeTheme() throws Exception
    {
        ObjectNode body = themed("#123456", "#0a0a0a");
        ((ObjectNode) body.get("pages").get(1).get("content").get("background")).put("value", "rouge");

        apply(body).andExpect(status().isBadRequest());
        assertThat(backgrounds()).containsOnly("#ffffff");
    }

    @Test
    void listsThePagesEditedSinceTheTheme() throws Exception
    {
        apply(themed("#123456", "#0a0a0a")).andExpect(status().isOk());
        JsonNode page = mvc.perform(get("/api/admin/pages/" + pages().get(0).get("id").asText()).cookie(session))
            .andReturn().getResponse().getContentAsString().transform(this::tree);
        ObjectNode update = json.createObjectNode();
        update.put("title", page.get("title").asText());
        update.put("slug", page.get("slug").asText());
        update.set("content", page.get("content"));
        update.put("version", page.get("version").asInt());
        mvc.perform(put("/api/admin/pages/" + page.get("id").asText()).cookie(session).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content(update.toString()))
            .andExpect(status().isOk());

        mvc.perform(get("/api/admin/book/theme").cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.modifiedSince[0]").value(page.get("title").asText()));
    }

    @Test
    void goesBackThroughThePreviousThemesOneAtATime() throws Exception
    {
        apply(themed("#111111", "#000001")).andExpect(status().isOk());
        JsonNode second = read(apply(themed("#222222", "#000002")).andExpect(status().isOk()));
        long firstId = second.get("id").asLong() - 1;

        mvc.perform(post("/api/admin/book/theme/" + firstId + "/revert").cookie(session).with(xsrf()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("NOTHING_TO_REVERT"));

        JsonNode afterFirstRevert = read(mvc.perform(post("/api/admin/book/theme/" + second.get("id").asLong()
            + "/revert").cookie(session).with(xsrf())).andExpect(status().isOk()));
        assertThat(backgrounds()).containsOnly("#111111");
        assertThat(afterFirstRevert.get("id").asLong()).isEqualTo(firstId);

        mvc.perform(post("/api/admin/book/theme/" + firstId + "/revert").cookie(session).with(xsrf()))
            .andExpect(status().isOk());
        assertThat(backgrounds()).containsOnly("#ffffff");
        assertThat(siteColor()).isEqualTo("#1f1a17");
    }

    @Test
    void keepsTheLastTenThemes() throws Exception
    {
        for (int index = 0; index < ThemeSnapshots.KEPT + 2; index++)
        {
            apply(themed(String.format("#%06d", index), "#000000")).andExpect(status().isOk());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM theme_snapshot", Integer.class))
            .isEqualTo(ThemeSnapshots.KEPT);
    }

    @Test
    void requiresAnAdministrator() throws Exception
    {
        mvc.perform(get("/api/admin/book/theme")).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- helpers

    /** Every page of the book at its current version, with a new page colour. */
    private ObjectNode themed(String pageColor, String siteColor) throws Exception
    {
        ArrayNode themed = json.createArrayNode();
        for (JsonNode summary : pages())
        {
            JsonNode page = tree(mvc.perform(get("/api/admin/pages/" + summary.get("id").asText()).cookie(session))
                .andReturn().getResponse().getContentAsString());
            ObjectNode content = page.get("content").deepCopy();
            content.set("background", json.createObjectNode().put("type", "color").put("value", pageColor));
            themed.addObject().put("id", page.get("id").asText()).put("version", page.get("version").asInt())
                .set("content", content);
        }
        ObjectNode body = json.createObjectNode().put("themeId", "nuit").put("backgroundColor", siteColor);
        body.set("pages", themed);
        return body;
    }

    private ResultActions apply(ObjectNode body) throws Exception
    {
        return mvc.perform(post("/api/admin/book/theme").cookie(session).with(xsrf())
            .contentType(MediaType.APPLICATION_JSON).content(body.toString()));
    }

    private JsonNode pages() throws Exception
    {
        return tree(mvc.perform(get("/api/admin/pages").cookie(session)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
    }

    private java.util.List<String> backgrounds()
    {
        return jdbc.queryForList("SELECT content -> 'background' ->> 'value' FROM page", String.class);
    }

    private String siteColor()
    {
        return jdbc.queryForObject("SELECT background_color FROM site_settings WHERE id = 1", String.class);
    }

    private boolean mediaExists(String id)
    {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM media WHERE id = ?::uuid)",
            Boolean.class, id));
    }

    private void createProject(String title) throws Exception
    {
        mvc.perform(post("/api/admin/projects").cookie(session).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.createObjectNode().put("title", title).toString()))
            .andExpect(status().isCreated());
    }

    private String upload(String kind) throws Exception
    {
        String body = mvc.perform(multipart("/api/admin/media")
                .file(new MockMultipartFile("file", "photo.png", "image/png", TestData.png(40, 30)))
                .param("kind", kind)
                .cookie(session).with(xsrf()))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return tree(body).get("id").asText();
    }

    private JsonNode read(ResultActions result) throws Exception
    {
        return tree(result.andReturn().getResponse().getContentAsString());
    }

    private JsonNode tree(String body)
    {
        try
        {
            return json.readTree(body);
        }
        catch (java.io.IOException exception)
        {
            throw new java.io.UncheckedIOException(exception);
        }
    }

}
