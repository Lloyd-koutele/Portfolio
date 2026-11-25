package com.portfolio.visit.internal;

import java.time.Duration;
import java.time.Period;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Visit tracking settings ({@code app.tracking.*}).
 *
 * @param salt secret salt for IP hashing ({@code TRACKING_SALT}), at least 16 characters
 * @param dedupWindow a session viewing the same page again within this window is not counted again
 * @param retention visits older than this are deleted
 */
@ConfigurationProperties(prefix = "app.tracking")
record TrackingProperties(
    String salt,
    @DefaultValue("30m") Duration dedupWindow,
    @DefaultValue("P13M") Period retention)
{
    static final int MIN_SALT_LENGTH = 16;

    TrackingProperties
    {
        if (salt == null || salt.length() < MIN_SALT_LENGTH)
        {
            throw new IllegalStateException("TRACKING_SALT must be set and at least " + MIN_SALT_LENGTH
                + " characters long");
        }
    }

    @Override
    public String toString()
    {
        return "TrackingProperties[dedupWindow=" + dedupWindow + ", retention=" + retention + "]";
    }
}
