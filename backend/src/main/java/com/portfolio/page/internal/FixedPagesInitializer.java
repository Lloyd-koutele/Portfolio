package com.portfolio.page.internal;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Makes sure the book's fixed pages exist (cover, table of contents, contact). Missing ones
 * are created as drafts; the table of contents starts with a title and the automatic "Sommaire" block.
 * Runs after the demo content, which creates them with a full design on a fresh install.
 */
@Component
@Order(20)
class FixedPagesInitializer implements ApplicationRunner
{
    private static final Logger LOG = LoggerFactory.getLogger(FixedPagesInitializer.class);

    private final PageRepository repository;
    private final PageContentValidator validator;
    private final PageService pages;
    private final Clock clock;

    FixedPagesInitializer(PageRepository repository, PageContentValidator validator, PageService pages, Clock clock)
    {
        this.repository = repository;
        this.validator = validator;
        this.pages = pages;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args)
    {
        for (PageKind kind : PageKind.values())
        {
            if (kind.isFixed() && repository.findByKind(kind).isEmpty())
            {
                ObjectNode content = kind == PageKind.TABLE_OF_CONTENTS ? tableOfContents() : validator.emptyContent();
                repository.save(new PageEntity(kind, null, pages.uniqueSlug(kind.defaultSlug()), kind.defaultTitle(),
                    0, pages.write(content), null, clock.instant()));
                LOG.info("Created the missing fixed page {}", kind);
            }
        }
    }

    /** Default table of contents: a title and the automatic list of projects. */
    private ObjectNode tableOfContents()
    {
        ObjectNode content = validator.emptyContent();
        ArrayNode elements = (ArrayNode) content.get("elements");
        ObjectNode title = elements.addObject();
        title.put("id", UUID.randomUUID().toString()).put("type", "text").put("x", 50).put("y", 48).put("width", 700)
            .put("height", 80).put("rotation", 0).put("zIndex", 0).put("opacity", 1).put("locked", false);
        title.putObject("props").put("text", "Sommaire").put("fontFamily", "Inter").put("fontSize", 56)
            .put("fontWeight", 700).put("italic", false).put("underline", false).put("align", "left")
            .put("color", "#111111").put("lineHeight", 1.2);
        ObjectNode toc = elements.addObject();
        toc.put("id", UUID.randomUUID().toString()).put("type", "toc").put("x", 50).put("y", 180).put("width", 700)
            .put("height", 760).put("rotation", 0).put("zIndex", 1).put("opacity", 1).put("locked", false);
        toc.putObject("props").put("fontFamily", "Inter").put("fontSize", 22).put("fontWeight", 600)
            .put("color", "#111111").put("accentColor", "#ee7d1f").put("lineHeight", 2).put("leaders", true);
        return content;
    }
}
