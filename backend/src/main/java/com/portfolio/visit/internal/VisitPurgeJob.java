package com.portfolio.visit.internal;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes visits and downloads older than the retention period (13 months), every night.
 */
@Component
class VisitPurgeJob
{
    private static final Logger LOG = LoggerFactory.getLogger(VisitPurgeJob.class);

    private final JdbcTemplate jdbc;
    private final TrackingProperties properties;
    private final Clock clock;

    VisitPurgeJob(JdbcTemplate jdbc, TrackingProperties properties, Clock clock)
    {
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "UTC")
    void scheduledPurge()
    {
        purge();
    }

    int purge()
    {
        Timestamp limit = Timestamp.from(clock.instant().atZone(ZoneOffset.UTC).minus(properties.retention())
            .toInstant());
        int deleted = jdbc.update("DELETE FROM visit WHERE occurred_at < ?", limit);
        deleted += jdbc.update("DELETE FROM download WHERE occurred_at < ?", limit);
        if (deleted > 0)
        {
            LOG.info("Purged {} visits and downloads older than {}", deleted, properties.retention());
        }
        return deleted;
    }
}
