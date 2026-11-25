package com.portfolio.page.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.IntegrationTest;
import com.portfolio.TestData;
import com.portfolio.media.MediaApi;
import com.portfolio.settings.SettingsApi;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class DemoContentSeederIT
{
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PageRepository repository;

    @Autowired
    private BookEditions editions;

    @Autowired
    private PageContentValidator validator;

    @Autowired
    private MediaApi mediaApi;

    @Autowired
    private SettingsApi settingsApi;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Clock clock;

    @BeforeEach
    void setUp()
    {
        TestData.reset(jdbc);
        jdbc.update("UPDATE site_settings SET demo_seeded = false");
    }

    @Test
    void createsValidPublishedDemoPagesOnlyOnce() throws Exception
    {
        DemoContentSeeder seeder = new DemoContentSeeder(true, repository, editions, validator, mediaApi, settingsApi,
            objectMapper, clock);

        seeder.run(null);

        assertThat(jdbc.queryForObject("SELECT jsonb_array_length(pages) FROM book_edition", Integer.class))
            .isEqualTo(3);
        assertThat(jdbc.queryForList("SELECT kind FROM page ORDER BY position, kind", String.class))
            .containsExactlyInAnyOrder("COVER", "TABLE_OF_CONTENTS", "CONTACT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM page WHERE kind = 'TABLE_OF_CONTENTS'"
            + " AND jsonb_path_exists(content, '$.elements[*] ? (@.type == \"toc\")')", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM media", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT background_media_id IS NOT NULL FROM site_settings", Boolean.class))
            .isTrue();

        jdbc.update("DELETE FROM page");
        seeder.run(null);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM page", Integer.class)).isZero();
    }

    @Test
    void doesNothingWhenDisabled() throws Exception
    {
        new DemoContentSeeder(false, repository, editions, validator, mediaApi, settingsApi, objectMapper, clock)
            .run(null);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM page", Integer.class)).isZero();
    }
}
