package com.portfolio.page.internal;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Random;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * Original abstract illustrations for the demo pages, drawn with Java2D (no third-party assets), in the
 * orange-on-ink palette of the book.
 */
final class DemoImages
{
    private static final Color INK = new Color(0x11, 0x11, 0x11);
    private static final Color ORANGE = new Color(0xEE, 0x7D, 0x1F);

    private DemoImages()
    {
    }

    /** Warm dusk with soft bokeh lights; used as the blurred backdrop behind the book. */
    static byte[] backdrop()
    {
        return draw(1600, 1000, 7, (g, w, h, random) ->
        {
            g.setPaint(new GradientPaint(0, 0, new Color(0x6E, 0x5A, 0x63), 0, h * 0.62f, new Color(0xE8, 0x9B, 0x72)));
            g.fillRect(0, 0, w, h);
            g.setPaint(new GradientPaint(0, h * 0.6f, new Color(0x3A, 0x2C, 0x2A), 0, h, new Color(0x1A, 0x14, 0x14)));
            g.fill(new Rectangle2D.Double(0, h * 0.6, w, h * 0.4));
            for (int index = 0; index < 60; index++)
            {
                double size = 20 + random.nextDouble() * 70;
                double x = random.nextDouble() * w;
                double y = h * 0.55 + random.nextDouble() * h * 0.4;
                Color base = random.nextBoolean() ? new Color(255, 170, 90) : new Color(255, 220, 170);
                g.setPaint(new RadialGradientPaint(new Point2D.Double(x, y), (float) size,
                    new float[] {0f, 0.7f, 1f},
                    new Color[] {withAlpha(base, 200), withAlpha(base, 90), withAlpha(base, 0)}));
                g.fill(new Ellipse2D.Double(x - size, y - size, size * 2, size * 2));
            }
        });
    }

    /** Concentric machined rings, like a turbine seen from the front. */
    static byte[] workshop()
    {
        return draw(880, 1080, 11, (g, w, h, random) ->
        {
            g.setPaint(new GradientPaint(0, 0, new Color(0x2B, 0x22, 0x1C), w, h, new Color(0x0D, 0x0B, 0x0A)));
            g.fillRect(0, 0, w, h);
            double cx = w * 0.55;
            double cy = h * 0.52;
            for (int ring = 14; ring >= 1; ring--)
            {
                double radius = ring * 34;
                float shade = 0.25f + 0.05f * (ring % 4);
                g.setColor(ring % 3 == 0 ? withAlpha(ORANGE, 230) : new Color(shade, shade, shade * 0.95f));
                g.setStroke(new BasicStroke(ring % 3 == 0 ? 9f : 4f));
                g.draw(new Ellipse2D.Double(cx - radius, cy - radius, radius * 2, radius * 2));
            }
            g.setStroke(new BasicStroke(3f));
            for (int spoke = 0; spoke < 36; spoke++)
            {
                double angle = Math.PI * 2 * spoke / 36;
                g.setColor(spoke % 6 == 0 ? ORANGE : new Color(90, 85, 80));
                g.draw(new Line2D.Double(cx + Math.cos(angle) * 70, cy + Math.sin(angle) * 70,
                    cx + Math.cos(angle) * 480, cy + Math.sin(angle) * 480));
            }
            g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), 120f, new float[] {0f, 1f},
                new Color[] {new Color(255, 190, 120), withAlpha(ORANGE, 0)}));
            g.fill(new Ellipse2D.Double(cx - 120, cy - 120, 240, 240));
        });
    }

    /** Technical drawing on a dark grid. */
    static byte[] blueprint()
    {
        return draw(1600, 760, 23, (g, w, h, random) ->
        {
            g.setColor(new Color(0x16, 0x18, 0x1C));
            g.fillRect(0, 0, w, h);
            g.setStroke(new BasicStroke(1f));
            g.setColor(new Color(255, 255, 255, 22));
            for (int x = 0; x < w; x += 40)
            {
                g.draw(new Line2D.Double(x, 0, x, h));
            }
            for (int y = 0; y < h; y += 40)
            {
                g.draw(new Line2D.Double(0, y, w, y));
            }
            g.setStroke(new BasicStroke(3f));
            for (int index = 0; index < 9; index++)
            {
                g.setColor(index % 3 == 0 ? ORANGE : new Color(220, 220, 220, 150));
                double x = 80 + random.nextDouble() * (w - 400);
                double y = 60 + random.nextDouble() * (h - 300);
                if (index % 2 == 0)
                {
                    double size = 80 + random.nextDouble() * 200;
                    g.draw(new Ellipse2D.Double(x, y, size, size));
                    g.draw(new Line2D.Double(x - 30, y + size / 2, x + size + 30, y + size / 2));
                    g.draw(new Line2D.Double(x + size / 2, y - 30, x + size / 2, y + size + 30));
                }
                else
                {
                    double width = 120 + random.nextDouble() * 260;
                    double height = 60 + random.nextDouble() * 160;
                    g.draw(new Rectangle2D.Double(x, y, width, height));
                }
            }
        });
    }

    /** Circuit traces converging to a glowing core. */
    static byte[] circuit()
    {
        return draw(1400, 660, 5, (g, w, h, random) ->
        {
            g.setPaint(new GradientPaint(0, 0, INK, w, h, new Color(0x2A, 0x1B, 0x10)));
            g.fillRect(0, 0, w, h);
            g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int index = 0; index < 70; index++)
            {
                double x = random.nextDouble() * w;
                double y = random.nextDouble() * h;
                double length = 60 + random.nextDouble() * 220;
                boolean horizontal = random.nextBoolean();
                g.setColor(index % 4 == 0 ? ORANGE : new Color(120, 100, 85, 170));
                double x2 = horizontal ? x + length : x;
                double y2 = horizontal ? y : y + length;
                g.draw(new Line2D.Double(x, y, x2, y2));
                g.draw(new Line2D.Double(x2, y2, x2 + 30, y2 + 30));
                g.fill(new Ellipse2D.Double(x2 + 24, y2 + 24, 14, 14));
            }
            g.setPaint(new RadialGradientPaint(new Point2D.Double(w * 0.62, h * 0.5), 260f, new float[] {0f, 1f},
                new Color[] {withAlpha(new Color(255, 160, 80), 200), withAlpha(ORANGE, 0)}));
            g.fill(new Ellipse2D.Double(w * 0.62 - 260, h * 0.5 - 260, 520, 520));
        });
    }

    /** Portrait-format abstract: stacked orange and ink planes. */
    static byte[] portrait()
    {
        return draw(640, 880, 3, (g, w, h, random) ->
        {
            g.setPaint(new GradientPaint(0, 0, new Color(0x3B, 0x2A, 0x20), 0, h, INK));
            g.fillRect(0, 0, w, h);
            for (int index = 0; index < 7; index++)
            {
                g.setColor(index % 2 == 0 ? withAlpha(ORANGE, 200 - index * 20) : new Color(255, 255, 255, 30));
                double offset = index * 70;
                g.fill(new java.awt.geom.Path2D.Double(new java.awt.Polygon(
                    new int[] {(int) (-100 + offset), (int) (w * 0.7 + offset), (int) (w * 0.4 + offset), -200},
                    new int[] {(int) (h * 0.2 + offset), (int) (h * 0.05 + offset), (int) (h * 0.6 + offset),
                        (int) (h * 0.8 + offset)},
                    4)));
            }
        });
    }

    @FunctionalInterface
    private interface Painter
    {
        void paint(Graphics2D graphics, int width, int height, Random random);
    }

    private static byte[] draw(int width, int height, long seed, Painter painter)
    {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        painter.paint(graphics, width, height, new Random(seed));
        graphics.dispose();
        return jpeg(image);
    }

    private static byte[] jpeg(BufferedImage image)
    {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out))
        {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.88f);
            writer.write(null, new IIOImage(image, null, null), param);
        }
        catch (IOException exception)
        {
            throw new UncheckedIOException(exception);
        }
        finally
        {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static Color withAlpha(Color color, int alpha)
    {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }
}
