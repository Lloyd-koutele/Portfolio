package com.portfolio.media.internal;

import static com.portfolio.TestRequests.adminSession;
import static com.portfolio.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.IntegrationTest;
import com.portfolio.TestData;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class MediaIT
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
    void uploadsAnImageAndServesItWithLongCache() throws Exception
    {
        JsonNode media = upload("photo.png", TestData.png(64, 48), status().isCreated());

        assertThat(media.get("width").asInt()).isEqualTo(64);
        assertThat(media.get("height").asInt()).isEqualTo(48);
        assertThat(media.get("url").asText()).matches("/media/[a-f0-9-]{36}\\.png");
        String etag = mvc.perform(get(media.get("url").asText()))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=31536000, public, immutable"))
            .andExpect(header().exists(HttpHeaders.ETAG))
            .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
        mvc.perform(get(media.get("url").asText()).header(HttpHeaders.IF_NONE_MATCH, etag))
            .andExpect(status().isNotModified());
    }

    @Test
    void aPngThatIsNotAnImageIsRefused() throws Exception
    {
        byte[] fake = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);

        mvc.perform(multipart("/api/admin/media").file(new MockMultipartFile("file", "image.png", "image/png", fake))
                .cookie(session).with(xsrf()))
            .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM media", Integer.class)).isZero();
    }

    @Test
    void svgIsRefused() throws Exception
    {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8);

        upload("dessin.svg", svg, status().isBadRequest());
    }

    @Test
    void exifMetadataIsRemoved() throws Exception
    {
        JsonNode media = upload("photo.jpg", TestData.jpegWithExif(30, 20), status().isCreated());

        byte[] stored = mvc.perform(get(media.get("url").asText())).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("GPS-SECRET-LOCATION")
            .doesNotContain("Exif");
        assertThat(media.get("mimeType").asText()).isEqualTo("image/jpeg");
    }

    @Test
    void aMediaUsedByAPageCannotBeDeleted() throws Exception
    {
        JsonNode media = upload("photo.png", TestData.png(10, 10), status().isCreated());
        String id = media.get("id").asText();
        jdbc.update("""
            INSERT INTO page (id, slug, title, position, content, version, kind)
            VALUES (?, 'avec-image', 'Avec image', 0, ?::jsonb, 0, 'COVER')
            """, UUID.randomUUID(), "{\"schemaVersion\":1,\"background\":{\"type\":\"color\",\"value\":\"#fff\"},"
            + "\"elements\":[{\"type\":\"image\",\"props\":{\"mediaId\":\"" + id + "\"}}]}");

        mvc.perform(delete("/api/admin/media/" + id).cookie(session).with(xsrf()))
            .andExpect(status().isConflict());

        // Still used by the published edition after leaving the working copy.
        TestData.publishBook(jdbc);
        jdbc.update("DELETE FROM page");
        mvc.perform(delete("/api/admin/media/" + id).cookie(session).with(xsrf()))
            .andExpect(status().isConflict());

        TestData.publishBook(jdbc);
        mvc.perform(delete("/api/admin/media/" + id).cookie(session).with(xsrf()))
            .andExpect(status().isNoContent());
        mvc.perform(get(media.get("url").asText())).andExpect(status().isNotFound());
    }

    @Test
    void aMediaUsedAsSiteBackgroundCannotBeDeleted() throws Exception
    {
        JsonNode media = upload("fond.png", TestData.png(10, 10), status().isCreated());
        jdbc.update("UPDATE site_settings SET background_media_id = ?::uuid", media.get("id").asText());

        mvc.perform(delete("/api/admin/media/" + media.get("id").asText()).cookie(session).with(xsrf()))
            .andExpect(status().isConflict());
    }

    @Test
    void libraryListsUploadsButNotThumbnails() throws Exception
    {
        upload("a.png", TestData.png(10, 10), status().isCreated());
        mvc.perform(multipart("/api/admin/media")
                .file(new MockMultipartFile("file", "thumb.png", "image/png", TestData.png(10, 10)))
                .param("kind", "THUMBNAIL").cookie(session).with(xsrf()))
            .andExpect(status().isCreated());

        mvc.perform(get("/api/admin/media").cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void unsafeFileNamesAreNotServed() throws Exception
    {
        mvc.perform(get("/media/..%2F..%2Fetc%2Fpasswd")).andExpect(status().is4xxClientError());
        mvc.perform(get("/media/passwd")).andExpect(status().isNotFound());
        mvc.perform(get("/media/" + UUID.randomUUID() + ".png")).andExpect(status().isNotFound());
    }

    @Test
    void uploadRequiresAdmin() throws Exception
    {
        mvc.perform(multipart("/api/admin/media")
                .file(new MockMultipartFile("file", "a.png", "image/png", TestData.png(10, 10))).with(xsrf()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadsAnMp4VideoAndServesByteRanges() throws Exception
    {
        byte[] video = TestData.mp4(2000);
        JsonNode media = json.readTree(mvc.perform(multipart("/api/admin/media")
                .file(new MockMultipartFile("file", "demo.mov", "application/octet-stream", video))
                .param("width", "1280").param("height", "720")
                .cookie(session).with(xsrf()))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString());

        assertThat(media.get("mimeType").asText()).isEqualTo("video/mp4");
        assertThat(media.get("url").asText()).endsWith(".mp4");
        assertThat(media.get("width").asInt()).isEqualTo(1280);
        String url = media.get("url").asText();
        mvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "video/mp4"))
            .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"));
        byte[] part = mvc.perform(get(url).header(HttpHeaders.RANGE, "bytes=4-7"))
            .andExpect(status().isPartialContent())
            .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 4-7/" + video.length))
            .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(part, StandardCharsets.US_ASCII)).isEqualTo("ftyp");
    }

    @Test
    void acceptsWebm() throws Exception
    {
        mvc.perform(multipart("/api/admin/media")
                .file(new MockMultipartFile("file", "clip.webm", "video/webm", TestData.webm()))
                .param("width", "640").param("height", "360")
                .cookie(session).with(xsrf()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.mimeType").value("video/webm"));
    }

    @Test
    void videoNeedsItsDimensionsAndMustFitTheLimit() throws Exception
    {
        mvc.perform(multipart("/api/admin/media")
                .file(new MockMultipartFile("file", "a.mp4", "video/mp4", TestData.mp4(100)))
                .cookie(session).with(xsrf()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Invalid video"));
        mvc.perform(multipart("/api/admin/media")
                .file(new MockMultipartFile("file", "big.mp4", "video/mp4", TestData.mp4(1024 * 1024)))
                .param("width", "640").param("height", "360")
                .cookie(session).with(xsrf()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("The video exceeds 1 MB"));
    }

    @Test
    void quickTimeAndMatroskaAreRefused() throws Exception
    {
        byte[] mov = TestData.mp4(100);
        System.arraycopy("qt  ".getBytes(StandardCharsets.US_ASCII), 0, mov, 8, 4);
        byte[] mkv = TestData.webm();
        mkv = new String(mkv, StandardCharsets.ISO_8859_1).replace("\u0084webm", "\u0088matroska")
            .getBytes(StandardCharsets.ISO_8859_1);

        for (byte[] file : new byte[][] {mov, mkv})
        {
            mvc.perform(multipart("/api/admin/media")
                    .file(new MockMultipartFile("file", "clip", "video/mp4", file))
                    .param("width", "640").param("height", "360").cookie(session).with(xsrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Only MP4 and WebM videos are accepted"));
        }
    }

    @Test
    void thumbnailsCannotBeVideos() throws Exception
    {
        mvc.perform(multipart("/api/admin/media")
                .file(new MockMultipartFile("file", "a.mp4", "video/mp4", TestData.mp4(100)))
                .param("kind", "THUMBNAIL").param("width", "640").param("height", "360")
                .cookie(session).with(xsrf()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void libraryCanBeFilteredByType() throws Exception
    {
        upload("a.png", TestData.png(10, 10), status().isCreated());
        mvc.perform(multipart("/api/admin/media")
                .file(new MockMultipartFile("file", "v.mp4", "video/mp4", TestData.mp4(100)))
                .param("width", "640").param("height", "360").cookie(session).with(xsrf()))
            .andExpect(status().isCreated());

        mvc.perform(get("/api/admin/media").param("type", "video").cookie(session))
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.content[0].mimeType").value("video/mp4"));
        mvc.perform(get("/api/admin/media").param("type", "image").cookie(session))
            .andExpect(jsonPath("$.content", hasSize(1)));
        mvc.perform(get("/api/admin/media").cookie(session)).andExpect(jsonPath("$.content", hasSize(2)));
    }

    private JsonNode upload(String name, byte[] content, org.springframework.test.web.servlet.ResultMatcher expected)
        throws Exception
    {
        String body = mvc.perform(multipart("/api/admin/media")
                .file(new MockMultipartFile("file", name, "application/octet-stream", content))
                .cookie(session).with(xsrf()))
            .andExpect(expected)
            .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }
}
