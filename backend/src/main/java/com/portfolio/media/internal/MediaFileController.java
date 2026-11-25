package com.portfolio.media.internal;

import com.portfolio.media.FileStorage;
import java.time.Duration;
import java.util.Optional;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves uploaded files. Names are unique and never reused, so responses are cached for a year.
 */
@RestController
class MediaFileController
{
    private final MediaService service;
    private final FileStorage storage;

    MediaFileController(MediaService service, FileStorage storage)
    {
        this.service = service;
        this.storage = storage;
    }

    /**
     * Images and videos. Returning a {@link Resource} lets Spring answer byte-range requests (206), which video
     * players need to seek and which Safari needs to play at all.
     */
    @GetMapping("/media/{storedName}")
    ResponseEntity<Resource> serve(@PathVariable String storedName,
        @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch)
    {
        if (!LocalFileStorage.SAFE_NAME.matcher(storedName).matches())
        {
            return ResponseEntity.notFound().build();
        }
        Optional<Media> media = service.findByStoredName(storedName);
        if (media.isEmpty())
        {
            return ResponseEntity.notFound().build();
        }
        String etag = "\"" + storedName + "\"";
        CacheControl cache = CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();
        if (etag.equals(ifNoneMatch))
        {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).cacheControl(cache).build();
        }
        Optional<Resource> content = storage.resource(storedName);
        if (content.isEmpty())
        {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(media.get().getMimeType()))
            .cacheControl(cache)
            .eTag(etag)
            .header(HttpHeaders.ACCEPT_RANGES, "bytes")
            .header("X-Content-Type-Options", "nosniff")
            .header("Content-Security-Policy", "default-src 'none'; sandbox")
            .body(content.get());
    }
}
