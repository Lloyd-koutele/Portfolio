package com.portfolio;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Database reset and sample files shared by integration tests.
 */
public final class TestData
{
    private static final String EMPTY_PAGE =
        "{\"schemaVersion\":1,\"background\":{\"type\":\"color\",\"value\":\"#ffffff\"},\"elements\":[]}";

    private TestData()
    {
    }

    public static void reset(JdbcTemplate jdbc)
    {
        jdbc.update("DELETE FROM visit");
        jdbc.update("DELETE FROM download");
        jdbc.update("DELETE FROM cv_document");
        jdbc.update("DELETE FROM book_edition");
        jdbc.update("DELETE FROM theme_snapshot");
        jdbc.update("DELETE FROM page");
        jdbc.update("DELETE FROM project");
        jdbc.update("""
            UPDATE site_settings SET background_media_id = NULL, ip_mode = 'MASKED', title = 'Portfolio',
                page_width = 800, page_height = 1000, background_color = '#1f1a17', privacy_text = ''
            """);
        jdbc.update("DELETE FROM media");
    }

    /** Smallest MP4 the server recognises: an "ftyp" box (ISO base media) followed by some data. */
    public static byte[] mp4(int dataBytes)
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 2, 0,
            'i', 's', 'o', 'm', 'i', 's', 'o', '2'});
        int size = dataBytes + 8;
        out.writeBytes(new byte[] {(byte) (size >> 24), (byte) (size >> 16), (byte) (size >> 8), (byte) size,
            'm', 'd', 'a', 't'});
        out.writeBytes(new byte[dataBytes]);
        return out.toByteArray();
    }

    /** Smallest WebM the server recognises: an EBML header whose DocType is "webm". */
    public static byte[] webm()
    {
        return new byte[] {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, (byte) 0x9F, 0x42, (byte) 0x86, (byte) 0x81, 0x01,
            0x42, (byte) 0xF7, (byte) 0x81, 0x01, 0x42, (byte) 0xF2, (byte) 0x81, 0x04, 0x42, (byte) 0xF3,
            (byte) 0x81, 0x08, 0x42, (byte) 0x82, (byte) 0x84, 'w', 'e', 'b', 'm', 0x42, (byte) 0x87, (byte) 0x81,
            0x04, 0x42, (byte) 0x85, (byte) 0x81, 0x02, 0x18, 0x53, (byte) 0x80, 0x67, 0x01, 0, 0, 0, 0, 0, 0, 0};
    }

    public static byte[] png(int width, int height)
    {
        return encode(width, height, "png");
    }

    public static byte[] jpeg(int width, int height)
    {
        return encode(width, height, "jpg");
    }

    /** A JPEG with an EXIF segment (APP1) carrying a GPS-looking marker right after SOI. */
    public static byte[] jpegWithExif(int width, int height)
    {
        byte[] plain = jpeg(width, height);
        byte[] payload = "Exif\0\0MM\0*\0\0\0\u0008\0\0GPS-SECRET-LOCATION".getBytes(StandardCharsets.ISO_8859_1);
        int length = payload.length + 2;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(plain, 0, 2);
        out.write(0xFF);
        out.write(0xE1);
        out.write((length >> 8) & 0xFF);
        out.write(length & 0xFF);
        out.writeBytes(payload);
        out.write(plain, 2, plain.length - 2);
        return out.toByteArray();
    }

    /** Inserts a project with a single sheet and publishes the book; returns the sheet id. */
    public static String insertPublishedPage(JdbcTemplate jdbc, String slug, int position)
    {
        UUID project = UUID.randomUUID();
        jdbc.update("INSERT INTO project (id, title, position) VALUES (?, ?, ?)", project, "Projet " + slug, position);
        String id = insertSheet(jdbc, project, slug, 0);
        publishBook(jdbc);
        return id;
    }

    /** Inserts a sheet of an existing project (working copy only); returns its id. */
    public static String insertSheet(JdbcTemplate jdbc, UUID project, String slug, int position)
    {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO page (id, slug, title, position, content, version, kind, project_id)
            VALUES (?, ?, ?, ?, ?::jsonb, 0, 'PROJECT_SHEET', ?)
            """, id, slug, "Titre " + slug, position, EMPTY_PAGE, project);
        return id.toString();
    }

    /** Inserts the three fixed pages (cover, table of contents, contact) in the working copy. */
    public static void insertFixedPages(JdbcTemplate jdbc)
    {
        for (String kind : new String[] {"COVER", "TABLE_OF_CONTENTS", "CONTACT"})
        {
            String slug = kind.toLowerCase(java.util.Locale.ROOT).replace('_', '-');
            jdbc.update("""
                INSERT INTO page (id, slug, title, position, content, version, kind)
                VALUES (?, ?, ?, 0, ?::jsonb, 0, ?)
                """, UUID.randomUUID(), slug, kind, EMPTY_PAGE, kind);
        }
    }

    /** Same as the "Publier le site" button: the working copy becomes the edition readers see. */
    public static void publishBook(JdbcTemplate jdbc)
    {
        jdbc.update("""
            INSERT INTO book_edition (pages)
            SELECT coalesce(jsonb_agg(jsonb_build_object(
                       'id', p.id, 'slug', p.slug, 'title', p.title, 'kind', p.kind,
                       'projectId', p.project_id, 'projectTitle', pr.title, 'content', p.content)
                   ORDER BY CASE p.kind WHEN 'COVER' THEN 0 WHEN 'TABLE_OF_CONTENTS' THEN 1
                                        WHEN 'PROJECT_SHEET' THEN 2 ELSE 3 END,
                            pr.position NULLS FIRST, p.position, p.id), '[]'::jsonb)
            FROM page p LEFT JOIN project pr ON pr.id = p.project_id
            """);
    }

    private static byte[] encode(int width, int height, String format)
    {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(0xEE, 0x7D, 0x1F));
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(Color.BLACK);
        graphics.fillRect(0, 0, width / 2, height / 2);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try
        {
            ImageIO.write(image, format, out);
        }
        catch (IOException exception)
        {
            throw new UncheckedIOException(exception);
        }
        return out.toByteArray();
    }
}
