package com.portfolio.media.internal;

import com.portfolio.media.FileStorage;
import com.portfolio.media.MediaApi;
import com.portfolio.media.MediaInfo;
import com.portfolio.media.MediaUsage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.tika.Tika;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.InputStreamSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
class MediaService implements MediaApi
{
    static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final int HEADER_BYTES = 64 * 1024;
    private static final int MAX_VIDEO_DIMENSION = 8192;
    private static final int MAX_NAME = 255;
    private static final Logger LOG = LoggerFactory.getLogger(MediaService.class);

    private final MediaRepository repository;
    private final FileStorage storage;
    private final ImageSanitizer sanitizer;
    private final ObjectProvider<MediaUsage> usages;
    private final MediaProperties properties;
    private final Clock clock;
    private final Tika tika = new Tika();

    MediaService(MediaRepository repository, FileStorage storage, ImageSanitizer sanitizer,
        ObjectProvider<MediaUsage> usages, MediaProperties properties, Clock clock)
    {
        this.properties = properties;
        this.repository = repository;
        this.storage = storage;
        this.sanitizer = sanitizer;
        this.usages = usages;
        this.clock = clock;
    }

    /**
     * Upload endpoint: images (PNG, JPEG, WebP, GIF, 5 MB, metadata removed) or videos (MP4, WebM, up to
     * MEDIA_MAX_VIDEO_MB, streamed to storage). Video dimensions come from the browser, which reads them before
     * sending the file.
     */
    @Transactional
    Media upload(InputStreamSource source, long size, String originalName, MediaKind kind, Integer width,
        Integer height) throws IOException
    {
        if (size <= 0)
        {
            throw new InvalidImageException("The file is empty");
        }
        byte[] header;
        try (InputStream input = source.getInputStream())
        {
            header = input.readNBytes(HEADER_BYTES);
        }
        String detected = tika.detect(header);
        if (VideoTypes.isVideoContainer(detected))
        {
            String type = VideoTypes.playableType(detected, header);
            if (type == null)
            {
                throw new InvalidVideoException("Only MP4 and WebM videos are accepted");
            }
            return uploadVideo(source, size, type, originalName, kind, width, height);
        }
        if (size > MAX_BYTES)
        {
            throw new InvalidImageException("The file exceeds 5 MB");
        }
        try (InputStream input = source.getInputStream())
        {
            return upload(input.readAllBytes(), originalName, kind);
        }
    }

    private Media uploadVideo(InputStreamSource source, long size, String type, String originalName, MediaKind kind,
        Integer width, Integer height) throws IOException
    {
        if (kind != MediaKind.UPLOAD)
        {
            throw new InvalidVideoException("Thumbnails must be images");
        }
        if (size > properties.maxVideoBytes())
        {
            throw new InvalidVideoException("The video exceeds " + properties.maxVideoMb() + " MB");
        }
        if (width == null || height == null || width < 1 || height < 1 || width > MAX_VIDEO_DIMENSION
            || height > MAX_VIDEO_DIMENSION)
        {
            throw new InvalidVideoException("Video width and height are required");
        }
        String storedName = UUID.randomUUID() + (VideoTypes.WEBM.equals(type) ? ".webm" : ".mp4");
        try (InputStream input = source.getInputStream())
        {
            storage.store(storedName, input);
        }
        deleteFileOnRollback(storedName);
        return repository.save(new Media(UUID.randomUUID(), storedName, cleanName(originalName), type, size, width,
            height, kind, clock.instant()));
    }

    @Transactional
    Media upload(byte[] content, String originalName, MediaKind kind)
    {
        if (content.length == 0)
        {
            throw new InvalidImageException("The file is empty");
        }
        if (content.length > MAX_BYTES)
        {
            throw new InvalidImageException("The file exceeds 5 MB");
        }
        ProcessedImage image = sanitizer.sanitize(content);
        UUID id = UUID.randomUUID();
        String storedName = UUID.randomUUID() + "." + image.extension();
        try
        {
            storage.store(storedName, image.content());
        }
        catch (IOException exception)
        {
            throw new UncheckedIOException(exception);
        }
        deleteFileOnRollback(storedName);
        return repository.save(new Media(id, storedName, cleanName(originalName), image, kind, clock.instant()));
    }

    /**
     * @param type "image", "video" or null for both
     */
    @Transactional(readOnly = true)
    Page<Media> library(String type, Pageable pageable)
    {
        if ("image".equals(type) || "video".equals(type))
        {
            return repository.findByKindAndMimeTypeStartingWithOrderByCreatedAtDesc(MediaKind.UPLOAD, type + "/",
                pageable);
        }
        return repository.findByKindOrderByCreatedAtDesc(MediaKind.UPLOAD, pageable);
    }

    @Transactional
    void delete(UUID id)
    {
        Media media = repository.findById(id).orElseThrow(MediaNotFoundException::new);
        if (isUsed(id))
        {
            throw new MediaInUseException();
        }
        remove(media);
    }

    @Transactional(readOnly = true)
    java.util.Optional<Media> findByStoredName(String storedName)
    {
        return repository.findByStoredName(storedName);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> existing(Collection<UUID> ids)
    {
        if (ids.isEmpty())
        {
            return Set.of();
        }
        return repository.findAllById(new HashSet<>(ids)).stream().map(Media::getId).collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, MediaInfo> describe(Collection<UUID> ids)
    {
        if (ids.isEmpty())
        {
            return Map.of();
        }
        return repository.findAllById(new HashSet<>(ids)).stream()
            .map(MediaService::info)
            .collect(Collectors.toMap(MediaInfo::id, Function.identity()));
    }

    @Override
    @Transactional
    public boolean deleteIfUnused(UUID mediaId)
    {
        return repository.findById(mediaId)
            .filter(media -> !isUsed(mediaId))
            .map(media ->
            {
                remove(media);
                return true;
            })
            .orElse(false);
    }

    @Override
    @Transactional
    public MediaInfo storeImage(byte[] content, String originalName)
    {
        Media media = upload(content, originalName, MediaKind.UPLOAD);
        return info(media);
    }

    private static MediaInfo info(Media media)
    {
        return new MediaInfo(media.getId(), media.url(), media.getWidth(), media.getHeight(), media.getMimeType());
    }

    private boolean isUsed(UUID mediaId)
    {
        List<MediaUsage> all = usages.orderedStream().toList();
        return all.stream().anyMatch(usage -> usage.isUsed(mediaId));
    }

    private void remove(Media media)
    {
        repository.delete(media);
        String name = media.getStoredName();
        if (TransactionSynchronizationManager.isSynchronizationActive())
        {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
            {
                @Override
                public void afterCommit()
                {
                    deleteFile(name);
                }
            });
        }
        else
        {
            deleteFile(name);
        }
    }

    private void deleteFileOnRollback(String name)
    {
        if (TransactionSynchronizationManager.isSynchronizationActive())
        {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
            {
                @Override
                public void afterCompletion(int status)
                {
                    if (status != STATUS_COMMITTED)
                    {
                        deleteFile(name);
                    }
                }
            });
        }
    }

    private void deleteFile(String name)
    {
        try
        {
            storage.delete(name);
        }
        catch (IOException exception)
        {
            LOG.warn("Could not delete stored file {}", name);
        }
    }

    private static String cleanName(String name)
    {
        String base = name == null ? "image" : name.replaceAll("[\\p{Cntrl}/\\\\]", "").trim();
        if (base.isEmpty())
        {
            base = "image";
        }
        return base.length() > MAX_NAME ? base.substring(0, MAX_NAME) : base;
    }
}
