package com.portfolio.media.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Media limits ({@code app.media.*}). Images stay limited to 5 MB; videos default to 100 MB
 * ({@code MEDIA_MAX_VIDEO_MB}).
 *
 * @param maxVideoMb largest accepted video, in megabytes
 */
@ConfigurationProperties(prefix = "app.media")
record MediaProperties(@DefaultValue("100") int maxVideoMb)
{
    static final long MEGABYTE = 1024L * 1024L;

    MediaProperties
    {
        if (maxVideoMb < 1 || maxVideoMb > 2048)
        {
            throw new IllegalStateException("MEDIA_MAX_VIDEO_MB must be between 1 and 2048");
        }
    }

    long maxVideoBytes()
    {
        return maxVideoMb * MEGABYTE;
    }
}
