package com.portfolio.stats.internal;

import com.portfolio.stats.internal.StatsDtos.DayPoint;
import com.portfolio.stats.internal.StatsDtos.DownloadPage;
import com.portfolio.stats.internal.StatsDtos.PageCount;
import com.portfolio.stats.internal.StatsDtos.Summary;
import com.portfolio.stats.internal.StatsDtos.VisitFilter;
import com.portfolio.stats.internal.StatsDtos.VisitPage;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/stats")
class StatsController
{
    static final int DEFAULT_PAGE_SIZE = 50;
    static final int MAX_PAGE_SIZE = 200;

    private final StatsQueries queries;
    private final Clock clock;

    StatsController(StatsQueries queries, Clock clock)
    {
        this.queries = queries;
        this.clock = clock;
    }

    @GetMapping("/summary")
    Summary summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to)
    {
        return queries.summary(Period.of(from, to, clock));
    }

    @GetMapping("/timeseries")
    List<DayPoint> timeseries(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to)
    {
        return queries.timeseries(Period.of(from, to, clock));
    }

    @GetMapping("/pages")
    List<PageCount> pages(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to)
    {
        return queries.pages(Period.of(from, to, clock));
    }

    @GetMapping("/visits")
    VisitPage visits(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) String ip,
        @RequestParam(required = false) UUID pageId,
        @RequestParam(required = false) String device,
        @RequestParam(defaultValue = "false") boolean includeBots,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size)
    {
        VisitFilter filter = new VisitFilter(Period.of(from, to, clock), ip, pageId, device, includeBots);
        return queries.visits(filter, Math.max(0, page), Math.min(MAX_PAGE_SIZE, Math.max(1, size)));
    }

    /** The download log (CV). */
    @GetMapping("/downloads")
    DownloadPage downloads(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(defaultValue = "false") boolean includeBots,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size)
    {
        return queries.downloadLog(Period.of(from, to, clock), includeBots, Math.max(0, page),
            Math.min(MAX_PAGE_SIZE, Math.max(1, size)));
    }

    @GetMapping("/visits.csv")
    void visitsCsv(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) String ip,
        @RequestParam(required = false) UUID pageId,
        @RequestParam(required = false) String device,
        @RequestParam(defaultValue = "false") boolean includeBots,
        HttpServletResponse response) throws IOException
    {
        VisitFilter filter = new VisitFilter(Period.of(from, to, clock), ip, pageId, device, includeBots);
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"visites.csv\"");
        Writer writer = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8);
        writer.write('﻿');
        writer.write(CsvWriter.HEADER + "\r\n");
        queries.forEachVisit(filter, row ->
        {
            try
            {
                CsvWriter.writeRow(writer, row);
            }
            catch (IOException exception)
            {
                throw new UncheckedIOException(exception);
            }
        });
        writer.flush();
    }
}
