package com.portfolio.stats.internal;

import com.portfolio.common.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.springframework.http.HttpStatus;

/**
 * Inclusive range of UTC days. Defaults to the last 30 days.
 */
record Period(LocalDate from, LocalDate to)
{
    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 800;

    static Period of(LocalDate from, LocalDate to, Clock clock)
    {
        LocalDate end = to != null ? to : LocalDate.now(clock.withZone(ZoneOffset.UTC));
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS - 1L);
        if (start.isAfter(end))
        {
            throw new InvalidPeriodException("from must be before to");
        }
        if (ChronoUnit.DAYS.between(start, end) >= MAX_DAYS)
        {
            throw new InvalidPeriodException("period is too long");
        }
        return new Period(start, end);
    }

    long days()
    {
        return ChronoUnit.DAYS.between(from, to) + 1;
    }

    Instant start()
    {
        return from.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    Instant endExclusive()
    {
        return to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    Period previous()
    {
        return new Period(from.minusDays(days()), from.minusDays(1));
    }

    static class InvalidPeriodException extends ApiException
    {
        InvalidPeriodException(String detail)
        {
            super(HttpStatus.BAD_REQUEST, "Invalid period", detail);
        }
    }
}
