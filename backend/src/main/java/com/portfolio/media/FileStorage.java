package com.portfolio.media;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import org.springframework.core.io.Resource;

/**
 * Binary storage for uploaded files. The local-disk implementation can be swapped for S3 later.
 */
public interface FileStorage
{
    void store(String name, byte[] content) throws IOException;

    /** Streams a large file (video) to storage without holding it in memory. */
    void store(String name, InputStream content) throws IOException;

    /** The stored file as a resource that supports byte ranges (video seeking). */
    Optional<Resource> resource(String name);

    Optional<InputStream> open(String name) throws IOException;

    void delete(String name) throws IOException;

    boolean exists(String name);
}
