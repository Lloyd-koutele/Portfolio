package com.portfolio.settings.internal;

import com.portfolio.settings.IpMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

record SettingsRequest(
    @NotBlank @Size(max = 120) String title,
    @Min(100) @Max(4000) int pageWidth,
    @Min(100) @Max(4000) int pageHeight,
    @NotNull @Pattern(regexp = "#[0-9a-fA-F]{6}") String backgroundColor,
    UUID backgroundMediaId,
    @NotNull IpMode ipMode,
    @Size(max = 20_000) String privacyText)
{
}
