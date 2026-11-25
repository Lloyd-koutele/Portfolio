package com.portfolio.page.internal;

import com.portfolio.common.ApiException;
import com.portfolio.page.internal.BookThemeDtos.ApplyThemeRequest;
import com.portfolio.page.internal.BookThemeDtos.ThemeHistory;
import com.portfolio.page.internal.BookThemeDtos.ThemedPage;
import com.portfolio.page.internal.ThemeSnapshots.Snapshot;
import com.portfolio.page.internal.ThemeSnapshots.SnapshotPage;
import com.portfolio.settings.SettingsApi;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A theme applied to the whole book in one go, and taken back in one go. Before the pages change, a copy of what
 * they were (content, thumbnail) and of the colour around the book is kept (see {@link ThemeSnapshots}). Either
 * every page takes the theme or none does. Readers see nothing until "Publier le site".
 */
@Service
class BookThemeService
{
    private final PageRepository repository;
    private final PageService pages;
    private final ThemeSnapshots snapshots;
    private final SettingsApi settingsApi;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    BookThemeService(PageRepository repository, PageService pages, ThemeSnapshots snapshots, SettingsApi settingsApi,
        ApplicationEventPublisher events, Clock clock)
    {
        this.repository = repository;
        this.pages = pages;
        this.snapshots = snapshots;
        this.settingsApi = settingsApi;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    ThemeHistory history()
    {
        return snapshots.latest().map(this::history).orElse(ThemeHistory.none());
    }

    @Transactional
    ThemeHistory apply(ApplyThemeRequest request)
    {
        Map<UUID, PageEntity> current = repository.findAll().stream()
            .collect(Collectors.toMap(PageEntity::getId, Function.identity()));
        Set<UUID> requested = request.pages().stream().map(ThemedPage::id).collect(Collectors.toSet());
        if (requested.size() != request.pages().size() || !requested.equals(current.keySet()))
        {
            throw new BookChangedException();
        }
        List<String> contents = new ArrayList<>();
        for (ThemedPage themed : request.pages())
        {
            if (current.get(themed.id()).getVersion() != themed.version())
            {
                throw new BookChangedException();
            }
            contents.add(pages.checkContent(themed.content(), themed.thumbnailMediaId()).json());
        }
        Instant now = clock.instant();
        List<PageEntity> edited = new ArrayList<>();
        List<SnapshotPage> before = new ArrayList<>();
        for (int index = 0; index < request.pages().size(); index++)
        {
            ThemedPage themed = request.pages().get(index);
            PageEntity page = current.get(themed.id());
            before.add(new SnapshotPage(page.getId(), pages.read(page.getContent()), page.getThumbnailMediaId(), 0));
            UUID thumbnail = themed.thumbnailMediaId() != null ? themed.thumbnailMediaId() : page.getThumbnailMediaId();
            page.edit(page.getTitle(), page.getSlug(), contents.get(index), thumbnail, now);
            edited.add(page);
        }
        repository.saveAllAndFlush(edited);
        List<SnapshotPage> kept = new ArrayList<>();
        for (int index = 0; index < before.size(); index++)
        {
            SnapshotPage page = before.get(index);
            kept.add(new SnapshotPage(page.id(), page.content(), page.thumbnailMediaId(),
                edited.get(index).getVersion()));
        }
        String previousBackground = settingsApi.current().backgroundColor();
        settingsApi.changeBackgroundColor(request.backgroundColor());
        snapshots.save(request.themeId(), previousBackground, kept);
        // Thumbnails replaced by the theme stay in the copy; they are released only when the copy is dropped.
        release(snapshots.prune());
        return history();
    }

    /** Puts the book back as it was before the last theme; the theme before it can then be taken back too. */
    @Transactional
    ThemeHistory revert(long id)
    {
        Snapshot snapshot = snapshots.latest().filter(latest -> latest.id() == id)
            .orElseThrow(NothingToRevertException::new);
        Instant now = clock.instant();
        Set<UUID> replaced = new HashSet<>();
        for (SnapshotPage saved : snapshot.pages())
        {
            repository.findById(saved.id()).ifPresent(page ->
            {
                if (page.getThumbnailMediaId() != null && !page.getThumbnailMediaId().equals(saved.thumbnailMediaId()))
                {
                    replaced.add(page.getThumbnailMediaId());
                }
                page.edit(page.getTitle(), page.getSlug(), pages.write(saved.content()), saved.thumbnailMediaId(),
                    now);
            });
        }
        repository.flush();
        settingsApi.changeBackgroundColor(snapshot.backgroundColor());
        snapshots.delete(snapshot.id());
        Set<UUID> released = new HashSet<>(replaced);
        // Thumbnails of pages deleted since the theme were only kept for this copy.
        released.addAll(ThemeSnapshots.thumbnails(snapshot));
        release(released);
        return history();
    }

    private ThemeHistory history(Snapshot snapshot)
    {
        Map<UUID, PageEntity> current = repository.findAllById(
            snapshot.pages().stream().map(SnapshotPage::id).toList()).stream()
            .collect(Collectors.toMap(PageEntity::getId, Function.identity()));
        List<String> modified = snapshot.pages().stream()
            .map(page -> current.get(page.id()) != null && current.get(page.id()).getVersion() != page.versionAfter()
                ? current.get(page.id()).getTitle()
                : null)
            .filter(Objects::nonNull)
            .toList();
        return new ThemeHistory(true, snapshot.id(), snapshot.themeId(), snapshot.appliedAt(), current.size(),
            modified);
    }

    private void release(Set<UUID> mediaIds)
    {
        if (!mediaIds.isEmpty())
        {
            events.publishEvent(new PageMediaReleased(mediaIds));
        }
    }

    static class BookChangedException extends ApiException
    {
        BookChangedException()
        {
            super(HttpStatus.CONFLICT, "Conflict", "The book changed while the theme was being prepared");
        }

        @Override
        public Map<String, Object> properties()
        {
            return Map.of("code", "BOOK_CHANGED");
        }
    }

    static class NothingToRevertException extends ApiException
    {
        NothingToRevertException()
        {
            super(HttpStatus.CONFLICT, "Conflict", "There is no theme to take back");
        }

        @Override
        public Map<String, Object> properties()
        {
            return Map.of("code", "NOTHING_TO_REVERT");
        }
    }
}
