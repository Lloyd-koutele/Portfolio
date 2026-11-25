package com.portfolio.page.internal;

import java.util.Set;
import java.util.UUID;

/**
 * Thumbnails no longer pointed to by a page; deleted after commit if nothing else uses them.
 */
record PageMediaReleased(Set<UUID> mediaIds)
{
}
