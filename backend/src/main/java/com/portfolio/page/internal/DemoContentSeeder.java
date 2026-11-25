package com.portfolio.page.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.media.MediaApi;
import com.portfolio.media.MediaInfo;
import com.portfolio.settings.SettingsApi;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * On the very first start (empty book), creates the published fixed pages (cover, table of contents,
 * contact) in the spirit of an engineering portfolio,
 * with original generated illustrations. The owner replaces them in the editor. Disabled with DEMO_CONTENT=false.
 */
@Component
@org.springframework.core.annotation.Order(10)
class DemoContentSeeder implements ApplicationRunner
{
    private static final Logger LOG = LoggerFactory.getLogger(DemoContentSeeder.class);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(id|img:[a-z]+)}}");

    private final boolean enabled;
    private final PageRepository repository;
    private final BookEditions editions;
    private final PageContentValidator validator;
    private final MediaApi mediaApi;
    private final SettingsApi settingsApi;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    DemoContentSeeder(@Value("${app.demo-content:false}") boolean enabled, PageRepository repository,
        BookEditions editions,
        PageContentValidator validator, MediaApi mediaApi, SettingsApi settingsApi, ObjectMapper objectMapper,
        Clock clock)
    {
        this.enabled = enabled;
        this.repository = repository;
        this.editions = editions;
        this.validator = validator;
        this.mediaApi = mediaApi;
        this.settingsApi = settingsApi;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException
    {
        if (!enabled || repository.count() > 0 || !settingsApi.claimDemoSeeding())
        {
            return;
        }
        Map<String, MediaInfo> images = Map.of(
            "workshop", mediaApi.storeImage(DemoImages.workshop(), "atelier.jpg"),
            "blueprint", mediaApi.storeImage(DemoImages.blueprint(), "plan-technique.jpg"),
            "circuit", mediaApi.storeImage(DemoImages.circuit(), "circuit.jpg"),
            "portrait", mediaApi.storeImage(DemoImages.portrait(), "composition.jpg"));
        MediaInfo backdrop = mediaApi.storeImage(DemoImages.backdrop(), "arriere-plan.jpg");
        settingsApi.applyDemoLook("Portfolio d'ingénierie", backdrop.id());

        JsonNode pages;
        try (InputStream input = new ClassPathResource("demo/pages.json").getInputStream())
        {
            String template = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            pages = objectMapper.readTree(fill(template, images));
        }
        int position = 0;
        for (JsonNode page : pages)
        {
            String json = validator.validate(page.get("content")).json();
            PageEntity entity = new PageEntity(PageKind.valueOf(page.get("kind").asText()), null,
                page.get("slug").asText(), page.get("title").asText(), position++, json, null, clock.instant());
            repository.save(entity);
        }
        repository.flush();
        editions.publish();
        LOG.info("Demo content created and published ({} pages)", position);
    }

    private static String fill(String template, Map<String, MediaInfo> images)
    {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find())
        {
            String key = matcher.group(1);
            String value = "id".equals(key) ? UUID.randomUUID().toString()
                : images.get(key.substring("img:".length())).id().toString();
            matcher.appendReplacement(out, value);
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
