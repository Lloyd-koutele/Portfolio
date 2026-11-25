package com.portfolio.page;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface PageApi
{
    /** A page of the edition readers currently see (the page may since have been deleted from the working copy). */
    Optional<PageRef> findPublishedBySlug(String slug);

    Map<UUID, PageRef> findByIds(Collection<UUID> ids);

    /** Projects of the edition readers currently see, in book order, with the number of their first page. */
    List<PublishedProject> publishedProjects();
}
