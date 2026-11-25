package com.portfolio.page.internal;

import com.portfolio.page.internal.PageDtos.BookStructure;
import com.portfolio.page.internal.PageDtos.CreateProjectRequest;
import com.portfolio.page.internal.PageDtos.CreateSheetRequest;
import com.portfolio.page.internal.PageDtos.PageDetail;
import com.portfolio.page.internal.PageDtos.PublicationStatus;
import com.portfolio.page.internal.PageDtos.RenameProjectRequest;
import com.portfolio.page.internal.PageDtos.ReorderRequest;
import jakarta.validation.Valid;
import java.net.URI;
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

/**
 * Structure of the book: fixed pages, projects and their sheets.
 */
@RestController
@RequestMapping("/api/admin")
class ProjectAdminController
{
    private final PageService service;

    ProjectAdminController(PageService service)
    {
        this.service = service;
    }

    @GetMapping("/book")
    BookStructure structure()
    {
        return service.structure();
    }

    /** "Publier le site": the whole working copy becomes the edition readers see. */
    @PostMapping("/book/publish")
    PublicationStatus publish()
    {
        return service.publishBook();
    }

    /** Creates a project with its first sheet; the response is that sheet, ready to open in the editor. */
    @PostMapping("/projects")
    ResponseEntity<PageDetail> createProject(@Valid @RequestBody CreateProjectRequest request)
    {
        PageDetail sheet = service.createProject(request.title());
        return ResponseEntity.created(URI.create("/api/admin/pages/" + sheet.id())).body(sheet);
    }

    @PutMapping("/projects/{id}")
    ResponseEntity<Void> renameProject(@PathVariable UUID id, @Valid @RequestBody RenameProjectRequest request)
    {
        service.renameProject(id, request.title());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/projects/{id}")
    ResponseEntity<Void> deleteProject(@PathVariable UUID id)
    {
        service.deleteProject(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/projects/order")
    ResponseEntity<Void> reorderProjects(@Valid @RequestBody ReorderRequest request)
    {
        service.reorderProjects(request.ids());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/projects/{id}/sheets")
    ResponseEntity<PageDetail> addSheet(@PathVariable UUID id, @Valid @RequestBody(required = false)
        CreateSheetRequest request)
    {
        PageDetail sheet = service.addSheet(id, request == null ? null : request.title());
        return ResponseEntity.created(URI.create("/api/admin/pages/" + sheet.id())).body(sheet);
    }

    @PutMapping("/projects/{id}/sheets/order")
    ResponseEntity<Void> reorderSheets(@PathVariable UUID id, @Valid @RequestBody ReorderRequest request)
    {
        service.reorderSheets(id, request.ids());
        return ResponseEntity.noContent().build();
    }
}
