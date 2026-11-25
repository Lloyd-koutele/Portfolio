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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.portfolio.IntegrationTest;
import com.portfolio.TestData;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class PageAdminIT
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
    }

    @Test
    void createsDraftPagesWithGeneratedUniqueSlugs() throws Exception
    {
        JsonNode first = create("Mon Projet Été");
        JsonNode second = create("Mon projet été");

        assertThat(first.get("slug").asText()).isEqualTo("mon-projet-ete");
        assertThat(second.get("slug").asText()).isEqualTo("mon-projet-ete-2");
        assertThat(second.get("kind").asText()).isEqualTo("PROJECT_SHEET");
        assertThat(second.get("projectId").asText()).isNotEqualTo(first.get("projectId").asText());
        assertThat(first.at("/content/elements").size()).isZero();
    }

    @Test
    void staleVersionIsRejectedWith409() throws Exception
    {
        JsonNode page = create("Conflit");
        ObjectNode body = updateBody(page, page.get("content"));
        mvc.perform(put("/api/admin/pages/" + page.get("id").asText()).cookie(session).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(page.get("version").asInt() + 1));

        mvc.perform(put("/api/admin/pages/" + page.get("id").asText()).cookie(session).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
            .andExpect(jsonPath("$.currentVersion").value(page.get("version").asInt() + 1));
    }

    @Test
    void invalidContentIsRejectedWith400AndFieldErrors() throws Exception
    {
        JsonNode page = create("Invalide");
        ObjectNode content = page.get("content").deepCopy();
        ArrayNode elements = (ArrayNode) content.get("elements");
        elements.add(element("script", json.createObjectNode()));
        ObjectNode button = json.createObjectNode().put("label", "Clic").put("href", "javascript:alert(1)")
            .put("fill", "#000000").put("color", "#ffffff").put("borderRadius", 0);
        elements.add(element("button", button));
        ObjectNode video = json.createObjectNode().put("provider", "youtube").put("videoId", "bad id");
        elements.add(element("video", video));

        mvc.perform(put("/api/admin/pages/" + page.get("id").asText()).cookie(session).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content(updateBody(page, content).toString()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors['elements[0].type']").exists())
            .andExpect(jsonPath("$.errors['elements[1].props.href']").exists())
            .andExpect(jsonPath("$.errors['elements[2].props.videoId']").exists());
    }

    @Test
    void moreThan300ElementsAreRejected() throws Exception
    {
        JsonNode page = create("Trop");
        ObjectNode content = page.get("content").deepCopy();
        ArrayNode elements = (ArrayNode) content.get("elements");
        for (int index = 0; index < 301; index++)
        {
            elements.add(element("shape", shapeProps()));
        }

        mvc.perform(put("/api/admin/pages/" + page.get("id").asText()).cookie(session).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content(updateBody(page, content).toString()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.elements").exists());
    }

    @Test
    void everyElementTypeRoundTrips() throws Exception
    {
        String mediaId = upload();
        JsonNode page = create("Complet");
        ObjectNode content = page.get("content").deepCopy();
        ArrayNode elements = (ArrayNode) content.get("elements");
        elements.add(element("text", json.createObjectNode().put("text", "Bonjour").put("fontFamily", "Inter")
            .put("fontSize", 32).put("fontWeight", 700).put("italic", false).put("underline", false)
            .put("align", "left").put("color", "#111111").put("lineHeight", 1.2)));
        elements.add(element("image", json.createObjectNode().put("mediaId", mediaId).put("alt", "Photo")
            .put("fit", "cover").put("borderRadius", 8)));
        elements.add(element("shape", shapeProps()));
        elements.add(element("drawing", json.createObjectNode().put("path", "M 0 0 L 10 10 Z")
            .put("viewBox", "0 0 10 10").put("stroke", "#111111").put("strokeWidth", 2).put("fill", "transparent")));
        elements.add(element("video", json.createObjectNode().put("provider", "vimeo").put("videoId", "76979871")));
        elements.add(element("button", json.createObjectNode().put("label", "Écrire").put("href", "mailto:a@b.fr")
            .put("fill", "#ee7d1f").put("color", "#ffffff").put("borderRadius", 4)));

        mvc.perform(put("/api/admin/pages/" + page.get("id").asText()).cookie(session).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content(updateBody(page, content).toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.elements", hasSize(6)))
            .andExpect(jsonPath("$.media['" + mediaId + "'].url").exists());

        mvc.perform(get("/api/admin/pages/" + page.get("id").asText()).cookie(session))
            .andExpect(jsonPath("$.content.elements[0].props.text").value("Bonjour"))
            .andExpect(jsonPath("$.content.elements[3].props.path").value("M 0 0 L 10 10 Z"));
    }

    @Test
    void videoElementsCanUseUploadedVideosButNotImages() throws Exception
    {
        String video = json.readTree(mvc.perform(multipart("/api/admin/media")
                .file(new MockMultipartFile("file", "v.mp4", "video/mp4", TestData.mp4(100)))
                .param("width", "1280").param("height", "720").cookie(session).with(xsrf()))
            .andReturn().getResponse().getContentAsString()).get("id").asText();
        String image = upload();
        JsonNode page = create("Vidéo");

        ObjectNode withVideo = page.get("content").deepCopy();
        ((ArrayNode) withVideo.get("elements")).add(element("video",
            json.createObjectNode().put("provider", "file").put("mediaId", video)));
        JsonNode saved = json.readTree(mvc.perform(put("/api/admin/pages/" + page.get("id").asText()).cookie(session)
                .with(xsrf()).contentType(MediaType.APPLICATION_JSON).content(updateBody(page, withVideo).toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.media['" + video + "'].mimeType").value("video/mp4"))
            .andReturn().getResponse().getContentAsString());

        ObjectNode imageAsVideo = page.get("content").deepCopy();
        ((ArrayNode) imageAsVideo.get("elements")).add(element("video",
            json.createObjectNode().put("provider", "file").put("mediaId", image)));
        mvc.perform(put("/api/admin/pages/" + page.get("id").asText()).cookie(session).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content(updateBody(saved, imageAsVideo).toString()))
            .andExpect(status().isBadRequest());

        ObjectNode videoAsImage = page.get("content").deepCopy();
        ((ArrayNode) videoAsImage.get("elements")).add(element("image", json.createObjectNode()
            .put("mediaId", video).put("alt", "x").put("fit", "cover").put("borderRadius", 0)));
        mvc.perform(put("/api/admin/pages/" + page.get("id").asText()).cookie(session).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content(updateBody(saved, videoAsImage).toString()))
            .andExpect(status().isBadRequest());

        mvc.perform(delete("/api/admin/media/" + video).cookie(session).with(xsrf()))
            .andExpect(status().isConflict());
    }

    @Test
    void unknownMediaIsRejected() throws Exception
    {
        JsonNode page = create("Média inconnu");
        ObjectNode content = page.get("content").deepCopy();
        ((ArrayNode) content.get("elements")).add(element("image", json.createObjectNode()
            .put("mediaId", UUID.randomUUID().toString()).put("alt", "x").put("fit", "cover").put("borderRadius", 0)));

        mvc.perform(put("/api/admin/pages/" + page.get("id").asText()).cookie(session).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content(updateBody(page, content).toString()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void readersSeeNothingUntilTheSiteIsPublishedThenEverythingInOrder() throws Exception
    {
        JsonNode first = create("Premier");
        JsonNode second = create("Second");
        reorder(second, first);

        mvc.perform(get("/api/public/book")).andExpect(jsonPath("$.pages", hasSize(0)));
        mvc.perform(get("/api/public/pages/premier")).andExpect(status().isNotFound());

        publishSite();

        mvc.perform(get("/api/public/book"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pages", hasSize(2)))
            .andExpect(jsonPath("$.pages[0].slug").value("second"))
            .andExpect(jsonPath("$.pages[1].slug").value("premier"))
            .andExpect(jsonPath("$.settings.pageWidth").value(800));
        mvc.perform(get("/api/public/pages/premier")).andExpect(status().isOk());
    }

    @Test
    void reorderingKeepsEditorVersionsValid() throws Exception
    {
        JsonNode first = create("Un");
        JsonNode second = create("Deux");

        reorder(second, first);

        mvc.perform(get("/api/admin/pages").cookie(session))
            .andExpect(jsonPath("$[0].id").value(second.get("id").asText()))
            .andExpect(jsonPath("$[0].version").value(second.get("version").asInt()));
        mvc.perform(put("/api/admin/projects/order").cookie(session).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"" + first.get("projectId").asText() + "\"]}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateCreatesADraftCopy() throws Exception
    {
        JsonNode page = create("Original");

        mvc.perform(post("/api/admin/pages/" + page.get("id").asText() + "/duplicate").cookie(session).with(xsrf()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("Original (copie)"))
            .andExpect(jsonPath("$.slug").value("original-2"));
    }

    @Test
    void deletingAPageKeepsItsVisitsWithoutPageId() throws Exception
    {
        JsonNode page = create("Supprimée");
        jdbc.update("""
            INSERT INTO visit (ip_display, ip_hash, device_type, page_id, page_slug, session_id)
            VALUES ('1.2.3.xxx', repeat('a', 64), 'DESKTOP', ?, 'supprimee', ?)
            """, UUID.fromString(page.get("id").asText()), UUID.randomUUID());

        mvc.perform(delete("/api/admin/pages/" + page.get("id").asText()).cookie(session).with(xsrf()))
            .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM visit WHERE page_id IS NULL", Integer.class)).isEqualTo(1);
        mvc.perform(get("/api/admin/pages/" + page.get("id").asText()).cookie(session))
            .andExpect(status().isNotFound());
    }

    @Test
    void slugMustBeValidAndUnique() throws Exception
    {
        JsonNode first = create("Alpha");
        JsonNode second = create("Beta");
        ObjectNode body = updateBody(second, second.get("content"));

        body.put("slug", first.get("slug").asText());
        mvc.perform(put("/api/admin/pages/" + second.get("id").asText()).cookie(session).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.slug").exists());

        body.put("slug", "Pas Valide!");
        mvc.perform(put("/api/admin/pages/" + second.get("id").asText()).cookie(session).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.slug").exists());
    }

    @Test
    void replacedThumbnailIsDeleted() throws Exception
    {
        JsonNode page = create("Miniature");
        String firstThumb = upload("THUMBNAIL");
        ObjectNode body = updateBody(page, page.get("content"));
        body.put("thumbnailMediaId", firstThumb);
        JsonNode saved = json.readTree(mvc.perform(put("/api/admin/pages/" + page.get("id").asText())
                .cookie(session).with(xsrf()).contentType(MediaType.APPLICATION_JSON).content(body.toString()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        String secondThumb = upload("THUMBNAIL");
        ObjectNode next = updateBody(saved, saved.get("content"));
        next.put("thumbnailMediaId", secondThumb);
        mvc.perform(put("/api/admin/pages/" + page.get("id").asText()).cookie(session).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content(next.toString()))
            .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM media WHERE id = ?::uuid", Integer.class, firstThumb))
            .isZero();
        mvc.perform(get("/api/admin/pages").cookie(session)).andExpect(jsonPath("$[0].thumbnailUrl").exists());
    }

    @Test
    void contentChangesStayPrivateUntilTheSiteIsPublished() throws Exception
    {
        JsonNode page = create("Édition");
        JsonNode first = save(page, "Première édition");
        publishSite();
        save(first, "Brouillon de la deuxième édition");

        mvc.perform(get("/api/public/pages/edition"))
            .andExpect(jsonPath("$.page.content.elements[0].props.text").value("Première édition"));
        mvc.perform(get("/api/admin/book").cookie(session))
            .andExpect(jsonPath("$.publication.upToDate").value(false))
            .andExpect(jsonPath("$.publication.publishedAt").exists());

        publishSite();
        mvc.perform(get("/api/public/book"))
            .andExpect(jsonPath("$.pages[0].content.elements[0].props.text").value("Brouillon de la deuxième édition"));
        mvc.perform(get("/api/admin/book").cookie(session))
            .andExpect(jsonPath("$.publication.upToDate").value(true));
    }

    @Test
    void aDeletedSheetStaysOnlineUntilTheNextPublication() throws Exception
    {
        JsonNode page = create("Retirée");
        save(page, "Texte publié");
        publishSite();

        mvc.perform(delete("/api/admin/projects/" + page.get("projectId").asText()).cookie(session).with(xsrf()))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/public/pages/retiree"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.content.elements[0].props.text").value("Texte publié"));

        publishSite();
        mvc.perform(get("/api/public/pages/retiree")).andExpect(status().isNotFound());
    }

    @Test
    void theBookAnswers304UntilANewEditionIsPublished() throws Exception
    {
        JsonNode page = create("Une");
        String id = page.get("id").asText();
        publishSite();
        String etag = mvc.perform(get("/api/public/book"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getHeader("ETag");
        assertThat(etag).isNotBlank();

        mvc.perform(get("/api/public/book").header("If-None-Match", etag)).andExpect(status().isNotModified());

        JsonNode detail = json.readTree(mvc.perform(get("/api/admin/pages/" + id).cookie(session))
            .andReturn().getResponse().getContentAsString());
        save(detail, "Nouvelle édition");
        mvc.perform(get("/api/public/book").header("If-None-Match", etag)).andExpect(status().isNotModified());
        publishSite();
        mvc.perform(get("/api/public/book").header("If-None-Match", etag)).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- helpers

    private void publishSite() throws Exception
    {
        mvc.perform(post("/api/admin/book/publish").cookie(session).with(xsrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.upToDate").value(true));
    }

    /** Saves the page with a single text element, as the editor's autosave does. */
    private JsonNode save(JsonNode page, String text) throws Exception
    {
        ObjectNode content = page.get("content").deepCopy();
        ArrayNode elements = content.putArray("elements");
        elements.add(element("text", json.createObjectNode().put("text", text).put("fontFamily", "Inter")
            .put("fontSize", 32).put("fontWeight", 700).put("italic", false).put("underline", false)
            .put("align", "left").put("color", "#111111").put("lineHeight", 1.2)));
        String body = mvc.perform(put("/api/admin/pages/" + page.get("id").asText()).cookie(session).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content(updateBody(page, content).toString()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private JsonNode create(String title) throws Exception
    {
        String body = mvc.perform(post("/api/admin/projects").cookie(session).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.createObjectNode().put("title", title).toString()))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    /** Reorders the projects of the given sheets (each test project has one sheet). */
    private void reorder(JsonNode... pages) throws Exception
    {
        ArrayNode ids = json.createArrayNode();
        for (JsonNode page : pages)
        {
            ids.add(page.get("projectId").asText());
        }
        mvc.perform(put("/api/admin/projects/order").cookie(session).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.createObjectNode().set("ids", ids).toString()))
            .andExpect(status().isNoContent());
    }

    private ObjectNode updateBody(JsonNode page, JsonNode content)
    {
        ObjectNode body = json.createObjectNode();
        body.put("title", page.get("title").asText());
        body.put("slug", page.get("slug").asText());
        body.set("content", content);
        body.put("version", page.get("version").asInt());
        return body;
    }

    private ObjectNode element(String type, ObjectNode props)
    {
        ObjectNode element = json.createObjectNode();
        element.put("id", UUID.randomUUID().toString());
        element.put("type", type);
        element.put("x", 10).put("y", 20).put("width", 100).put("height", 50).put("rotation", 0)
            .put("zIndex", 1).put("opacity", 1).put("locked", false);
        element.set("props", props);
        return element;
    }

    private ObjectNode shapeProps()
    {
        return json.createObjectNode().put("shape", "rect").put("fill", "#ee7d1f").put("stroke", "transparent")
            .put("strokeWidth", 0).put("borderRadius", 0);
    }

    private String upload() throws Exception
    {
        return upload("UPLOAD");
    }

    private String upload(String kind) throws Exception
    {
        String body = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/api/admin/media")
                .file(new MockMultipartFile("file", "photo.png", "image/png", TestData.png(40, 30)))
                .param("kind", kind)
                .cookie(session).with(xsrf()))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asText();
    }
}
