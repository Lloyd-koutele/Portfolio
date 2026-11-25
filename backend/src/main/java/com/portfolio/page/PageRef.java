package com.portfolio.page;

import java.util.UUID;

/**
 * Minimal reference to a page for other modules (visit tracking, statistics).
 */
public record PageRef(UUID id, String slug, String title)
{
}
