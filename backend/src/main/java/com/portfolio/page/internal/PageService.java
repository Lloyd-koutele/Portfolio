package com.portfolio.page.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.media.MediaApi;
import com.portfolio.media.MediaInfo;
import com.portfolio.media.MediaUsage;
import com.portfolio.page.PageApi;
import com.portfolio.page.PageRef;
import com.portfolio.page.PublishedProject;
import com.portfolio.page.internal.PageDtos.BookStructure;
import com.portfolio.page.internal.PageDtos.PageDetail;
import com.portfolio.page.internal.PageDtos.PageSummary;
import com.portfolio.page.internal.PageDtos.ProjectView;
import com.portfolio.page.internal.PageDtos.PublicationStatus;
import com.portfolio.page.internal.PageDtos.PublicBook;
import com.portfolio.page.internal.PageDtos.PublicPage;
import com.portfolio.page.internal.PageDtos.PublicPageResponse;
import com.portfolio.page.internal.PageDtos.PublicSettings;
import com.portfolio.page.internal.PageDtos.TocEntry;
import com.portfolio.page.internal.PageDtos.UpdatePageRequest;
import com.portfolio.page.internal.PageExceptions.FixedPageException;
import com.portfolio.page.internal.PageExceptions.InvalidFieldException;
import com.portfolio.page.internal.PageExceptions.PageNotFoundException;
import com.portfolio.page.internal.PageExceptions.ProjectNotFoundException;
import com.portfolio.page.internal.PageExceptions.VersionConflictException;
import com.portfolio.settings.SettingsApi;
import com.portfolio.settings.SiteSettingsView;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pages of the book and its structure: fixed pages (cover, table of contents, contact) around projects, each made
 * of any number of sheets. The administration edits a working copy; "Publier le site" turns all of it into the
 * edition readers see (see {@link BookEditions}).
 */
@Service
class PageService implements PageApi, MediaUsage
{
    private final PageRepository repository;
    private final ProjectRepository projects;
    private final BookEditions editions;
    private final ThemeSnapshots themeSnapshots;
    private final PageContentValidator validator;
    private final MediaApi mediaApi;
    private final SettingsApi settingsApi;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    PageService(PageRepository repository, ProjectRepository projects, BookEditions editions,
        ThemeSnapshots themeSnapshots, PageContentValidator validator,
        MediaApi mediaApi, SettingsApi settingsApi, ObjectMapper objectMapper, ApplicationEventPublisher events,
        Clock clock)
    {
        this.repository = repository;
        this.projects = projects;
        this.editions = editions;
        this.themeSnapshots = themeSnapshots;
        this.validator = validator;
        this.mediaApi = mediaApi;
        this.settingsApi = settingsApi;
        this.objectMapper = objectMapper;
        this.events = events;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- structure (admin)

    /** Every page in book order, drafts included, with its number in the published book. */
    @Transactional(readOnly = true)
    List<PageSummary> list()
    {
        Book book = loadBook();
        return book.ordered().stream().map(book::summary).toList();
    }

    @Transactional(readOnly = true)
    BookStructure structure()
    {
        Book book = loadBook();
        List<PageSummary> start = new ArrayList<>();
        List<PageSummary> end = new ArrayList<>();
        for (PageEntity page : book.ordered())
        {
            if (page.getKind() == PageKind.CONTACT)
            {
                end.add(book.summary(page));
            }
            else if (page.getKind().isFixed())
            {
                start.add(book.summary(page));
            }
        }
        List<ProjectView> projectViews = new ArrayList<>();
        for (ProjectEntity project : book.projects())
        {
            List<PageSummary> sheets = book.ordered().stream()
                .filter(page -> project.getId().equals(page.getProjectId()))
                .map(book::summary)
                .toList();
            List<Integer> numbers = sheets.stream().map(PageSummary::pageNumber).filter(Objects::nonNull).toList();
            projectViews.add(new ProjectView(project.getId(), project.getTitle(), project.getPosition(),
                numbers.isEmpty() ? null : numbers.get(0), numbers.isEmpty() ? null : numbers.get(numbers.size() - 1),
                sheets));
        }
        return new BookStructure(start, projectViews, end, book.toc(), publication());
    }

    /** Creates a project at the end of the projects, with a first sheet; returns that sheet for the editor. */
    @Transactional
    PageDetail createProject(String title)
    {
        ProjectEntity project = projects.saveAndFlush(
            new ProjectEntity(title.trim(), projects.maxPosition() + 1, clock.instant()));
        return detail(newSheet(project, project.getTitle()));
    }

    @Transactional
    void renameProject(UUID id, String title)
    {
        ProjectEntity project = findProject(id);
        project.rename(title.trim(), clock.instant());
    }

    /** Deletes a project and all its sheets; their visits stay, detached from the page. */
    @Transactional
    void deleteProject(UUID id)
    {
        ProjectEntity project = findProject(id);
        Set<UUID> thumbnails = repository.findByProjectIdOrderByPositionAsc(id).stream()
            .map(PageEntity::getThumbnailMediaId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        repository.deleteAll(repository.findByProjectIdOrderByPositionAsc(id));
        repository.flush();
        projects.delete(project);
        projects.flush();
        renumberProjects(projects.findAllByOrderByPositionAsc());
        if (!thumbnails.isEmpty())
        {
            events.publishEvent(new PageMediaReleased(thumbnails));
        }
    }

    @Transactional
    void reorderProjects(List<UUID> ids)
    {
        List<ProjectEntity> all = projects.findAllByOrderByPositionAsc();
        Map<UUID, ProjectEntity> byId = all.stream().collect(Collectors.toMap(ProjectEntity::getId, p -> p));
        requireSameIds(ids, byId.keySet(), "project");
        renumberProjects(ids.stream().map(byId::get).toList());
    }

    /** Adds a sheet at the end of a project. */
    @Transactional
    PageDetail addSheet(UUID projectId, String title)
    {
        ProjectEntity project = findProject(projectId);
        int count = repository.findByProjectIdOrderByPositionAsc(projectId).size();
        String sheetTitle = title == null || title.isBlank()
            ? truncate(project.getTitle() + " — " + (count + 1), 200)
            : title.trim();
        return detail(newSheet(project, sheetTitle));
    }

    @Transactional
    void reorderSheets(UUID projectId, List<UUID> ids)
    {
        findProject(projectId);
        List<PageEntity> sheets = repository.findByProjectIdOrderByPositionAsc(projectId);
        Map<UUID, PageEntity> byId = sheets.stream().collect(Collectors.toMap(PageEntity::getId, p -> p));
        requireSameIds(ids, byId.keySet(), "sheet of this project");
        renumberSheets(ids.stream().map(byId::get).toList());
    }

    // ---------------------------------------------------------------- pages (admin)

    @Transactional(readOnly = true)
    PageDetail get(UUID id)
    {
        return detail(find(id));
    }

    @Transactional
    PageDetail update(UUID id, UpdatePageRequest request)
    {
        PageEntity page = find(id);
        if (page.getVersion() != request.version())
        {
            throw new VersionConflictException(page.getVersion());
        }
        String slug = request.slug().trim();
        checkSlug(slug, id);
        PageContentValidator.ValidatedContent content = checkContent(request.content(), request.thumbnailMediaId());
        UUID previousThumbnail = page.getThumbnailMediaId();
        UUID thumbnail = request.thumbnailMediaId() != null ? request.thumbnailMediaId() : previousThumbnail;
        page.edit(request.title().trim(), slug, content.json(), thumbnail, clock.instant());
        PageEntity saved = repository.saveAndFlush(page);
        if (previousThumbnail != null && !previousThumbnail.equals(thumbnail))
        {
            events.publishEvent(new PageMediaReleased(Set.of(previousThumbnail)));
        }
        return detail(saved);
    }

    /**
     * Validates a page's content and checks that every media it uses (and its thumbnail) exists and has the right
     * kind.
     */
    PageContentValidator.ValidatedContent checkContent(JsonNode rawContent, UUID thumbnailMediaId)
    {
        PageContentValidator.ValidatedContent content = validator.validate(rawContent);
        Set<UUID> referenced = new HashSet<>(content.mediaIds());
        if (thumbnailMediaId != null)
        {
            referenced.add(thumbnailMediaId);
        }
        Map<UUID, MediaInfo> found = mediaApi.describe(referenced);
        if (!found.keySet().containsAll(referenced))
        {
            throw new InvalidPageContentException(Map.of("content", "references unknown media"));
        }
        for (UUID mediaId : referenced)
        {
            boolean mustBeVideo = content.videoIds().contains(mediaId);
            if (mustBeVideo ? !found.get(mediaId).isVideo() : !found.get(mediaId).isImage())
            {
                throw new InvalidPageContentException(Map.of("content",
                    mustBeVideo ? "a video element must use a video" : "an image must use an image file"));
            }
        }
        return content;
    }

    /** Deletes a project sheet. Fixed pages cannot be deleted. */
    @Transactional
    void delete(UUID id)
    {
        PageEntity page = find(id);
        if (page.getKind().isFixed())
        {
            throw new FixedPageException();
        }
        repository.delete(page);
        repository.flush();
        renumberSheets(repository.findByProjectIdOrderByPositionAsc(page.getProjectId()));
        if (page.getThumbnailMediaId() != null)
        {
            events.publishEvent(new PageMediaReleased(Set.of(page.getThumbnailMediaId())));
        }
    }

    /** Copies a sheet, as a draft, at the end of the same project. */
    @Transactional
    PageDetail duplicate(UUID id)
    {
        PageEntity source = find(id);
        if (source.getKind().isFixed())
        {
            throw new FixedPageException();
        }
        String title = truncate(source.getTitle() + " (copie)", 200);
        PageEntity copy = new PageEntity(PageKind.PROJECT_SHEET, source.getProjectId(), uniqueSlug(source.getSlug()),
            title, repository.maxPositionInProject(source.getProjectId()) + 1, source.getContent(),
            source.getThumbnailMediaId(), clock.instant());
        return detail(repository.saveAndFlush(copy));
    }

    /**
     * "Publier le site": the whole working copy (content, projects, order, deletions) becomes the edition readers
     * see. Nothing reaches the public site before that.
     */
    @Transactional
    PublicationStatus publishBook()
    {
        return new PublicationStatus(editions.publish(), true);
    }

    @Transactional(readOnly = true)
    PublicationStatus publication()
    {
        return new PublicationStatus(editions.latest().map(BookEditions.Edition::publishedAt).orElse(null),
            editions.upToDate());
    }

    // ---------------------------------------------------------------- public

    @Transactional(readOnly = true)
    PublicBook book()
    {
        List<BookEditions.EditionPage> pages = editionPages();
        List<PublicPage> publicPages = new ArrayList<>();
        Set<UUID> mediaIds = new HashSet<>();
        for (BookEditions.EditionPage page : pages)
        {
            mediaIds.addAll(mediaIn(page.content()));
            publicPages.add(new PublicPage(page.id(), page.slug(), page.title(), page.content()));
        }
        return new PublicBook(publicSettings(), publicPages, mediaApi.describe(mediaIds), editionToc(pages));
    }

    @Transactional(readOnly = true)
    PublicPageResponse publishedPage(String slug)
    {
        List<BookEditions.EditionPage> pages = editionPages();
        BookEditions.EditionPage page = pages.stream().filter(p -> p.slug().equals(slug)).findFirst()
            .orElseThrow(PageNotFoundException::new);
        return new PublicPageResponse(publicSettings(),
            new PublicPage(page.id(), page.slug(), page.title(), page.content()),
            mediaApi.describe(mediaIn(page.content())), editionToc(pages));
    }

    private List<BookEditions.EditionPage> editionPages()
    {
        return editions.latest().map(BookEditions.Edition::pages).orElse(List.of());
    }

    /** Table of contents of an edition: each project with the number of its first page (1 = cover). */
    private static List<TocEntry> editionToc(List<BookEditions.EditionPage> pages)
    {
        List<TocEntry> toc = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (int index = 0; index < pages.size(); index++)
        {
            BookEditions.EditionPage page = pages.get(index);
            if (page.projectId() != null && seen.add(page.projectId()))
            {
                toc.add(new TocEntry(page.projectId(), page.projectTitle(), index + 1, page.slug()));
            }
        }
        return toc;
    }

    // ---------------------------------------------------------------- API for other modules

    @Override
    @Transactional(readOnly = true)
    public Optional<PageRef> findPublishedBySlug(String slug)
    {
        return editionPages().stream()
            .filter(page -> page.slug().equals(slug))
            .findFirst()
            .map(page -> new PageRef(page.id(), page.slug(), page.title()));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, PageRef> findByIds(Collection<UUID> ids)
    {
        return repository.findAllById(new HashSet<>(ids)).stream()
            .collect(Collectors.toMap(PageEntity::getId,
                page -> new PageRef(page.getId(), page.getSlug(), page.getTitle())));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PublishedProject> publishedProjects()
    {
        return editionToc(editionPages()).stream()
            .map(entry -> new PublishedProject(entry.title(), entry.page(), entry.slug()))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isUsed(UUID mediaId)
    {
        return repository.referencesMedia(mediaId) || editions.latestReferences(mediaId)
            || themeSnapshots.references(mediaId);
    }

    // ---------------------------------------------------------------- book model

    /**
     * The working copy of the book: pages in order, their numbers (1 = cover) and the table of contents (each
     * project with the number of its first sheet), as they will be after the next publication.
     */
    private record Book(List<PageEntity> ordered, List<ProjectEntity> projects, Map<UUID, Integer> numbers,
        List<TocEntry> toc, Map<UUID, MediaInfo> thumbnails)
    {
        PageSummary summary(PageEntity page)
        {
            MediaInfo thumbnail = page.getThumbnailMediaId() == null ? null
                : thumbnails.get(page.getThumbnailMediaId());
            return new PageSummary(page.getId(), page.getTitle(), page.getSlug(), page.getKind(), page.getProjectId(),
                page.getPosition(), numbers.getOrDefault(page.getId(), 0), thumbnail == null ? null : thumbnail.url(),
                page.getVersion(), page.getCreatedAt(), page.getUpdatedAt());
        }
    }

    private Book loadBook()
    {
        List<ProjectEntity> allProjects = projects.findAllByOrderByPositionAsc();
        List<PageEntity> ordered = BookOrder.order(repository.findAll(), allProjects);
        Map<UUID, Integer> numbers = new HashMap<>();
        for (int index = 0; index < ordered.size(); index++)
        {
            numbers.put(ordered.get(index).getId(), index + 1);
        }
        List<TocEntry> toc = new ArrayList<>();
        for (ProjectEntity project : allProjects)
        {
            ordered.stream()
                .filter(page -> project.getId().equals(page.getProjectId()))
                .findFirst()
                .ifPresent(first -> toc.add(new TocEntry(project.getId(), project.getTitle(),
                    numbers.get(first.getId()), first.getSlug())));
        }
        Map<UUID, MediaInfo> thumbnails = mediaApi.describe(ordered.stream()
            .map(PageEntity::getThumbnailMediaId).filter(Objects::nonNull).collect(Collectors.toSet()));
        return new Book(ordered, allProjects, numbers, toc, thumbnails);
    }

    // ---------------------------------------------------------------- helpers

    private PageEntity newSheet(ProjectEntity project, String title)
    {
        PageEntity page = new PageEntity(PageKind.PROJECT_SHEET, project.getId(), uniqueSlug(Slugs.fromTitle(title)),
            title, repository.maxPositionInProject(project.getId()) + 1, write(validator.emptyContent()), null,
            clock.instant());
        return repository.saveAndFlush(page);
    }

    private PublicSettings publicSettings()
    {
        SiteSettingsView settings = settingsApi.current();
        String backgroundUrl = null;
        if (settings.backgroundMediaId() != null)
        {
            MediaInfo info = mediaApi.describe(List.of(settings.backgroundMediaId())).get(settings.backgroundMediaId());
            backgroundUrl = info == null ? null : info.url();
        }
        return new PublicSettings(settings.title(), settings.pageWidth(), settings.pageHeight(),
            settings.backgroundColor(), backgroundUrl, settings.privacyText());
    }

    private PageEntity find(UUID id)
    {
        return repository.findById(id).orElseThrow(PageNotFoundException::new);
    }

    private ProjectEntity findProject(UUID id)
    {
        return projects.findById(id).orElseThrow(ProjectNotFoundException::new);
    }

    private static void requireSameIds(List<UUID> ids, Set<UUID> actual, String what)
    {
        Set<UUID> requested = new HashSet<>(ids);
        if (requested.size() != ids.size() || !requested.equals(actual))
        {
            throw new InvalidFieldException("ids", "must list every " + what + " exactly once");
        }
    }

    private void checkSlug(String slug, UUID currentId)
    {
        if (!Slugs.isValid(slug))
        {
            throw new InvalidFieldException("slug", "lowercase letters, digits and hyphens only");
        }
        boolean taken = currentId == null
            ? repository.existsBySlug(slug)
            : repository.existsBySlugAndIdNot(slug, currentId);
        if (taken)
        {
            throw new InvalidFieldException("slug", "is already used by another page");
        }
    }

    String uniqueSlug(String base)
    {
        String candidate = base;
        int suffix = 2;
        while (repository.existsBySlug(candidate))
        {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    /** Changes positions without touching the optimistic-lock version, so an open editor stays valid. */
    private void renumberSheets(List<PageEntity> ordered)
    {
        for (int index = 0; index < ordered.size(); index++)
        {
            if (ordered.get(index).getPosition() != index)
            {
                repository.updatePosition(ordered.get(index).getId(), index);
            }
        }
    }

    private static void renumberProjects(List<ProjectEntity> ordered)
    {
        for (int index = 0; index < ordered.size(); index++)
        {
            ordered.get(index).moveTo(index);
        }
    }

    private PageDetail detail(PageEntity page)
    {
        JsonNode content = read(page.getContent());
        return new PageDetail(page.getId(), page.getTitle(), page.getSlug(), page.getKind(), page.getProjectId(),
            page.getPosition(), content, page.getThumbnailMediaId(), page.getVersion(), page.getCreatedAt(),
            page.getUpdatedAt(), mediaApi.describe(mediaIn(content)));
    }

    private static Set<UUID> mediaIn(JsonNode content)
    {
        Set<UUID> ids = new HashSet<>();
        for (JsonNode value : content.findValues("mediaId"))
        {
            try
            {
                ids.add(UUID.fromString(value.asText()));
            }
            catch (IllegalArgumentException ignored)
            {
                // Stored content is validated; an unreadable id is simply skipped.
            }
        }
        return ids;
    }

    JsonNode read(String json)
    {
        try
        {
            return objectMapper.readTree(json);
        }
        catch (JsonProcessingException exception)
        {
            throw new UncheckedIOException(exception);
        }
    }

    String write(JsonNode node)
    {
        try
        {
            return objectMapper.writeValueAsString(node);
        }
        catch (JsonProcessingException exception)
        {
            throw new UncheckedIOException(exception);
        }
    }

    private static String truncate(String value, int max)
    {
        return value.length() > max ? value.substring(0, max) : value;
    }
}
