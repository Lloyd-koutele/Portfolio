package com.portfolio.visit;

import java.util.Locale;

/** Where a download was started from. */
public enum DownloadSource
{
    /** The public book. */
    BOOK,
    /** The administration's home page. */
    HOME,
    /** Any other link. */
    DIRECT;

    /** Reads the {@code source} parameter of a download link; anything unknown counts as {@link #DIRECT}. */
    public static DownloadSource parse(String value)
    {
        if (value != null)
        {
            for (DownloadSource source : values())
            {
                if (source.name().equals(value.trim().toUpperCase(Locale.ROOT)))
                {
                    return source;
                }
            }
        }
        return DIRECT;
    }
}
