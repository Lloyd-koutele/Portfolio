package com.portfolio.profile.internal;

import static com.portfolio.TestRequests.adminSession;
import static com.portfolio.TestRequests.xsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portfolio.IntegrationTest;
import com.portfolio.TestData;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class ProfileIT
{
    private static final String VALID = """
        {"fullName":"Ada Lovelace","headline":"Ingénieure logiciel","intro":"Bonjour","availability":"Disponible",
         "skills":["Java"," Java ","SQL"],
         "experiences":[{"role":"Architecte","organization":"ACME","period":"2024","description":"SI"}],
         "photoMediaId":null}
        """;

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
    void homePageProfileIsPublicAndListsPublishedProjects() throws Exception
    {
        TestData.insertFixedPages(jdbc);
        TestData.insertPublishedPage(jdbc, "projet-alpha", 0);

        mvc.perform(get("/api/public/profile"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").isNotEmpty())
            .andExpect(jsonPath("$.skills").isArray())
            .andExpect(jsonPath("$.projects.length()").value(1))
            .andExpect(jsonPath("$.projects[0].slug").value("projet-alpha"))
            .andExpect(jsonPath("$.projects[0].page").value(3));
    }

    @Test
    void onlyTheAdministratorEditsIt() throws Exception
    {
        mvc.perform(put("/api/admin/profile").with(xsrf()).contentType(MediaType.APPLICATION_JSON).content(VALID))
            .andExpect(status().isUnauthorized());

        Cookie session = adminSession(mvc);
        mvc.perform(put("/api/admin/profile").with(xsrf()).cookie(session).contentType(MediaType.APPLICATION_JSON)
                .content(VALID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").value("Ada Lovelace"))
            .andExpect(jsonPath("$.skills.length()").value(2))
            .andExpect(jsonPath("$.experiences[0].organization").value("ACME"));
        mvc.perform(get("/api/public/profile")).andExpect(jsonPath("$.headline").value("Ingénieure logiciel"));
    }

    @Test
    void invalidProfilesAreRejected() throws Exception
    {
        Cookie session = adminSession(mvc);
        mvc.perform(put("/api/admin/profile").with(xsrf()).cookie(session).contentType(MediaType.APPLICATION_JSON)
                .content(VALID.replace("\"Ada Lovelace\"", "\"\"")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.fullName").exists());
        mvc.perform(put("/api/admin/profile").with(xsrf()).cookie(session).contentType(MediaType.APPLICATION_JSON)
                .content(VALID.replace("\"photoMediaId\":null",
                    "\"photoMediaId\":\"00000000-0000-0000-0000-000000000001\"")))
            .andExpect(status().isBadRequest());
    }
}
