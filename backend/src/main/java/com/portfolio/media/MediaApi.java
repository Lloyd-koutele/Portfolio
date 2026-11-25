package com.portfolio.media;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Media operations available to other modules.
 */
public interface MediaApi
{
    /**
     * @return the ids among {@code ids} that exist
     */
    Set<UUID> existing(Collection<UUID> ids);

    Map<UUID, MediaInfo> describe(Collection<UUID> ids);

    /**
     * Deletes the media (row and file) when no {@link MediaUsage} references it any more.
     *
     * @return whether it was deleted
     */
    boolean deleteIfUnused(UUID mediaId);

    /**
     * Stores a validated image, as the upload endpoint does. Used by the demo content seeder.
     */
    MediaInfo storeImage(byte[] content, String originalName);
}
