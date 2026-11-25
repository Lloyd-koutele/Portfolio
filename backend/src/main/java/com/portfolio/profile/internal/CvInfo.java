package com.portfolio.profile.internal;

import java.time.Instant;

/**
 * The CV offered for download; {@code available} is false (and the other fields null) when none was uploaded.
 */
record CvInfo(boolean available, String fileName, Long sizeBytes, Instant updatedAt)
{
    static CvInfo none()
    {
        return new CvInfo(false, null, null, null);
    }
}
