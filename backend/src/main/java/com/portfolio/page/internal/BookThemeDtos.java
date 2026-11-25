package com.portfolio.page.internal;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class BookThemeDtos
{
    private BookThemeDtos()
    {
    }

    /**
     * Every page of the book, already re-coloured by the administration (the theme's rules live in the shared
     * front-end code, so the preview and the result are the same), with the colour around the book.
     */
    record ApplyThemeRequest(
        @NotBlank @Size(max = 40) @Pattern(regexp = "[a-z0-9-]+") String themeId,
        @NotNull @Pattern(regexp = "#[0-9a-fA-F]{6}") String backgroundColor,
        @NotEmpty @Size(max = 1_000) List<@Valid @NotNull ThemedPage> pages)
    {
    }

    /**
     * @param version the version the page was read at; the whole theme is refused if one page changed since
     * @param thumbnailMediaId a new thumbnail, or null to keep the current one
     */
    record ThemedPage(@NotNull UUID id, @NotNull Integer version, @NotNull JsonNode content, UUID thumbnailMediaId)
    {
    }

    /**
     * The last theme that can be taken back, if any.
     *
     * @param modifiedSince titles of the pages edited after the theme: going back also undoes those edits
     */
    record ThemeHistory(boolean available, Long id, String themeId, Instant appliedAt, int pages,
        List<String> modifiedSince)
    {
        static ThemeHistory none()
        {
            return new ThemeHistory(false, null, null, null, 0, List.of());
        }
    }
}
