package com.portfolio.media;

import java.util.UUID;

/**
 * What a client needs to display a media file.
 *
 * @param id media id
 * @param url public URL ({@code /media/{storedName}})
 * @param width width in pixels
 * @param height height in pixels
 * @param mimeType detected type: image/png, image/jpeg, image/webp, image/gif, video/mp4 or video/webm
 */
public record MediaInfo(UUID id, String url, int width, int height, String mimeType)
{
    public boolean isVideo()
    {
        return mimeType != null && mimeType.startsWith("video/");
    }

    public boolean isImage()
    {
        return mimeType != null && mimeType.startsWith("image/");
    }
}
