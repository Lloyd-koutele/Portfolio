/**
 * Media: image upload (type checked on content, EXIF removed), storage behind {@link com.portfolio.media.FileStorage},
 * cached file serving and the media library. Other modules use {@link com.portfolio.media.MediaApi} and declare
 * where they reference media through {@link com.portfolio.media.MediaUsage}.
 */
@ApplicationModule(displayName = "Media")
package com.portfolio.media;

import org.springframework.modulith.ApplicationModule;
