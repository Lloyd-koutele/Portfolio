package com.portfolio.profile.internal;

import com.portfolio.common.ApiException;
import com.portfolio.media.FileStorage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * The CV: one PDF at most, replaced by each upload. The file is stored under a generated name; the name chosen
 * by the administrator is only used as the download name.
 */
@Service
class CvService
{
    static final long MAX_BYTES = 10L * 1024 * 1024;
    static final String DEFAULT_NAME = "CV.pdf";
    private static final int MAX_NAME = 120;
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final Logger LOG = LoggerFactory.getLogger(CvService.class);

    private final JdbcTemplate jdbc;
    private final FileStorage storage;
    private final Clock clock;

    CvService(JdbcTemplate jdbc, FileStorage storage, Clock clock)
    {
        this.jdbc = jdbc;
        this.storage = storage;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    CvInfo info()
    {
        return current().map(Stored::info).orElse(CvInfo.none());
    }

    /** The file to send, with its download name; empty when no CV was uploaded or its file is missing. */
    @Transactional(readOnly = true)
    Optional<Download> file()
    {
        return current().flatMap(stored -> storage.resource(stored.storedName())
            .map(resource -> new Download(resource, stored.fileName(), stored.sizeBytes())));
    }

    @Transactional
    CvInfo upload(MultipartFile file) throws IOException
    {
        if (file == null || file.isEmpty())
        {
            throw new InvalidCvException("The file is empty");
        }
        if (file.getSize() > MAX_BYTES)
        {
            throw new InvalidCvException("The CV must not exceed 10 MB");
        }
        try (InputStream in = file.getInputStream())
        {
            if (!Arrays.equals(in.readNBytes(PDF_MAGIC.length), PDF_MAGIC))
            {
                throw new InvalidCvException("The CV must be a PDF file");
            }
        }
        Optional<Stored> previous = current();
        String storedName = UUID.randomUUID() + ".pdf";
        try (InputStream in = file.getInputStream())
        {
            storage.store(storedName, in);
        }
        String fileName = safeName(file.getOriginalFilename());
        Instant now = clock.instant();
        jdbc.update("""
            INSERT INTO cv_document (id, stored_name, file_name, size_bytes, uploaded_at) VALUES (1, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET stored_name = EXCLUDED.stored_name, file_name = EXCLUDED.file_name,
                size_bytes = EXCLUDED.size_bytes, uploaded_at = EXCLUDED.uploaded_at
            """, storedName, fileName, file.getSize(), Timestamp.from(now));
        previous.ifPresent(stored -> deleteQuietly(stored.storedName()));
        return new CvInfo(true, fileName, file.getSize(), now);
    }

    @Transactional
    void delete()
    {
        Optional<Stored> previous = current();
        jdbc.update("DELETE FROM cv_document");
        previous.ifPresent(stored -> deleteQuietly(stored.storedName()));
    }

    /**
     * Keeps the last path segment, letters, digits, spaces and {@code . _ ( ) -}, and ends with {@code .pdf}.
     */
    static String safeName(String original)
    {
        String name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("[^\\p{L}\\p{N} ._()-]", "").replaceAll("\\s+", " ").trim();
        if (name.toLowerCase(Locale.ROOT).endsWith(".pdf"))
        {
            name = name.substring(0, name.length() - 4).trim();
        }
        name = name.replaceAll("^[.\\s]+", "");
        if (name.isEmpty())
        {
            return DEFAULT_NAME;
        }
        if (name.length() > MAX_NAME)
        {
            name = name.substring(0, MAX_NAME).trim();
        }
        return name + ".pdf";
    }

    private Optional<Stored> current()
    {
        List<Stored> rows = jdbc.query(
            "SELECT stored_name, file_name, size_bytes, uploaded_at FROM cv_document WHERE id = 1",
            (rs, index) -> new Stored(rs.getString(1), rs.getString(2), rs.getLong(3),
                rs.getTimestamp(4).toInstant()));
        return rows.stream().findFirst();
    }

    private void deleteQuietly(String storedName)
    {
        try
        {
            storage.delete(storedName);
        }
        catch (IOException exception)
        {
            LOG.warn("Could not delete the previous CV file {}", storedName, exception);
        }
    }

    record Download(Resource resource, String fileName, long sizeBytes)
    {
    }

    private record Stored(String storedName, String fileName, long sizeBytes, Instant uploadedAt)
    {
        CvInfo info()
        {
            return new CvInfo(true, fileName, sizeBytes, uploadedAt);
        }
    }

    static class InvalidCvException extends ApiException
    {
        InvalidCvException(String detail)
        {
            super(HttpStatus.BAD_REQUEST, "Invalid CV", detail);
        }
    }
}
