package com.portfolio.media.internal;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedModel;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/media")
class MediaAdminController
{
    private static final int MAX_PAGE_SIZE = 100;

    private final MediaService service;

    MediaAdminController(MediaService service)
    {
        this.service = service;
    }

    /**
     * Image or video upload. For videos, the browser sends the dimensions it read from the file.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<MediaResponse> upload(@RequestPart("file") MultipartFile file,
        @RequestParam(name = "kind", defaultValue = "UPLOAD") MediaKind kind,
        @RequestParam(name = "width", required = false) Integer width,
        @RequestParam(name = "height", required = false) Integer height) throws IOException
    {
        Media media = service.upload(file, file.getSize(), file.getOriginalFilename(), kind, width, height);
        return ResponseEntity.created(URI.create(media.url())).body(MediaResponse.from(media));
    }

    @GetMapping
    PagedModel<MediaResponse> list(@RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "40") int size, @RequestParam(required = false) String type)
    {
        PageRequest request = PageRequest.of(Math.max(0, page), Math.min(MAX_PAGE_SIZE, Math.max(1, size)));
        Page<MediaResponse> result = service.library(type, request).map(MediaResponse::from);
        return new PagedModel<>(result);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id)
    {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
