package com.portfolio.page.internal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Order of the book: Cover, Table of contents, then each project's sheets (projects and sheets in their own order),
 * then Contact.
 */
final class BookOrder
{
    private BookOrder()
    {
    }

    static List<PageEntity> order(List<PageEntity> pages, List<ProjectEntity> projects)
    {
        Map<UUID, List<PageEntity>> sheets = new LinkedHashMap<>();
        for (ProjectEntity project : projects)
        {
            sheets.put(project.getId(), new ArrayList<>());
        }
        List<PageEntity> ordered = new ArrayList<>();
        addFixed(ordered, pages, PageKind.COVER);
        addFixed(ordered, pages, PageKind.TABLE_OF_CONTENTS);
        for (PageEntity page : pages)
        {
            if (page.getKind() == PageKind.PROJECT_SHEET && sheets.containsKey(page.getProjectId()))
            {
                sheets.get(page.getProjectId()).add(page);
            }
        }
        for (List<PageEntity> projectSheets : sheets.values())
        {
            projectSheets.sort(Comparator.comparingInt(PageEntity::getPosition));
            ordered.addAll(projectSheets);
        }
        addFixed(ordered, pages, PageKind.CONTACT);
        return ordered;
    }

    private static void addFixed(List<PageEntity> ordered, List<PageEntity> pages, PageKind kind)
    {
        pages.stream().filter(page -> page.getKind() == kind).findFirst().ifPresent(ordered::add);
    }
}
