package com.portfolio.page.internal;

import com.portfolio.page.internal.BookThemeDtos.ApplyThemeRequest;
import com.portfolio.page.internal.BookThemeDtos.ThemeHistory;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Theme of the whole book: apply one, or go back to how the book was before the last one. */
@RestController
@RequestMapping("/api/admin/book/theme")
class BookThemeController
{
    private final BookThemeService service;

    BookThemeController(BookThemeService service)
    {
        this.service = service;
    }

    @GetMapping
    ThemeHistory history()
    {
        return service.history();
    }

    @PostMapping
    ThemeHistory apply(@Valid @RequestBody ApplyThemeRequest request)
    {
        return service.apply(request);
    }

    @PostMapping("/{id}/revert")
    ThemeHistory revert(@PathVariable long id)
    {
        return service.revert(id);
    }
}
