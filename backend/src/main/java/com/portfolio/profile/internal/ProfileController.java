package com.portfolio.profile.internal;

import com.portfolio.profile.internal.ProfileDtos.ProfileRequest;
import com.portfolio.profile.internal.ProfileDtos.ProfileResponse;
import com.portfolio.profile.internal.ProfileDtos.PublicProfile;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ProfileController
{
    private final ProfileService service;

    ProfileController(ProfileService service)
    {
        this.service = service;
    }

    /** The administration's home page, readable before signing in. */
    @GetMapping("/api/public/profile")
    PublicProfile publicProfile()
    {
        return service.publicProfile();
    }

    @GetMapping("/api/admin/profile")
    ProfileResponse read()
    {
        return service.read();
    }

    @PutMapping("/api/admin/profile")
    ProfileResponse update(@Valid @RequestBody ProfileRequest request)
    {
        return service.update(request);
    }
}
