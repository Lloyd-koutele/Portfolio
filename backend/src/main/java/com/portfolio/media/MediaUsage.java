package com.portfolio.media;

import java.util.UUID;

/**
 * Implemented by modules that reference media (pages, site settings). A media file still referenced
 * somewhere cannot be deleted.
 */
public interface MediaUsage
{
    boolean isUsed(UUID mediaId);
}
