package com.portfolio.media.internal;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

/**
 * Detects the real image type from its bytes (Apache Tika, never the file name), reads the dimensions from the
 * headers and removes metadata (EXIF, XMP, IPTC, comments) without re-encoding. A JPEG that relies on an EXIF
 * orientation is rotated first, since removing the tag would otherwise show it sideways.
 */
@Component
class ImageSanitizer
{
    static final Map<String, String> EXTENSIONS = Map.of(
        "image/png", "png",
        "image/jpeg", "jpg",
        "image/webp", "webp",
        "image/gif", "gif");

    private static final int MAX_DIMENSION = 12_000;
    private static final long MAX_PIXELS_TO_DECODE = 40_000_000L;

    private final Tika tika = new Tika();

    ProcessedImage sanitize(byte[] content)
    {
        String mimeType = tika.detect(content);
        String extension = EXTENSIONS.get(mimeType);
        if (extension == null)
        {
            throw new InvalidImageException("Only PNG, JPEG, WebP and GIF images are accepted");
        }
        try
        {
            ProcessedImage image = switch (extension)
            {
                case "png" -> png(content);
                case "jpg" -> jpeg(content);
                case "webp" -> webp(content);
                case "gif" -> gif(content);
                default -> throw new InvalidImageException("Unsupported image type");
            };
            if (image.width() < 1 || image.height() < 1 || image.width() > MAX_DIMENSION
                || image.height() > MAX_DIMENSION)
            {
                throw new InvalidImageException("Image dimensions are out of bounds");
            }
            return image;
        }
        catch (IndexOutOfBoundsException | IOException exception)
        {
            throw new InvalidImageException("Corrupted image");
        }
    }

    // ---------------------------------------------------------------- PNG

    private static ProcessedImage png(byte[] in)
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
        out.write(in, 0, 8);
        int width = 0;
        int height = 0;
        int pos = 8;
        boolean ended = false;
        while (pos + 12 <= in.length && !ended)
        {
            int length = readInt(in, pos);
            String type = new String(in, pos + 4, 4, StandardCharsets.US_ASCII);
            int total = 12 + length;
            if (length < 0 || pos + total > in.length)
            {
                throw new InvalidImageException("Corrupted PNG");
            }
            if ("IHDR".equals(type))
            {
                width = readInt(in, pos + 8);
                height = readInt(in, pos + 12);
            }
            if (!isPngMetadata(type))
            {
                out.write(in, pos, total);
            }
            ended = "IEND".equals(type);
            pos += total;
        }
        if (!ended || width == 0)
        {
            throw new InvalidImageException("Corrupted PNG");
        }
        return new ProcessedImage("image/png", "png", width, height, out.toByteArray());
    }

    private static boolean isPngMetadata(String type)
    {
        return switch (type)
        {
            case "eXIf", "tEXt", "zTXt", "iTXt", "tIME" -> true;
            default -> false;
        };
    }

    // ---------------------------------------------------------------- JPEG

    private static ProcessedImage jpeg(byte[] in) throws IOException
    {
        if ((in[0] & 0xFF) != 0xFF || (in[1] & 0xFF) != 0xD8)
        {
            throw new InvalidImageException("Corrupted JPEG");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
        out.write(in, 0, 2);
        int width = 0;
        int height = 0;
        int orientation = 1;
        int pos = 2;
        while (pos + 4 <= in.length)
        {
            if ((in[pos] & 0xFF) != 0xFF)
            {
                throw new InvalidImageException("Corrupted JPEG");
            }
            int marker = in[pos + 1] & 0xFF;
            if (marker == 0xFF)
            {
                pos++;
                continue;
            }
            if (marker == 0xD9 || (marker >= 0xD0 && marker <= 0xD7) || marker == 0x01)
            {
                out.write(in, pos, 2);
                pos += 2;
                continue;
            }
            int length = readShort(in, pos + 2);
            int total = 2 + length;
            if (length < 2 || pos + total > in.length)
            {
                throw new InvalidImageException("Corrupted JPEG");
            }
            if (isStartOfFrame(marker))
            {
                height = readShort(in, pos + 5);
                width = readShort(in, pos + 7);
            }
            if (marker == 0xE1)
            {
                orientation = Math.max(orientation, exifOrientation(in, pos + 4, length - 2));
            }
            if (marker == 0xDA)
            {
                out.write(in, pos, in.length - pos);
                break;
            }
            if (!isJpegMetadata(marker))
            {
                out.write(in, pos, total);
            }
            pos += total;
        }
        if (width == 0 || height == 0)
        {
            throw new InvalidImageException("Corrupted JPEG");
        }
        byte[] stripped = out.toByteArray();
        if (orientation > 1 && orientation <= 8 && (long) width * height <= MAX_PIXELS_TO_DECODE)
        {
            ProcessedImage rotated = rotateJpeg(stripped, orientation);
            if (rotated != null)
            {
                return rotated;
            }
        }
        return new ProcessedImage("image/jpeg", "jpg", width, height, stripped);
    }

    private static boolean isStartOfFrame(int marker)
    {
        return marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
    }

    /** Keeps JFIF (APP0), ICC profile (APP2) and Adobe (APP14); drops EXIF/XMP, IPTC, other APPn and comments. */
    private static boolean isJpegMetadata(int marker)
    {
        if (marker == 0xFE)
        {
            return true;
        }
        return marker >= 0xE1 && marker <= 0xEF && marker != 0xE2 && marker != 0xEE;
    }

    private static int exifOrientation(byte[] in, int start, int length)
    {
        if (length < 14 || !"Exif".equals(new String(in, start, 4, StandardCharsets.US_ASCII)))
        {
            return 1;
        }
        int tiff = start + 6;
        boolean little = in[tiff] == 'I';
        int ifd = tiff + read32(in, tiff + 4, little);
        int end = start + length;
        if (ifd + 2 > end)
        {
            return 1;
        }
        int entries = read16(in, ifd, little);
        for (int index = 0; index < entries; index++)
        {
            int entry = ifd + 2 + index * 12;
            if (entry + 12 > end)
            {
                return 1;
            }
            if (read16(in, entry, little) == 0x0112)
            {
                return read16(in, entry + 8, little);
            }
        }
        return 1;
    }

    private static ProcessedImage rotateJpeg(byte[] content, int orientation) throws IOException
    {
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(content));
        if (source == null || source.getColorModel().hasAlpha())
        {
            return null;
        }
        int w = source.getWidth();
        int h = source.getHeight();
        boolean swap = orientation >= 5;
        BufferedImage target = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_RGB);
        AffineTransform transform = new AffineTransform();
        switch (orientation)
        {
            case 2 -> transform.scale(-1, 1);
            case 3 -> transform.rotate(Math.PI);
            case 4 -> transform.scale(1, -1);
            case 5 ->
            {
                transform.rotate(Math.PI / 2);
                transform.scale(1, -1);
            }
            case 6 -> transform.rotate(Math.PI / 2);
            case 7 ->
            {
                transform.rotate(-Math.PI / 2);
                transform.scale(1, -1);
            }
            case 8 -> transform.rotate(-Math.PI / 2);
            default ->
            {
                return null;
            }
        }
        AffineTransform centred = new AffineTransform();
        centred.translate(target.getWidth() / 2.0, target.getHeight() / 2.0);
        centred.concatenate(transform);
        centred.translate(-w / 2.0, -h / 2.0);
        Graphics2D graphics = target.createGraphics();
        graphics.drawImage(source, centred, null);
        graphics.dispose();

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext())
        {
            return null;
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out))
        {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.92f);
            writer.write(null, new IIOImage(target, null, null), param);
        }
        finally
        {
            writer.dispose();
        }
        return new ProcessedImage("image/jpeg", "jpg", target.getWidth(), target.getHeight(), out.toByteArray());
    }

    // ---------------------------------------------------------------- WebP

    private static ProcessedImage webp(byte[] in)
    {
        if (in.length < 20 || !"RIFF".equals(ascii(in, 0)) || !"WEBP".equals(ascii(in, 8)))
        {
            throw new InvalidImageException("Corrupted WebP");
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream(in.length);
        int width = 0;
        int height = 0;
        int pos = 12;
        while (pos + 8 <= in.length)
        {
            String fourcc = ascii(in, pos);
            int size = readIntLe(in, pos + 4);
            int padded = size + (size & 1);
            if (size < 0 || pos + 8 + size > in.length)
            {
                throw new InvalidImageException("Corrupted WebP");
            }
            int data = pos + 8;
            switch (fourcc)
            {
                case "VP8X" ->
                {
                    width = 1 + read24Le(in, data + 4);
                    height = 1 + read24Le(in, data + 7);
                    byte[] chunk = java.util.Arrays.copyOfRange(in, pos, Math.min(in.length, data + padded));
                    chunk[8] = (byte) (chunk[8] & ~0x0C);
                    body.write(chunk, 0, chunk.length);
                }
                case "VP8 " ->
                {
                    if (width == 0)
                    {
                        width = readShortLe(in, data + 6) & 0x3FFF;
                        height = readShortLe(in, data + 8) & 0x3FFF;
                    }
                    body.write(in, pos, Math.min(8 + padded, in.length - pos));
                }
                case "VP8L" ->
                {
                    if (width == 0)
                    {
                        int bits = readIntLe(in, data + 1);
                        width = 1 + (bits & 0x3FFF);
                        height = 1 + ((bits >> 14) & 0x3FFF);
                    }
                    body.write(in, pos, Math.min(8 + padded, in.length - pos));
                }
                case "EXIF", "XMP " ->
                {
                    // Metadata: dropped.
                }
                default -> body.write(in, pos, Math.min(8 + padded, in.length - pos));
            }
            pos += 8 + padded;
        }
        byte[] chunks = body.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream(chunks.length + 12);
        out.writeBytes("RIFF".getBytes(StandardCharsets.US_ASCII));
        int riffSize = chunks.length + 4;
        out.write(riffSize & 0xFF);
        out.write((riffSize >> 8) & 0xFF);
        out.write((riffSize >> 16) & 0xFF);
        out.write((riffSize >> 24) & 0xFF);
        out.writeBytes("WEBP".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(chunks);
        if (width == 0)
        {
            throw new InvalidImageException("Corrupted WebP");
        }
        return new ProcessedImage("image/webp", "webp", width, height, out.toByteArray());
    }

    // ---------------------------------------------------------------- GIF

    private static ProcessedImage gif(byte[] in)
    {
        int width = readShortLe(in, 6);
        int height = readShortLe(in, 8);
        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
        int pos = 13;
        int flags = in[10] & 0xFF;
        if ((flags & 0x80) != 0)
        {
            pos += 3 * (1 << ((flags & 0x07) + 1));
        }
        out.write(in, 0, pos);
        while (pos < in.length)
        {
            int block = in[pos] & 0xFF;
            if (block == 0x3B)
            {
                out.write(0x3B);
                return new ProcessedImage("image/gif", "gif", width, height, out.toByteArray());
            }
            int start = pos;
            if (block == 0x2C)
            {
                int localFlags = in[pos + 9] & 0xFF;
                pos += 10;
                if ((localFlags & 0x80) != 0)
                {
                    pos += 3 * (1 << ((localFlags & 0x07) + 1));
                }
                pos += 1;
                pos = skipSubBlocks(in, pos);
                out.write(in, start, pos - start);
            }
            else if (block == 0x21)
            {
                int label = in[pos + 1] & 0xFF;
                boolean drop = label == 0xFE
                    || (label == 0xFF && in[pos + 2] == 11 && ascii(in, pos + 3, 11).startsWith("XMP"));
                pos = skipSubBlocks(in, pos + 2);
                if (!drop)
                {
                    out.write(in, start, pos - start);
                }
            }
            else
            {
                throw new InvalidImageException("Corrupted GIF");
            }
        }
        throw new InvalidImageException("Corrupted GIF");
    }

    private static int skipSubBlocks(byte[] in, int pos)
    {
        int cursor = pos;
        while (true)
        {
            int size = in[cursor] & 0xFF;
            cursor += 1 + size;
            if (size == 0)
            {
                return cursor;
            }
        }
    }

    // ---------------------------------------------------------------- byte helpers

    private static String ascii(byte[] in, int pos)
    {
        return ascii(in, pos, 4);
    }

    private static String ascii(byte[] in, int pos, int length)
    {
        return new String(in, pos, length, StandardCharsets.US_ASCII);
    }

    private static int readInt(byte[] in, int pos)
    {
        return ((in[pos] & 0xFF) << 24) | ((in[pos + 1] & 0xFF) << 16) | ((in[pos + 2] & 0xFF) << 8)
            | (in[pos + 3] & 0xFF);
    }

    private static int readShort(byte[] in, int pos)
    {
        return ((in[pos] & 0xFF) << 8) | (in[pos + 1] & 0xFF);
    }

    private static int readIntLe(byte[] in, int pos)
    {
        return (in[pos] & 0xFF) | ((in[pos + 1] & 0xFF) << 8) | ((in[pos + 2] & 0xFF) << 16)
            | ((in[pos + 3] & 0xFF) << 24);
    }

    private static int read24Le(byte[] in, int pos)
    {
        return (in[pos] & 0xFF) | ((in[pos + 1] & 0xFF) << 8) | ((in[pos + 2] & 0xFF) << 16);
    }

    private static int readShortLe(byte[] in, int pos)
    {
        return (in[pos] & 0xFF) | ((in[pos + 1] & 0xFF) << 8);
    }

    private static int read16(byte[] in, int pos, boolean little)
    {
        return little ? readShortLe(in, pos) : readShort(in, pos);
    }

    private static int read32(byte[] in, int pos, boolean little)
    {
        return little ? readIntLe(in, pos) : readInt(in, pos);
    }
}
