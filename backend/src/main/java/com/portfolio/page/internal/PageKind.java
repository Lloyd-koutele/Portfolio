package com.portfolio.page.internal;

/**
 * Place of a page in the book. The fixed pages exist once each; project sheets belong to a project.
 * Book order: COVER, TABLE_OF_CONTENTS, then the projects with their sheets, then CONTACT.
 */
enum PageKind
{
    COVER("Couverture", "couverture"),
    TABLE_OF_CONTENTS("Sommaire", "sommaire"),
    PROJECT_SHEET(null, null),
    CONTACT("Contact", "contact");

    private final String defaultTitle;
    private final String defaultSlug;

    PageKind(String defaultTitle, String defaultSlug)
    {
        this.defaultTitle = defaultTitle;
        this.defaultSlug = defaultSlug;
    }

    boolean isFixed()
    {
        return this != PROJECT_SHEET;
    }

    String defaultTitle()
    {
        return defaultTitle;
    }

    String defaultSlug()
    {
        return defaultSlug;
    }
}
