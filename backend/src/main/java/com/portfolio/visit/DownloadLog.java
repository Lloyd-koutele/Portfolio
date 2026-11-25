package com.portfolio.visit;

import org.springframework.security.core.Authentication;

/**
 * Records file downloads (the CV) in the same way as page views: IP according to the site's IP mode, device
 * and robots detected from the User-Agent, administrator excluded, rows purged with the visits.
 */
public interface DownloadLog
{
    /**
     * @param fileName the name of the file served, kept in the log even if the file is replaced later
     * @return whether a row was stored (false for the logged-in administrator)
     */
    boolean recordCvDownload(String fileName, DownloadSource source, String clientIp, String userAgent,
        String referer, Authentication authentication);
}
