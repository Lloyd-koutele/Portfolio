package com.portfolio.page.internal;

import com.portfolio.page.internal.PageDtos.PageDetail;
import com.portfolio.page.internal.PageDtos.PageSummary;
import com.portfolio.page.internal.PageDtos.UpdatePageRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/pages")
class PageAdminController
{
    private final PageService service;

    PageAdminController(PageService service)
    {
        this.service = service;
    }

    @GetMapping
    List<PageSummary> list()
    {
        return service.list();
    }


    @GetMapping("/{id}")
    PageDetail get(@PathVariable UUID id)
    {
        return service.get(id);
    }

    @PutMapping("/{id}")
    PageDetail update(@PathVariable UUID id, @Valid @RequestBody UpdatePageRequest request)
    {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id)
    {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/duplicate")
    ResponseEntity<PageDetail> duplicate(@PathVariable UUID id)
    {
        PageDetail page = service.duplicate(id);
        return ResponseEntity.created(URI.create("/api/admin/pages/" + page.id())).body(page);
    }


}
