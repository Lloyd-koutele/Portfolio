package com.portfolio.stats.internal;

import com.portfolio.page.PageApi;
import com.portfolio.page.PageRef;
import com.portfolio.stats.internal.StatsDtos.DayPoint;
import com.portfolio.stats.internal.StatsDtos.DownloadPage;
import com.portfolio.stats.internal.StatsDtos.DownloadRow;
import com.portfolio.stats.internal.StatsDtos.PageCount;
import com.portfolio.stats.internal.StatsDtos.PageInfo;
import com.portfolio.stats.internal.StatsDtos.Summary;
import com.portfolio.stats.internal.StatsDtos.Totals;
import com.portfolio.stats.internal.StatsDtos.VisitFilter;
import com.portfolio.stats.internal.StatsDtos.VisitPage;
import com.portfolio.stats.internal.StatsDtos.VisitRow;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * SQL aggregations over {@code visit} and {@code download}. Unique visitors are distinct {@code ip_hash} values.
 */
@Component
@Transactional(readOnly = true)
class StatsQueries
{
    static final Set<String> DEVICES = Set.of("DESKTOP", "MOBILE", "TABLET", "BOT");
    static final int CSV_LIMIT = 100_000;

    private final JdbcTemplate jdbc;
    private final PageApi pageApi;
    private final Clock clock;

    StatsQueries(JdbcTemplate jdbc, PageApi pageApi, Clock clock)
    {
        this.jdbc = jdbc;
        this.pageApi = pageApi;
        this.clock = clock;
    }

    Summary summary(Period period)
    {
        Totals current = totals(period);
        Totals previous = totals(period.previous());
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        long visitsToday = totals(new Period(today, today)).visits();
        Map<String, Long> devices = new LinkedHashMap<>();
        devices.put("DESKTOP", 0L);
        devices.put("MOBILE", 0L);
        devices.put("TABLET", 0L);
        jdbc.query("""
            SELECT device_type, count(*) FROM visit
            WHERE NOT is_bot AND occurred_at >= ? AND occurred_at < ?
            GROUP BY device_type
            """, (ResultSet rs) ->
            {
                devices.put(rs.getString(1), rs.getLong(2));
            }, Timestamp.from(period.start()), Timestamp.from(period.endExclusive()));
        return new Summary(period.from(), period.to(), current.visits(), current.uniqueVisitors(), visitsToday,
            previous, change(current.visits(), previous.visits()),
            change(current.uniqueVisitors(), previous.uniqueVisitors()), devices, downloads(period));
    }

    /** CV downloads, most recent first; robots left out unless asked for. */
    DownloadPage downloadLog(Period period, boolean includeBots, int pageNumber, int size)
    {
        String where = " WHERE occurred_at >= ? AND occurred_at < ?" + (includeBots ? "" : " AND NOT is_bot");
        Timestamp start = Timestamp.from(period.start());
        Timestamp end = Timestamp.from(period.endExclusive());
        Long total = jdbc.queryForObject("SELECT count(*) FROM download" + where, Long.class, start, end);
        List<DownloadRow> rows = jdbc.query("SELECT id, occurred_at, ip_display, file_name, source, device_type,"
            + " is_bot, referer FROM download" + where + " ORDER BY occurred_at DESC, id DESC LIMIT ? OFFSET ?",
            (rs, index) -> new DownloadRow(rs.getLong(1), rs.getTimestamp(2).toInstant(), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getString(6), rs.getBoolean(7), rs.getString(8)),
            start, end, size, (long) pageNumber * size);
        long totalElements = total == null ? 0 : total;
        int totalPages = (int) ((totalElements + size - 1) / size);
        return new DownloadPage(rows, new PageInfo(pageNumber, size, totalElements, totalPages));
    }

    private long downloads(Period period)
    {
        Long count = jdbc.queryForObject(
            "SELECT count(*) FROM download WHERE NOT is_bot AND occurred_at >= ? AND occurred_at < ?", Long.class,
            Timestamp.from(period.start()), Timestamp.from(period.endExclusive()));
        return count == null ? 0 : count;
    }

    List<DayPoint> timeseries(Period period)
    {
        return jdbc.query("""
            SELECT d::date AS day, count(v.id) AS visits, count(DISTINCT v.ip_hash) AS uniques
            FROM generate_series(?::date, ?::date, interval '1 day') AS d
            LEFT JOIN visit v ON NOT v.is_bot
                AND v.occurred_at >= d AND v.occurred_at < d + interval '1 day'
            GROUP BY d
            ORDER BY d
            """, (rs, row) -> new DayPoint(rs.getDate(1).toLocalDate(), rs.getLong(2), rs.getLong(3)),
            Date.valueOf(period.from()), Date.valueOf(period.to()));
    }

    List<PageCount> pages(Period period)
    {
        record Row(UUID pageId, String slug, long visits)
        {
        }
        List<Row> rows = jdbc.query("""
            SELECT page_id, page_slug, count(*) AS visits FROM visit
            WHERE NOT is_bot AND occurred_at >= ? AND occurred_at < ?
            GROUP BY page_id, page_slug
            ORDER BY visits DESC, page_slug
            """, (rs, row) -> new Row(rs.getObject(1, UUID.class), rs.getString(2), rs.getLong(3)),
            Timestamp.from(period.start()), Timestamp.from(period.endExclusive()));
        Map<UUID, PageRef> titles = pageApi.findByIds(rows.stream().map(Row::pageId).filter(Objects::nonNull)
            .collect(Collectors.toSet()));
        return rows.stream().map(row ->
        {
            PageRef page = row.pageId() == null ? null : titles.get(row.pageId());
            return new PageCount(row.pageId(), page != null ? page.slug() : row.slug(),
                page != null ? page.title() : row.slug(), page == null, row.visits());
        }).toList();
    }

    VisitPage visits(VisitFilter filter, int pageNumber, int size)
    {
        List<Object> args = new ArrayList<>();
        String where = where(filter, args);
        Long total = jdbc.queryForObject("SELECT count(*) FROM visit v " + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) pageNumber * size);
        List<VisitRow> rows = withTitles(jdbc.query(select() + where
            + " ORDER BY v.occurred_at DESC, v.id DESC LIMIT ? OFFSET ?", this::row, pageArgs.toArray()));
        long totalElements = total == null ? 0 : total;
        int totalPages = (int) ((totalElements + size - 1) / size);
        return new VisitPage(rows, new PageInfo(pageNumber, size, totalElements, totalPages));
    }

    void forEachVisit(VisitFilter filter, Consumer<VisitRow> consumer)
    {
        List<Object> args = new ArrayList<>();
        String where = where(filter, args);
        args.add(CSV_LIMIT);
        List<VisitRow> rows = withTitles(jdbc.query(select() + where
            + " ORDER BY v.occurred_at DESC, v.id DESC LIMIT ?", this::row, args.toArray()));
        rows.forEach(consumer);
    }

    private Totals totals(Period period)
    {
        return jdbc.queryForObject("""
            SELECT count(*), count(DISTINCT ip_hash) FROM visit
            WHERE NOT is_bot AND occurred_at >= ? AND occurred_at < ?
            """, (rs, row) -> new Totals(rs.getLong(1), rs.getLong(2)),
            Timestamp.from(period.start()), Timestamp.from(period.endExclusive()));
    }

    private static String select()
    {
        return "SELECT v.id, v.occurred_at, v.ip_display, v.page_id, v.page_slug, v.device_type, v.is_bot, v.referer"
            + " FROM visit v ";
    }

    private static String where(VisitFilter filter, List<Object> args)
    {
        StringBuilder sql = new StringBuilder("WHERE v.occurred_at >= ? AND v.occurred_at < ?");
        args.add(Timestamp.from(filter.period().start()));
        args.add(Timestamp.from(filter.period().endExclusive()));
        if (!filter.includeBots())
        {
            sql.append(" AND NOT v.is_bot");
        }
        if (filter.ip() != null && !filter.ip().isBlank())
        {
            sql.append(" AND v.ip_display ILIKE ? ESCAPE '\\'");
            args.add("%" + filter.ip().trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
        if (filter.pageId() != null)
        {
            sql.append(" AND v.page_id = ?");
            args.add(filter.pageId());
        }
        if (filter.device() != null && DEVICES.contains(filter.device()))
        {
            sql.append(" AND v.device_type = ?");
            args.add(filter.device());
        }
        return sql.toString();
    }

    private VisitRow row(ResultSet rs, int row) throws SQLException
    {
        return new VisitRow(rs.getLong(1), rs.getTimestamp(2).toInstant(), rs.getString(3),
            rs.getObject(4, UUID.class), rs.getString(5), null, rs.getString(6), rs.getBoolean(7), rs.getString(8));
    }

    private List<VisitRow> withTitles(List<VisitRow> rows)
    {
        Map<UUID, PageRef> pages = pageApi.findByIds(rows.stream().map(VisitRow::pageId).filter(Objects::nonNull)
            .collect(Collectors.toSet()));
        return rows.stream().map(row ->
        {
            PageRef page = row.pageId() == null ? null : pages.get(row.pageId());
            return new VisitRow(row.id(), row.occurredAt(), row.ip(), row.pageId(), row.pageSlug(),
                page == null ? row.pageSlug() : page.title(), row.deviceType(), row.bot(), row.referer());
        }).toList();
    }

    private static Double change(long current, long previous)
    {
        if (previous == 0)
        {
            return null;
        }
        return Math.round((current - previous) * 1000.0 / previous) / 10.0;
    }
}
