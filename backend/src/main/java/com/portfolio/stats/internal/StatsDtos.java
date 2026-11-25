package com.portfolio.stats.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class StatsDtos
{
    private StatsDtos()
    {
    }

    record Totals(long visits, long uniqueVisitors)
    {
    }

    record Summary(LocalDate from, LocalDate to, long totalVisits, long uniqueVisitors, long visitsToday,
        Totals previous, Double visitsChange, Double uniqueVisitorsChange, Map<String, Long> devices,
        long downloads)
    {
    }

    record DayPoint(LocalDate date, long visits, long uniqueVisitors)
    {
    }

    record PageCount(UUID pageId, String slug, String title, boolean deleted, long visits)
    {
    }

    record VisitRow(long id, Instant occurredAt, String ip, UUID pageId, String pageSlug, String pageTitle,
        String deviceType, boolean bot, String referer)
    {
    }

    record PageInfo(int number, int size, long totalElements, int totalPages)
    {
    }

    record VisitPage(List<VisitRow> content, PageInfo page)
    {
    }

    record DownloadRow(long id, Instant occurredAt, String ip, String fileName, String source, String deviceType,
        boolean bot, String referer)
    {
    }

    record DownloadPage(List<DownloadRow> content, PageInfo page)
    {
    }

    record VisitFilter(Period period, String ip, UUID pageId, String device, boolean includeBots)
    {
    }
}
