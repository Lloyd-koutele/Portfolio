package com.portfolio.media.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.portfolio.TestData;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ImageSanitizerTest
{
    private final ImageSanitizer sanitizer = new ImageSanitizer();

    @Test
    void readsPngDimensionsAndDropsTextChunks() throws IOException
    {
        byte[] png = withTextChunk(TestData.png(12, 7));

        ProcessedImage image = sanitizer.sanitize(png);

        assertThat(image.mimeType()).isEqualTo("image/png");
        assertThat(image.width()).isEqualTo(12);
        assertThat(image.height()).isEqualTo(7);
        assertThat(new String(image.content(), StandardCharsets.ISO_8859_1)).doesNotContain("Author");
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(image.content()));
        assertThat(decoded.getWidth()).isEqualTo(12);
    }

    @Test
    void readsJpegDimensions()
    {
        ProcessedImage image = sanitizer.sanitize(TestData.jpeg(33, 21));

        assertThat(image.extension()).isEqualTo("jpg");
        assertThat(image.width()).isEqualTo(33);
        assertThat(image.height()).isEqualTo(21);
    }

    @Test
    void readsGifDimensionsAndDropsComments() throws IOException
    {
        BufferedImage source = new BufferedImage(9, 5, BufferedImage.TYPE_BYTE_INDEXED);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(source, "gif", out);
        byte[] gif = out.toByteArray();
        byte[] withComment = insertGifComment(gif, "secret comment");

        ProcessedImage image = sanitizer.sanitize(withComment);

        assertThat(image.mimeType()).isEqualTo("image/gif");
        assertThat(image.width()).isEqualTo(9);
        assertThat(image.height()).isEqualTo(5);
        assertThat(new String(image.content(), StandardCharsets.ISO_8859_1)).doesNotContain("secret comment");
        assertThat(ImageIO.read(new ByteArrayInputStream(image.content()))).isNotNull();
    }

    @Test
    void readsLosslessWebpDimensionsAndDropsExif()
    {
        byte[] webp = webpLossless(300, 200, true);

        ProcessedImage image = sanitizer.sanitize(webp);

        assertThat(image.mimeType()).isEqualTo("image/webp");
        assertThat(image.width()).isEqualTo(300);
        assertThat(image.height()).isEqualTo(200);
        assertThat(new String(image.content(), StandardCharsets.ISO_8859_1)).doesNotContain("EXIF")
            .doesNotContain("GPS");
        int riffSize = (image.content()[4] & 0xFF) | ((image.content()[5] & 0xFF) << 8);
        assertThat(riffSize).isEqualTo(image.content().length - 8);
    }

    @Test
    void refusesNonImages()
    {
        assertThatThrownBy(() -> sanitizer.sanitize("%PDF-1.7 fake".getBytes(StandardCharsets.US_ASCII)))
            .isInstanceOf(InvalidImageException.class);
    }

    @Test
    void refusesTruncatedImages()
    {
        byte[] png = TestData.png(10, 10);
        byte[] truncated = java.util.Arrays.copyOf(png, 40);

        assertThatThrownBy(() -> sanitizer.sanitize(truncated)).isInstanceOf(InvalidImageException.class);
    }

    @Test
    void rotatesJpegAccordingToExifOrientation()
    {
        byte[] jpeg = withOrientation(TestData.jpeg(40, 20), 6);

        ProcessedImage image = sanitizer.sanitize(jpeg);

        assertThat(image.width()).isEqualTo(20);
        assertThat(image.height()).isEqualTo(40);
    }

    // ---------------------------------------------------------------- builders

    private static byte[] withTextChunk(byte[] png)
    {
        byte[] data = "Author\0Jane Doe".getBytes(StandardCharsets.ISO_8859_1);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(png, 0, 33);
        writeInt(out, data.length);
        out.writeBytes("tEXt".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(data);
        writeInt(out, 0);
        out.write(png, 33, png.length - 33);
        return out.toByteArray();
    }

    private static byte[] insertGifComment(byte[] gif, String comment)
    {
        int flags = gif[10] & 0xFF;
        int headerEnd = 13 + ((flags & 0x80) != 0 ? 3 * (1 << ((flags & 0x07) + 1)) : 0);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(gif, 0, headerEnd);
        out.write(0x21);
        out.write(0xFE);
        byte[] text = comment.getBytes(StandardCharsets.US_ASCII);
        out.write(text.length);
        out.writeBytes(text);
        out.write(0);
        out.write(gif, headerEnd, gif.length - headerEnd);
        return out.toByteArray();
    }

    private static byte[] webpLossless(int width, int height, boolean withExif)
    {
        ByteArrayOutputStream chunks = new ByteArrayOutputStream();
        if (withExif)
        {
            byte[] vp8x = new byte[10];
            vp8x[0] = 0x08;
            write24(vp8x, 4, width - 1);
            write24(vp8x, 7, height - 1);
            chunk(chunks, "VP8X", vp8x);
        }
        byte[] vp8l = new byte[10];
        vp8l[0] = 0x2F;
        int bits = (width - 1) | ((height - 1) << 14);
        vp8l[1] = (byte) bits;
        vp8l[2] = (byte) (bits >> 8);
        vp8l[3] = (byte) (bits >> 16);
        vp8l[4] = (byte) (bits >> 24);
        chunk(chunks, "VP8L", vp8l);
        if (withExif)
        {
            chunk(chunks, "EXIF", "GPS 48.85 2.35".getBytes(StandardCharsets.US_ASCII));
        }
        byte[] body = chunks.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("RIFF".getBytes(StandardCharsets.US_ASCII));
        writeIntLe(out, body.length + 4);
        out.writeBytes("WEBP".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(body);
        return out.toByteArray();
    }

    private static byte[] withOrientation(byte[] jpeg, int orientation)
    {
        ByteArrayOutputStream tiff = new ByteArrayOutputStream();
        tiff.writeBytes("Exif\0\0".getBytes(StandardCharsets.ISO_8859_1));
        tiff.writeBytes(new byte[] {'M', 'M', 0, 42, 0, 0, 0, 8, 0, 1, 0x01, 0x12, 0, 3, 0, 0, 0, 1, 0,
            (byte) orientation, 0, 0, 0, 0, 0, 0, 0, 0});
        byte[] payload = tiff.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);
        out.write(0xFF);
        out.write(0xE1);
        out.write(((payload.length + 2) >> 8) & 0xFF);
        out.write((payload.length + 2) & 0xFF);
        out.writeBytes(payload);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    private static void chunk(ByteArrayOutputStream out, String fourcc, byte[] data)
    {
        out.writeBytes(fourcc.getBytes(StandardCharsets.US_ASCII));
        writeIntLe(out, data.length);
        out.writeBytes(data);
        if ((data.length & 1) == 1)
        {
            out.write(0);
        }
    }

    private static void write24(byte[] target, int offset, int value)
    {
        target[offset] = (byte) value;
        target[offset + 1] = (byte) (value >> 8);
        target[offset + 2] = (byte) (value >> 16);
    }

    private static void writeInt(ByteArrayOutputStream out, int value)
    {
        out.write((value >> 24) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static void writeIntLe(ByteArrayOutputStream out, int value)
    {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 24) & 0xFF);
    }
}
