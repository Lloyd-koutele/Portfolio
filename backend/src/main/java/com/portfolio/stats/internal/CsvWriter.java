package com.portfolio.stats.internal;

import com.portfolio.stats.internal.StatsDtos.VisitRow;
import java.io.IOException;
import java.io.Writer;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * RFC 4180 CSV with protection against spreadsheet formula injection.
 */
final class CsvWriter
{
    static final String HEADER = "date;ip;page;appareil;robot;provenance";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneOffset.UTC);

    private CsvWriter()
    {
    }

    static void writeRow(Writer writer, VisitRow row) throws IOException
    {
        writer.write(String.join(";",
            DATE.format(row.occurredAt()),
            cell(row.ip()),
            cell(row.pageTitle()),
            cell(row.deviceType()),
            row.bot() ? "oui" : "non",
            cell(row.referer())));
        writer.write("\r\n");
    }

    static String cell(String value)
    {
        if (value == null)
        {
            return "";
        }
        String safe = value;
        if (!safe.isEmpty() && "=+-@\t\r".indexOf(safe.charAt(0)) >= 0)
        {
            safe = "'" + safe;
        }
        if (safe.contains(";") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r"))
        {
            safe = "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }
}
