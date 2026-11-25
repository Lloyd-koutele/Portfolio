package com.portfolio.visit.internal;

import com.portfolio.common.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class VisitController
{
    private final VisitRecorder recorder;
    private final ClientIpResolver clientIpResolver;

    VisitController(VisitRecorder recorder, ClientIpResolver clientIpResolver)
    {
        this.recorder = recorder;
        this.clientIpResolver = clientIpResolver;
    }

    /**
     * Always answers 204, whether the visit was stored or ignored, so the client learns nothing.
     */
    @PostMapping("/api/public/visits")
    ResponseEntity<Void> record(@Valid @RequestBody VisitRequest body, HttpServletRequest request)
    {
        recorder.record(body, clientIpResolver.resolve(request), request.getHeader(HttpHeaders.USER_AGENT),
            SecurityContextHolder.getContext().getAuthentication());
        return ResponseEntity.noContent().build();
    }
}
