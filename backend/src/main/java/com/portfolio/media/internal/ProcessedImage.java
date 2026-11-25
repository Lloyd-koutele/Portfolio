package com.portfolio.media.internal;

/**
 * An uploaded image after validation and metadata removal.
 *
 * @param mimeType type detected from the content
 * @param extension file extension matching the type
 * @param width display width in pixels
 * @param height display height in pixels
 * @param content bytes without EXIF / XMP / comments
 */
record ProcessedImage(String mimeType, String extension, int width, int height, byte[] content)
{
}
