package com.portfolio.profile.internal;

import com.portfolio.page.PublishedProject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class ProfileDtos
{
    private ProfileDtos()
    {
    }

    record Experience(
        @NotBlank @Size(max = 120) String role,
        @Size(max = 120) String organization,
        @Size(max = 60) String period,
        @Size(max = 400) String description)
    {
    }

    record ProfileRequest(
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Size(max = 160) String headline,
        @NotNull @Size(max = 1000) String intro,
        @NotNull @Size(max = 300) String availability,
        @NotNull @Size(max = 24) List<@NotBlank @Size(max = 40) String> skills,
        @NotNull @Size(max = 12) List<@Valid @NotNull Experience> experiences,
        UUID photoMediaId)
    {
    }

    /** What the administration edits. */
    record ProfileResponse(String fullName, String headline, String intro, String availability, List<String> skills,
        List<Experience> experiences, UUID photoMediaId, String photoUrl, Instant updatedAt)
    {
    }

    /** What the home page shows (no ids, plus the published projects). */
    record PublicProfile(String fullName, String headline, String intro, String availability, List<String> skills,
        List<Experience> experiences, String photoUrl, List<PublishedProject> projects)
    {
    }
}
