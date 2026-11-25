package com.portfolio.media.internal;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Narrows Tika's container detection to what every browser plays: MP4 (ISO base media with an MP4 brand) and WebM
 * (Matroska whose DocType is "webm"). QuickTime .mov and generic Matroska .mkv are refused.
 */
final class VideoTypes
{
    static final String MP4 = "video/mp4";
    static final String WEBM = "video/webm";

    private static final Set<String> ISO_CONTAINERS = Set.of("video/quicktime", "video/mp4", "video/x-m4v");
    private static final Set<String> MATROSKA_CONTAINERS = Set.of("application/x-matroska", "video/x-matroska",
        "video/webm", "audio/webm");
    private static final Set<String> MP4_BRANDS = Set.of("isom", "iso2", "iso4", "iso5", "iso6", "mp41", "mp42",
        "avc1", "dash", "M4V ", "mmp4", "MSNV");

    private VideoTypes()
    {
    }

    /** True when Tika saw a video container, even one we then refuse (to give a video-specific message). */
    static boolean isVideoContainer(String tikaType)
    {
        return ISO_CONTAINERS.contains(tikaType) || MATROSKA_CONTAINERS.contains(tikaType);
    }

    /**
     * @return {@link #MP4}, {@link #WEBM}, or null when the container holds something browsers may not play
     */
    static String playableType(String tikaType, byte[] header)
    {
        if (ISO_CONTAINERS.contains(tikaType))
        {
            return isMp4Brand(header) ? MP4 : null;
        }
        if (MATROSKA_CONTAINERS.contains(tikaType))
        {
            return docType(header).equals("webm") ? WEBM : null;
        }
        return null;
    }

    /** ISO base media: "ftyp" box at offset 4, major brand right after. */
    private static boolean isMp4Brand(byte[] header)
    {
        if (header.length < 12 || !"ftyp".equals(new String(header, 4, 4, StandardCharsets.US_ASCII)))
        {
            return false;
        }
        return MP4_BRANDS.contains(new String(header, 8, 4, StandardCharsets.US_ASCII));
    }

    /** EBML header: the DocType element (ID 0x4282) gives "webm" or "matroska". */
    private static String docType(byte[] header)
    {
        int limit = Math.min(header.length, 256);
        for (int index = 0; index + 3 < limit; index++)
        {
            if ((header[index] & 0xFF) == 0x42 && (header[index + 1] & 0xFF) == 0x82)
            {
                int sizeByte = header[index + 2] & 0xFF;
                if ((sizeByte & 0x80) == 0)
                {
                    return "";
                }
                int length = sizeByte & 0x7F;
                if (index + 3 + length > header.length)
                {
                    return "";
                }
                return new String(header, index + 3, length, StandardCharsets.US_ASCII);
            }
        }
        return "";
    }
}
