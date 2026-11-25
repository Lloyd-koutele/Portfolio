package com.portfolio.page.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.portfolio.media.MediaInfo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class PageDtos
{
    private PageDtos()
    {
    }

    record CreateProjectRequest(@NotBlank @Size(max = 200) String title)
    {
    }

    record RenameProjectRequest(@NotBlank @Size(max = 200) String title)
    {
    }

    record CreateSheetRequest(@Size(max = 200) String title)
    {
    }

    record UpdatePageRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 120) String slug,
        @NotNull JsonNode content,
        @NotNull Integer version,
        UUID thumbnailMediaId)
    {
    }

    record ReorderRequest(@NotEmpty List<@NotNull UUID> ids)
    {
    }

    /**
     * @param pageNumber number of the page in the book as it will be after the next publication (1 = cover)
     */
    record PageSummary(UUID id, String title, String slug, PageKind kind, UUID projectId, int position,
        int pageNumber, String thumbnailUrl, int version, Instant createdAt, Instant updatedAt)
    {
    }

    /** One line of the automatic table of contents: a project and the number of its first page. */
    record TocEntry(UUID projectId, String title, int page, String slug)
    {
    }

    record ProjectView(UUID id, String title, int position, Integer firstPage, Integer lastPage,
        List<PageSummary> sheets)
    {
    }

    /**
     * @param publishedAt last time the public site was updated (null before the first publication)
     * @param upToDate whether readers already see exactly the working copy
     */
    record PublicationStatus(Instant publishedAt, boolean upToDate)
    {
    }

    /** The working copy of the book, its table of contents, and the state of the public site. */
    record BookStructure(List<PageSummary> start, List<ProjectView> projects, List<PageSummary> end,
        List<TocEntry> toc, PublicationStatus publication)
    {
    }

    record PageDetail(UUID id, String title, String slug, PageKind kind, UUID projectId, int position,
        JsonNode content, UUID thumbnailMediaId, int version, Instant createdAt, Instant updatedAt,
        Map<UUID, MediaInfo> media)
    {
    }

    record PublicPage(UUID id, String slug, String title, JsonNode content)
    {
    }

    record PublicSettings(String title, int pageWidth, int pageHeight, String backgroundColor, String backgroundUrl,
        String privacyText)
    {
    }

    record PublicBook(PublicSettings settings, List<PublicPage> pages, Map<UUID, MediaInfo> media,
        List<TocEntry> toc)
    {
    }

    record PublicPageResponse(PublicSettings settings, PublicPage page, Map<UUID, MediaInfo> media,
        List<TocEntry> toc)
    {
    }
}
