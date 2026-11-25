package com.portfolio.media.internal;

import java.time.Instant;
import java.util.UUID;

record MediaResponse(UUID id, String url, String originalName, String mimeType, long sizeBytes, int width,
    int height, Instant createdAt)
{
    static MediaResponse from(Media media)
    {
        return new MediaResponse(media.getId(), media.url(), media.getOriginalName(), media.getMimeType(),
            media.getSizeBytes(), media.getWidth(), media.getHeight(), media.getCreatedAt());
    }
}
