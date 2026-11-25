package com.portfolio.settings;

import java.util.UUID;

public interface SettingsApi
{
    SiteSettingsView current();

    /**
     * Marks the demo content as created.
     *
     * @return true only the first time, so the demo is never created twice
     */
    boolean claimDemoSeeding();

    /**
     * Sets the site title and backdrop used by the demo content.
     */
    void applyDemoLook(String title, UUID backgroundMediaId);

    /**
     * Changes the colour around the book (used when a theme is applied to the whole book, or taken back).
     *
     * @param color a {@code #rrggbb} colour
     */
    void changeBackgroundColor(String color);
}
