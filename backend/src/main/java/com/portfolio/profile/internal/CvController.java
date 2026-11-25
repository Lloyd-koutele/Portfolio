package com.portfolio.profile.internal;

import com.portfolio.common.ApiException;
import com.portfolio.common.ClientIpResolver;
import com.portfolio.profile.internal.CvService.Download;
import com.portfolio.visit.DownloadLog;
import com.portfolio.visit.DownloadSource;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * The CV: uploaded in the administration, downloaded by visitors (each download is logged).
 */
@RestController
class CvController
{
    private final CvService service;
    private final DownloadLog downloadLog;
    private final ClientIpResolver clientIpResolver;

    CvController(CvService service, DownloadLog downloadLog, ClientIpResolver clientIpResolver)
    {
        this.service = service;
        this.downloadLog = downloadLog;
        this.clientIpResolver = clientIpResolver;
    }

    @GetMapping("/api/public/cv")
    ResponseEntity<CvInfo> info()
    {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(service.info());
    }

    /** Sends the CV as an attachment and logs the download; never cached, so each download reaches the log. */
    @GetMapping("/api/public/cv/download")
    ResponseEntity<Resource> download(@RequestParam(required = false) String source, HttpServletRequest request)
    {
        Download file = service.file().orElseThrow(CvNotFoundException::new);
        downloadLog.recordCvDownload(file.fileName(), DownloadSource.parse(source),
            clientIpResolver.resolve(request), request.getHeader(HttpHeaders.USER_AGENT),
            request.getHeader(HttpHeaders.REFERER), SecurityContextHolder.getContext().getAuthentication());
        return send(file, ContentDisposition.attachment());
    }

    /** The current CV, shown in the administration without being logged. */
    @GetMapping("/api/admin/cv/file")
    ResponseEntity<Resource> preview()
    {
        return send(service.file().orElseThrow(CvNotFoundException::new), ContentDisposition.inline());
    }

    @PostMapping(path = "/api/admin/cv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<CvInfo> upload(@RequestPart("file") MultipartFile file) throws IOException
    {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.upload(file));
    }

    @DeleteMapping("/api/admin/cv")
    ResponseEntity<Void> delete()
    {
        service.delete();
        return ResponseEntity.noContent().build();
    }

    private static ResponseEntity<Resource> send(Download file, ContentDisposition.Builder disposition)
    {
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .contentLength(file.sizeBytes())
            .cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION,
                disposition.filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .body(file.resource());
    }

    static class CvNotFoundException extends ApiException
    {
        CvNotFoundException()
        {
            super(HttpStatus.NOT_FOUND, "No CV", "No CV has been uploaded");
        }
    }
}
