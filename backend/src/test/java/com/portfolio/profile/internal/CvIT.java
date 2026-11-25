package com.portfolio.profile.internal;

import static com.portfolio.TestRequests.adminSession;
import static com.portfolio.TestRequests.remoteAddr;
import static com.portfolio.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portfolio.IntegrationTest;
import com.portfolio.TestData;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class CvIT
{
    private static final byte[] PDF = "%PDF-1.7\n%fake cv\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

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
    void noCvAtFirst() throws Exception
    {
        mvc.perform(get("/api/public/cv"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false));
        mvc.perform(get("/api/public/cv/download")).andExpect(status().isNotFound());
    }

    @Test
    void onlyTheAdministratorUploadsAndOnlyPdfs() throws Exception
    {
        mvc.perform(multipart("/api/admin/cv").file(pdf("cv.pdf", PDF)).with(xsrf()))
            .andExpect(status().isUnauthorized());

        Cookie session = adminSession(mvc);
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A};
        mvc.perform(multipart("/api/admin/cv").file(pdf("cv.pdf", png)).with(xsrf()).cookie(session))
            .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cv_document", Integer.class)).isZero();
    }

    @Test
    void visitorsDownloadItAndEachDownloadIsLogged() throws Exception
    {
        Cookie session = adminSession(mvc);
        mvc.perform(multipart("/api/admin/cv").file(pdf("../Mon CV <2026>.pdf", PDF)).with(xsrf()).cookie(session))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.fileName").value("Mon CV 2026.pdf"))
            .andExpect(jsonPath("$.sizeBytes").value(PDF.length));

        mvc.perform(get("/api/public/cv"))
            .andExpect(jsonPath("$.available").value(true))
            .andExpect(jsonPath("$.fileName").value("Mon CV 2026.pdf"));

        mvc.perform(get("/api/public/cv/download").param("source", "book").with(remoteAddr("203.0.113.7"))
                .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header(HttpHeaders.REFERER, "https://portfolio-public/p/couverture?secret=1"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, Matchers.startsWith("attachment;")))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(content().bytes(PDF));
        mvc.perform(get("/api/public/cv/download").param("source", "nimporte").with(remoteAddr("198.51.100.4"))
                .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) Mobile"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/public/cv/download").param("source", "home").with(remoteAddr("198.51.100.5")))
            .andExpect(status().isOk());

        Map<String, Object> first = jdbc.queryForMap(
            "SELECT source, file_name, ip_display, device_type, referer FROM download ORDER BY id LIMIT 1");
        assertThat(first).containsEntry("source", "BOOK").containsEntry("file_name", "Mon CV 2026.pdf")
            .containsEntry("ip_display", "203.0.113.xxx").containsEntry("device_type", "DESKTOP")
            .containsEntry("referer", "https://portfolio-public/p/couverture");
        assertThat(jdbc.queryForList("SELECT source FROM download ORDER BY id", String.class))
            .containsExactly("BOOK", "DIRECT", "HOME");
        // Without a User-Agent the download counts as a robot: logged, but left out of the dashboard by default.

        mvc.perform(get("/api/admin/stats/downloads").cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.content[1].source").value("BOOK"))
            .andExpect(jsonPath("$.page.totalElements").value(2));
        mvc.perform(get("/api/admin/stats/downloads").param("includeBots", "true").cookie(session))
            .andExpect(jsonPath("$.content.length()").value(3));
        mvc.perform(get("/api/admin/stats/summary").cookie(session)).andExpect(jsonPath("$.downloads").value(2));
    }

    @Test
    void theAdministratorsOwnDownloadsAreNotLogged() throws Exception
    {
        Cookie session = adminSession(mvc);
        mvc.perform(multipart("/api/admin/cv").file(pdf("cv.pdf", PDF)).with(xsrf()).cookie(session));

        mvc.perform(get("/api/public/cv/download").cookie(session)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/cv/file").cookie(session))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, Matchers.startsWith("inline;")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM download", Integer.class)).isZero();
    }

    @Test
    void replacingThenDeletingTheCv() throws Exception
    {
        Cookie session = adminSession(mvc);
        mvc.perform(multipart("/api/admin/cv").file(pdf("ancien.pdf", PDF)).with(xsrf()).cookie(session));
        mvc.perform(multipart("/api/admin/cv").file(pdf("nouveau.pdf", PDF)).with(xsrf()).cookie(session))
            .andExpect(status().isCreated());
        mvc.perform(get("/api/public/cv")).andExpect(jsonPath("$.fileName").value("nouveau.pdf"));

        mvc.perform(delete("/api/admin/cv").with(xsrf()).cookie(session)).andExpect(status().isNoContent());
        mvc.perform(get("/api/public/cv")).andExpect(jsonPath("$.available").value(false));
        mvc.perform(get("/api/public/cv/download")).andExpect(status().isNotFound());
    }

    private static MockMultipartFile pdf(String name, byte[] content)
    {
        return new MockMultipartFile("file", name, "application/pdf", content);
    }
}
