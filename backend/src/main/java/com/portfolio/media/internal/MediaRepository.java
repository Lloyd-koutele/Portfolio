package com.portfolio.media.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface MediaRepository extends JpaRepository<Media, UUID>
{
    Page<Media> findByKindOrderByCreatedAtDesc(MediaKind kind, Pageable pageable);

    Page<Media> findByKindAndMimeTypeStartingWithOrderByCreatedAtDesc(MediaKind kind, String mimePrefix,
        Pageable pageable);

    Optional<Media> findByStoredName(String storedName);
}
