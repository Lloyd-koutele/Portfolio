package com.portfolio.page.internal;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * URL slugs: lowercase letters, digits and single hyphens.
 */
final class Slugs
{
    static final int MAX_LENGTH = 120;
    static final Pattern VALID = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    private Slugs()
    {
    }

    static String fromTitle(String title)
    {
        String ascii = Normalizer.normalize(title == null ? "" : title, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replace("œ", "oe")
            .replace("æ", "ae")
            .toLowerCase(Locale.ROOT);
        String slug = ascii.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        if (slug.length() > MAX_LENGTH - 6)
        {
            slug = slug.substring(0, MAX_LENGTH - 6).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? "page" : slug;
    }

    static boolean isValid(String slug)
    {
        return slug != null && slug.length() <= MAX_LENGTH && VALID.matcher(slug).matches();
    }
}
