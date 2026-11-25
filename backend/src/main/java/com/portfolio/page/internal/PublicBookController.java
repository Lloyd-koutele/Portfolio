package com.portfolio.page.internal;

import com.portfolio.page.internal.PageDtos.PublicBook;
import com.portfolio.page.internal.PageDtos.PublicPageResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
class PublicBookController
{
    private final PageService service;

    PublicBookController(PageService service)
    {
        this.service = service;
    }

    @GetMapping("/book")
    ResponseEntity<PublicBook> book()
    {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(service.book());
    }

    @GetMapping("/pages/{slug}")
    ResponseEntity<PublicPageResponse> page(@PathVariable String slug)
    {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(service.publishedPage(slug));
    }
}
