package com.portfolio.visit.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

record VisitRequest(
    @NotBlank @Size(max = 120) String pageSlug,
    @NotNull UUID sessionId,
    @Size(max = 2048) String referer)
{
}
