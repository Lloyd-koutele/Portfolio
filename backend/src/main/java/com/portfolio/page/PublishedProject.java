package com.portfolio.page;

/**
 * A project of the published book: its title, the number of its first page (1 = cover) and that page's slug.
 */
public record PublishedProject(String title, int page, String slug)
{
}
